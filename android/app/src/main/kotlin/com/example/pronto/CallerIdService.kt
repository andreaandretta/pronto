package com.example.pronto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
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
    
    // Fix Kimi: Call state polling to detect IDLE faster
    private var callStatePoller: Runnable? = null
    
    // Fix Kimi: Network callback reference for cleanup
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
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
        registerNetworkCallback()
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
        // EMERGENCY: Force close action
        if (intent?.action == "FORCE_CLOSE") {
            android.util.Log.d("CallerIdService", "FORCE_CLOSE action received - emergency shutdown")
            closeOverlay()
            return START_NOT_STICKY
        }
        
        // Check if this is an update action
        if (intent?.action == "UPDATE_NUMBER") {
            val newNumber = sanitizePhoneNumber(intent.getStringExtra("phone_number"))
            android.util.Log.d("CallerIdService", "UPDATE_NUMBER received: $newNumber")
            updatePhoneNumber(newNumber)
            return START_NOT_STICKY
        }
        
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
        
        // Fix Kimi: Start call state polling to detect IDLE faster (every 1s after 2s delay)
        startCallStatePolling()
        
        // Fix Kimi: Register network callback to close overlay on network loss
        registerNetworkCallback()
        
        return START_NOT_STICKY
    }
    
    // Fix Kimi: Poll call state every second to detect IDLE
    private fun startCallStatePolling() {
        callStatePoller = object : Runnable {
            override fun run() {
                try {
                    val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    @Suppress("DEPRECATION")
                    val callState = telephonyManager.callState
                    
                    if (callState == TelephonyManager.CALL_STATE_IDLE) {
                        android.util.Log.d("CallerIdService", "Poller detected IDLE - closing overlay")
                        closeOverlay()
                    } else {
                        // Still in call, check again in 1s
                        handler.postDelayed(this, 1000)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallerIdService", "Error in call state poller: ${e.message}")
                    // On error, close overlay to be safe
                    closeOverlay()
                }
            }
        }
        // Start polling after 2s delay
        handler.postDelayed(callStatePoller!!, 2000)
        android.util.Log.d("CallerIdService", "Call state polling started (2s delay, then 1s interval)")
    }
    
    // Fix Kimi: Register network callback to close overlay on network loss
    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    android.util.Log.w("CallerIdService", "Network lost - forcing overlay close")
                    handler.post { closeOverlay() }
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
            android.util.Log.d("CallerIdService", "Network callback registered")
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Failed to register network callback: ${e.message}")
        }
    }
    
    private fun updatePhoneNumber(newNumber: String) {
        if (newNumber == incomingNumber) {
            android.util.Log.d("CallerIdService", "Number unchanged: $newNumber")
            return
        }
        
        incomingNumber = newNumber
        pendingNumber = newNumber
        
        // Update UI via React bridge if ready
        if (reactReady) {
            android.util.Log.d("CallerIdService", "React ready, sending updated number: $newNumber")
            sendPhoneNumberToReact()
        } else {
            android.util.Log.d("CallerIdService", "React not ready yet, number will be sent when ready")
        }
        
        // Also update fallback HTML if being used
        webView?.evaluateJavascript(
            """
            (function() {
                var display = document.getElementById('phone-display');
                if (display) {
                    display.textContent = ${JSONObject.quote(newNumber)};
                    return 'Updated to: ${newNumber}';
                }
                return 'Element not found';
            })();
            """.trimIndent(),
            { result ->
                android.util.Log.d("CallerIdService", "Fallback HTML update result: $result")
            }
        )
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
            // Position at top center of screen, BELOW native call notification
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Use dp for consistent positioning across screen densities
            val density = resources.displayMetrics.density
            // Position below native Android call notification (which occupies ~200-250dp)
            // 280dp ensures PRONTO card doesn't block native answer/reject buttons
            params.y = (280 * density).toInt()  // 280dp from top - below native call UI

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
        
        // Fallback: se dopo 3 secondi React non segnala ready, usa fallback
        handler.postDelayed({
            if (!reactReady) {
                android.util.Log.w("CallerIdService", "React not ready after 3s, using fallback")
                loadFallbackHtml()
            }
        }, 3000)  // Reduced from 10s to 3s for faster UX
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
        
        // Fallback HTML - matches React UI exactly with glassmorphism design
        val fallbackHtml = """
        <!DOCTYPE html>
        <html lang="it">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no, maximum-scale=1.0">
        <title>PRONTO</title>
        <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            html, body {
                background: transparent !important;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
                -webkit-font-smoothing: antialiased;
            }
            body {
                display: flex;
                justify-content: center;
                padding: 0;
            }
            .pronto-card {
                background: rgba(15, 23, 42, 0.95);
                backdrop-filter: blur(10px);
                -webkit-backdrop-filter: blur(10px);
                border-radius: 16px;
                width: 100%;
                max-width: 320px;
                padding: 16px;
                box-shadow: 0 10px 25px rgba(0,0,0,0.5);
                border: 1px solid rgba(255,255,255,0.1);
            }
            .header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 12px;
            }
            .title {
                display: flex;
                align-items: center;
                gap: 8px;
                color: #14b8a6;
                font-size: 14px;
                font-weight: 700;
                letter-spacing: 1px;
            }
            .title::before {
                content: '📞';
                font-size: 16px;
            }
            .close-btn {
                width: 28px;
                height: 28px;
                background: rgba(255,255,255,0.1);
                border: none;
                border-radius: 50%;
                color: rgba(255,255,255,0.7);
                font-size: 18px;
                cursor: pointer;
                display: flex;
                align-items: center;
                justify-content: center;
                transition: all 0.15s ease;
            }
            .close-btn:active {
                background: rgba(255,255,255,0.2);
                transform: scale(0.95);
            }
            .content {
                display: flex;
                flex-direction: column;
                gap: 8px;
            }
            .whatsapp-btn {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 8px;
                width: 100%;
                padding: 12px 16px;
                background: linear-gradient(135deg, #25D366 0%, #128C7E 100%);
                border: none;
                border-radius: 12px;
                color: white;
                font-size: 15px;
                font-weight: 600;
                cursor: pointer;
                transition: all 0.15s ease;
                box-shadow: 0 4px 12px rgba(37, 211, 102, 0.3);
            }
            .whatsapp-btn:active {
                transform: scale(0.98);
                box-shadow: 0 2px 8px rgba(37, 211, 102, 0.2);
            }
            .whatsapp-btn svg {
                width: 20px;
                height: 20px;
                fill: white;
            }
        </style>
        </head>
        <body>
        <div class="pronto-card">
            <div class="header">
                <span class="title">PRONTO</span>
                <button class="close-btn" onclick="closeOverlay()">×</button>
            </div>
            <div class="content">
                <button class="whatsapp-btn" onclick="openWhatsApp()">
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                        <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
                    </svg>
                    <span>Apri WhatsApp</span>
                </button>
            </div>
        </div>
        <script>
            // Phone number passed directly from Kotlin: $safeNumber
            var currentPhone = '$safeNumber';
            
            function closeOverlay() {
                console.log('Close button clicked');
                if (window.Android && window.Android.performAction) {
                    window.Android.performAction('CLOSE');
                }
            }
            
            function openWhatsApp() {
                console.log('WhatsApp button clicked, phone:', currentPhone);
                // Use Android bridge for proper number handling
                if (window.Android && window.Android.performAction) {
                    window.Android.performAction('WHATSAPP');
                } else {
                    // Fallback: clean number in JS and open directly
                    var num = currentPhone || '';
                    // Remove spaces, dashes, parentheses, +
                    num = num.replace(/[\s\-\(\)\+]/g, '');
                    // Handle Italian prefixes
                    if (num.startsWith('0039')) num = num.substring(2);
                    else if (num.startsWith('39') && num.length > 9) { /* keep */ }
                    else if (num.startsWith('3') && num.length >= 9) num = '39' + num;
                    else if (num.startsWith('0') && num.length >= 9) num = '39' + num.substring(1);
                    
                    var url = (num.length >= 10 && !num.includes('Privato')) 
                        ? 'https://wa.me/' + num 
                        : 'https://wa.me/';
                    console.log('Opening WhatsApp URL:', url);
                    window.location.href = url;
                }
            }
            
            // Update phone from bridge if available (backup method)
            try {
                if (window.Android && window.Android.getPhoneNumber) {
                    var bridgePhone = window.Android.getPhoneNumber();
                    if (bridgePhone && bridgePhone.length > 0) {
                        currentPhone = bridgePhone;
                        document.getElementById('phone-display').textContent = bridgePhone;
                    }
                }
            } catch(e) { 
                console.log('Bridge fallback error:', e); 
            }
            
            // Signal ready to Kotlin
            console.log('Fallback HTML loaded with phone: ' + currentPhone);
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
                        // Note: closeOverlay() is now called inside openWhatsApp() after intent launch
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
        // BUG FIX: Clean number completely for wa.me format
        // wa.me requires: country code + number, no spaces, no +, no dashes
        var cleanNumber = incomingNumber
            .replace(" ", "")      // Remove spaces
            .replace("-", "")      // Remove dashes
            .replace("(", "")      // Remove parentheses
            .replace(")", "")
            .replace("+", "")      // Remove + (we'll add country code properly)
        
        android.util.Log.d("CallerIdService", "Cleaning number: '$incomingNumber' -> '$cleanNumber'")
        
        // Handle Italian prefixes
        if (cleanNumber.startsWith("39") && cleanNumber.length > 9) {
            // Already has 39 prefix, keep it
            android.util.Log.d("CallerIdService", "Number already has 39 prefix")
        } else if (cleanNumber.startsWith("0039")) {
            // Remove 00 international prefix, keep 39
            cleanNumber = cleanNumber.substring(2)
            android.util.Log.d("CallerIdService", "Removed 00 prefix: $cleanNumber")
        } else if (cleanNumber.startsWith("3") && cleanNumber.length >= 9) {
            // Italian mobile without country code (3xx xxx xxxx) - add 39
            cleanNumber = "39$cleanNumber"
            android.util.Log.d("CallerIdService", "Added 39 prefix: $cleanNumber")
        } else if (cleanNumber.startsWith("0") && cleanNumber.length >= 9) {
            // Italian landline (0xx xxx xxxx) - remove leading 0, add 39
            cleanNumber = "39${cleanNumber.substring(1)}"
            android.util.Log.d("CallerIdService", "Converted landline: $cleanNumber")
        }
        
        android.util.Log.d("CallerIdService", "Final WhatsApp number: '$cleanNumber' (original: '$incomingNumber')")
        
        try {
            // Check if number is empty, private, or too short
            val isValidNumber = cleanNumber.isNotBlank() && 
                                cleanNumber.length >= 10 && 
                                !cleanNumber.contains("Privato", ignoreCase = true) &&
                                !cleanNumber.contains("Sconosciuto", ignoreCase = true)
            
            if (!isValidNumber) {
                android.util.Log.d("CallerIdService", "Number is invalid/private, opening WhatsApp main app")
                // Open WhatsApp main app instead of specific chat
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    setPackage("com.whatsapp")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // WhatsApp not installed, try browser
                    val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                }
            } else {
                // Valid number - open wa.me with cleaned number
                android.util.Log.d("CallerIdService", "Opening wa.me/$cleanNumber")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$cleanNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            android.util.Log.d("CallerIdService", "WhatsApp intent launched successfully")
            
            // FIX: Close overlay after opening WhatsApp
            handler.postDelayed({ closeOverlay() }, 300) // Short delay to let WhatsApp open first
            
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Failed to open WhatsApp: ${e.message}")
            // If WhatsApp fails, try browser fallback
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://web.whatsapp.com/")
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
        android.util.Log.d("CallerIdService", "Rejecting call...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            try {
                val success = telecomManager.endCall()
                android.util.Log.d("CallerIdService", "Call rejected via TelecomManager, success: $success")
            } catch (e: SecurityException) {
                android.util.Log.e("CallerIdService", "Cannot reject call - missing ANSWER_PHONE_CALLS permission: ${e.message}")
                e.printStackTrace()
            }
        } else {
            android.util.Log.w("CallerIdService", "Cannot reject call - API level too low (requires API 28+)")
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
        
        // Fix Kimi: Cancel call state polling
        callStatePoller?.let { handler.removeCallbacks(it) }
        callStatePoller = null
        
        // Fix Kimi: Unregister network callback
        networkCallback?.let { callback ->
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(callback)
                android.util.Log.d("CallerIdService", "Network callback unregistered")
            } catch (e: Exception) {
                android.util.Log.e("CallerIdService", "Error unregistering network callback: ${e.message}")
            }
        }
        networkCallback = null
        
        // Remove all pending callbacks
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
        
        // CRITICAL: Stop foreground to remove notification and allow service to die
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        stopSelf()
    }
    
    // Emergency force close - called when call state is IDLE
    fun forceClose() {
        android.util.Log.d("CallerIdService", "EMERGENCY FORCE CLOSE triggered")
        closeOverlay()
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
