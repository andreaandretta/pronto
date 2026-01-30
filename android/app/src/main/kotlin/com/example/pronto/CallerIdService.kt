package com.example.pronto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class CallerIdService : Service() {
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    private var incomingNumber: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var reactReady: Boolean = false
    private var pendingNumber: String? = null
    
    // Battery optimization: WakeLock with timeout
    private var wakeLock: PowerManager.WakeLock? = null
    private val WAKELOCK_TIMEOUT = 60_000L // 60 seconds max
    
    // Race condition prevention
    private val isOverlayActive = AtomicBoolean(false)
    
    // Auto-dismiss timer for battery/memory protection
    private val AUTO_DISMISS_TIMEOUT = 15_000L // 15 seconds (reduced from 60s)
    private val autoDismissRunnable = Runnable {
        android.util.Log.w("CallerIdService", "Auto-dismissing overlay after timeout")
        closeOverlay()
    }
    
    // Sanitize phone number input
    private fun sanitizePhoneNumber(input: String?): String {
        if (input.isNullOrBlank()) return ""
        // Whitelist: only digits, +, spaces, dashes, parentheses
        return input.replace(Regex("[^0-9+\\s\\-()]"), "").take(20)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundSafely()
    }
    
    /**
     * Start foreground service with fallback for Android 14+ SecurityException.
     * On API 34+, phoneCall foreground service type is restricted to system dialers.
     * We use specialUse type instead, with try-catch as safety net.
     */
    private fun startForegroundSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34): Use ServiceInfo constant for specialUse
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                android.util.Log.d("CallerIdService", "Started foreground with SPECIAL_USE type (API 34+)")
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
                android.util.Log.d("CallerIdService", "Started foreground service")
            }
        } catch (e: SecurityException) {
            android.util.Log.e("CallerIdService", "SecurityException in startForeground: ${e.message}")
            android.util.Log.w("CallerIdService", "Continuing without foreground - overlay may still work")
            // Don't crash - the overlay can still work via SYSTEM_ALERT_WINDOW permission
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Error starting foreground: ${e.message}")
            e.printStackTrace()
        }
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "caller_id_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Race condition check: prevent duplicate overlays
        if (!isOverlayActive.compareAndSet(false, true)) {
            android.util.Log.w("CallerIdService", "Overlay already active, ignoring duplicate start")
            return START_NOT_STICKY
        }
        
        // Sanitize input to prevent XSS
        incomingNumber = sanitizePhoneNumber(intent?.getStringExtra("phone_number"))
        reactReady = false
        pendingNumber = incomingNumber
        
        // Acquire WakeLock to keep CPU running during overlay (battery optimized with timeout)
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PRONTO::CallerIdWakeLock"
            ).apply {
                acquire(WAKELOCK_TIMEOUT)
            }
            android.util.Log.d("CallerIdService", "WakeLock acquired with ${WAKELOCK_TIMEOUT}ms timeout")
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Failed to acquire WakeLock: ${e.message}")
        }
        
        android.util.Log.d("CallerIdService", "Starting overlay for number: $incomingNumber")
        showOverlay()
        
        // Schedule auto-dismiss for battery protection
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_TIMEOUT)
        
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        try {
            // Verify phone number is not blank
            if (incomingNumber.isBlank()) {
                android.util.Log.e("CallerIdService", "Empty phone number, using default")
                incomingNumber = "Numero Privato"
            }
            
            // Verify overlay permission
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.util.Log.e("CallerIdService", "CRASH PREVENTED: Missing SYSTEM_ALERT_WINDOW permission")
                isOverlayActive.set(false)
                stopSelf()
                return
            }
            
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) {
                android.util.Log.e("CallerIdService", "CRASH PREVENTED: WindowManager is null")
                isOverlayActive.set(false)
                stopSelf()
                return
            }

            // FIX: Use FLAG_NOT_TOUCH_MODAL to allow touch pass-through behind overlay
            // Use WRAP_CONTENT for both width and height so touches outside card go through
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,  // CRITICAL: Not MATCH_PARENT - blocks side touches
                WindowManager.LayoutParams.WRAP_CONTENT,  // Only cover card area, not full screen
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or  // Allow touch behind overlay
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
            // Position at top center of screen
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Use dp for consistent positioning across screen densities
            val density = resources.displayMetrics.density
            params.y = (60 * density).toInt()  // 60dp from top

            // Initialize WebView on main thread with try-catch
            initWebViewSafely(params)
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "CRASH in showOverlay: ${e.message}")
            e.printStackTrace()
            isOverlayActive.set(false)
            stopSelf()
        }
    }
    
    private fun initWebViewSafely(params: WindowManager.LayoutParams) {
        try {
            // Ensure we're on the main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                handler.post { initWebViewSafely(params) }
                return
            }
            
            webView = WebView(this).apply {
            // FIX Issue 2: Make WebView focusable and clickable for button interaction
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            
            // Configurazione WebView estesa
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                // Cache settings
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                // Database per localStorage
                databaseEnabled = true
                // Altri settings per compatibilità
                javaScriptCanOpenWindowsAutomatically = true
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = false
            }
            
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // FIX: Add JavaScript interface BEFORE loading content
            // Use both "Android" and "AndroidInterface" for compatibility
            addJavascriptInterface(AndroidBridge(), "Android")
            addJavascriptInterface(AndroidBridge(), "AndroidInterface")
            
            // WebChromeClient per logging console JavaScript
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.d("WebViewConsole", "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}")
                    return true
                }
            }
            
            // WebViewClient migliorato con error handling dettagliato
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    android.util.Log.d("CallerIdService", "Page loaded: $url")
                    // NON inviare subito il numero - aspetta handshake da React
                    // Il numero verrà inviato quando onReactReady() viene chiamato
                }
                
                override fun onReceivedError(
                    view: WebView?, 
                    request: WebResourceRequest?, 
                    error: WebResourceError?
                ) {
                    val errorMsg = "WebView error: ${error?.description} at ${request?.url}"
                    android.util.Log.e("CallerIdService", errorMsg)
                    // Se l'errore è sul file principale, prova il fallback
                    if (request?.url.toString().contains("index.html")) {
                        android.util.Log.e("CallerIdService", "Failed to load main HTML, using fallback")
                        loadFallbackHtml()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    android.util.Log.e("CallerIdService", "HTTP error: ${errorResponse?.statusCode} at ${request?.url}")
                }
            }
            
            // Carica l'HTML
            loadMainContent()
        }

        // Verify WebView was created successfully
        if (webView == null) {
            android.util.Log.e("CallerIdService", "CRASH PREVENTED: WebView is null after init")
            isOverlayActive.set(false)
            stopSelf()
            return
        }

        try {
            windowManager?.addView(webView, params)
            android.util.Log.d("CallerIdService", "Overlay added to window manager")
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Error adding overlay: ${e.message}")
            e.printStackTrace()
            isOverlayActive.set(false)
            stopSelf()
        }
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "CRASH in initWebViewSafely: ${e.message}")
            e.printStackTrace()
            isOverlayActive.set(false)
            stopSelf()
        }
    }

    private fun loadMainContent() {
        val assetPath = "file:///android_asset/www/index.html"
        android.util.Log.d("CallerIdService", "Loading URL: $assetPath")
        
        // Verifica che il file esista prima di caricarlo
        try {
            // Check if main index.html exists
            val indexStream = assets.open("www/index.html")
            val indexSize = indexStream.available()
            indexStream.close()
            android.util.Log.d("CallerIdService", "Asset www/index.html exists, size: $indexSize bytes")
            
            // Also check assets subfolder
            try {
                val assetsList = assets.list("www/assets")
                android.util.Log.d("CallerIdService", "www/assets contains ${assetsList?.size ?: 0} files: ${assetsList?.joinToString()}")
            } catch (e: Exception) {
                android.util.Log.w("CallerIdService", "Could not list www/assets: ${e.message}")
            }
            
            webView?.loadUrl(assetPath)
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Asset file not found: ${e.message}")
            android.util.Log.e("CallerIdService", "Listing android_asset contents:")
            try {
                val rootAssets = assets.list("") ?: arrayOf()
                android.util.Log.e("CallerIdService", "Root assets: ${rootAssets.joinToString()}")
                if (rootAssets.contains("www")) {
                    val wwwAssets = assets.list("www") ?: arrayOf()
                    android.util.Log.e("CallerIdService", "www/ assets: ${wwwAssets.joinToString()}")
                }
            } catch (listError: Exception) {
                android.util.Log.e("CallerIdService", "Failed to list assets: ${listError.message}")
            }
            loadFallbackHtml()
            return
        }
        
        // Fallback: se dopo 10 secondi React non segnala ready, usa fallback
        handler.postDelayed({
            if (!reactReady) {
                android.util.Log.w("CallerIdService", "React not ready after 10s, using fallback")
                loadFallbackHtml()
            }
        }, 10000)  // Increased from 8s to 10s
    }

    private fun sendPhoneNumberToReact() {
        val number = pendingNumber ?: return
        // Usa JSONObject.quote per escaping sicuro contro XSS
        val safeNumber = JSONObject.quote(number)
        
        webView?.evaluateJavascript(
            """
            (function() {
                console.log('PRONTO: Received phone number from Android');
                if (window.setPhoneNumber && typeof window.setPhoneNumber === 'function') {
                    window.setPhoneNumber($safeNumber);
                    return 'Number sent to React: ' + $safeNumber;
                } else {
                    console.error('PRONTO: setPhoneNumber not found!');
                    return 'ERROR: setPhoneNumber not found';
                }
            })();
            """.trimIndent(),
            { result ->
                android.util.Log.d("CallerIdService", "JS Result: $result")
            }
        )
    }

    private fun loadFallbackHtml() {
        // Sanitizza il numero per il fallback HTML
        val safeNumber = incomingNumber
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
        
        // Compact heads-up notification style (matches React UI)
        val fallbackHtml = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <title>PRONTO</title>
        <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                min-height: 100vh;
                background: transparent;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                display: flex;
                align-items: flex-start;
                justify-content: center;
                padding: 8px 12px;
            }
            .notification {
                background: rgba(15, 23, 42, 0.95);
                backdrop-filter: blur(20px);
                border-radius: 16px;
                width: 100%;
                max-width: 320px;
                overflow: hidden;
                box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5);
                border: 1px solid rgba(255,255,255,0.1);
            }
            .row {
                display: flex;
                align-items: center;
                gap: 12px;
                padding: 12px;
            }
            .icon {
                width: 40px;
                height: 40px;
                background: rgba(20, 184, 166, 0.2);
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 18px;
                flex-shrink: 0;
            }
            .info {
                flex: 1;
                min-width: 0;
            }
            .info .label {
                color: rgba(255,255,255,0.6);
                font-size: 11px;
            }
            .info .number {
                color: white;
                font-size: 14px;
                font-weight: 600;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
            .btn-wa {
                width: 40px;
                height: 40px;
                background: #25D366;
                border: none;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
                flex-shrink: 0;
                font-size: 18px;
            }
            .btn-wa:active { transform: scale(0.9); }
            .btn-close {
                width: 32px;
                height: 32px;
                background: rgba(255,255,255,0.1);
                border: none;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
                flex-shrink: 0;
                color: rgba(255,255,255,0.7);
                font-size: 16px;
            }
            .btn-close:active { background: rgba(255,255,255,0.2); }
            .footer {
                text-align: center;
                color: rgba(255,255,255,0.3);
                font-size: 10px;
                padding: 0 12px 8px;
            }
        </style>
        </head>
        <body>
        <div class="notification">
            <div class="row">
                <div class="icon">📞</div>
                <div class="info">
                    <p class="label">Chiamata in arrivo</p>
                    <p class="number" id="phoneNumber">$safeNumber</p>
                </div>
                <button class="btn-wa" onclick="action('WHATSAPP')" title="WhatsApp">💬</button>
                <button class="btn-close" onclick="action('CLOSE')" title="Chiudi">✕</button>
            </div>
            <p class="footer">PRONTO • tap WhatsApp per chattare</p>
        </div>
        <script>
            function action(type) {
                console.log('Action:', type);
                if (window.Android && window.Android.performAction) {
                    window.Android.performAction(type);
                }
            }
            try {
                if (window.Android && window.Android.getPhoneNumber) {
                    var num = window.Android.getPhoneNumber();
                    if (num) document.getElementById('phoneNumber').textContent = num;
                }
            } catch(e) { console.log('Bridge error:', e); }
        </script>
        </body>
        </html>
        """.trimIndent()
        
        webView?.loadDataWithBaseURL("file:///android_asset/", fallbackHtml, "text/html", "UTF-8", null)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun performAction(action: String) {
            android.util.Log.d("CallerIdService", ">>> BRIDGE: performAction called with: $action")
            handler.post {
                android.util.Log.d("CallerIdService", ">>> BRIDGE: Executing action on main thread: $action")
                when (action.uppercase()) {
                    "WHATSAPP" -> {
                        android.util.Log.d("CallerIdService", ">>> BRIDGE: Opening WhatsApp")
                        openWhatsApp()
                    }
                    "ANSWER" -> {
                        android.util.Log.d("CallerIdService", ">>> BRIDGE: Answering call")
                        answerCall()
                    }
                    "REJECT" -> {
                        android.util.Log.d("CallerIdService", ">>> BRIDGE: Rejecting call")
                        rejectCall()
                    }
                    "CLOSE" -> {
                        android.util.Log.d("CallerIdService", ">>> BRIDGE: Closing overlay")
                        closeOverlay()
                    }
                    else -> {
                        android.util.Log.w("CallerIdService", ">>> BRIDGE: Unknown action: $action")
                    }
                }
            }
        }
        
        @JavascriptInterface
        fun getPhoneNumber(): String {
            android.util.Log.d("CallerIdService", ">>> BRIDGE: getPhoneNumber called, returning: $incomingNumber")
            return incomingNumber
        }
        
        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("WebViewJS", ">>> JS LOG: $message")
        }
        
        @JavascriptInterface
        fun onReactReady() {
            android.util.Log.d("CallerIdService", ">>> BRIDGE: React reported ready, sending phone number")
            reactReady = true
            handler.post {
                sendPhoneNumberToReact()
            }
        }
        
        // Additional bridge methods for direct actions
        @JavascriptInterface
        fun closeOverlay() {
            android.util.Log.d("CallerIdService", ">>> BRIDGE: closeOverlay() called directly")
            handler.post { this@CallerIdService.closeOverlay() }
        }
        
        @JavascriptInterface
        fun openWhatsApp(number: String?) {
            android.util.Log.d("CallerIdService", ">>> BRIDGE: openWhatsApp() called with: $number")
            handler.post { this@CallerIdService.openWhatsApp() }
        }
    }

    private fun openWhatsApp() {
        val cleanNumber = incomingNumber.replace(Regex("[^0-9+]"), "")
        android.util.Log.d("CallerIdService", "Opening WhatsApp for number: $cleanNumber")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            android.util.Log.d("CallerIdService", "WhatsApp intent launched successfully")
            // DON'T close overlay - let user return to call and close manually
            // Overlay will auto-dismiss after timeout anyway
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Failed to open WhatsApp: ${e.message}")
            // If WhatsApp fails, try browser fallback
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$cleanNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(browserIntent)
            } catch (e2: Exception) {
                android.util.Log.e("CallerIdService", "Browser fallback also failed: ${e2.message}")
            }
        }
    }

    private fun answerCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            try {
                telecomManager.acceptRingingCall()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
        // Delay overlay close so user can see caller info briefly
        handler.postDelayed({ closeOverlay() }, 2000)  // 2 seconds delay
    }

    private fun rejectCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            try {
                telecomManager.endCall()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
        closeOverlay()
    }

    private fun closeOverlay() {
        // Prevent multiple close calls
        if (!isOverlayActive.compareAndSet(true, false)) {
            android.util.Log.d("CallerIdService", "Overlay already closed, skipping")
            return
        }
        
        android.util.Log.d("CallerIdService", "Closing overlay and destroying WebView")
        
        // Cancel auto-dismiss timer
        handler.removeCallbacks(autoDismissRunnable)
        handler.removeCallbacksAndMessages(null)
        
        // Release WakeLock to save battery
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    android.util.Log.d("CallerIdService", "WakeLock released")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Error releasing WakeLock: ${e.message}")
        }
        wakeLock = null
        
        webView?.let { wv ->
            try {
                // Remove JavaScript interfaces to prevent memory leaks
                wv.removeJavascriptInterface("Android")
                wv.removeJavascriptInterface("AndroidInterface")
                
                // Remove from WindowManager
                windowManager?.removeView(wv)
                
                // Complete WebView cleanup to prevent memory leaks
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.removeAllViews()
                wv.clearCache(true)
                
                // Destroy the WebView
                wv.destroy()
                
                android.util.Log.d("CallerIdService", "WebView destroyed successfully")
            } catch (e: Exception) {
                android.util.Log.e("CallerIdService", "Error destroying WebView: ${e.message}")
            }
        }
        
        webView = null
        reactReady = false
        pendingNumber = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Caller ID",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PRONTO")
            .setContentText("Servizio Caller ID attivo")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
    }

    override fun onDestroy() {
        android.util.Log.d("CallerIdService", "Service destroyed")
        closeOverlay()
        super.onDestroy()
    }
}
