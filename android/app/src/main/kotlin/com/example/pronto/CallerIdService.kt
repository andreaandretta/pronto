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

class CallerIdService : Service() {
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    private var incomingNumber: String = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        incomingNumber = intent?.getStringExtra("phone_number") ?: ""
        android.util.Log.d("CallerIdService", "Starting overlay for number: $incomingNumber")
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        webView = WebView(this).apply {
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
            
            addJavascriptInterface(AndroidBridge(), "Android")
            
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
                    val escapedNumber = incomingNumber.replace("'", "\\'")
                    view?.evaluateJavascript(
                        """
                        (function() {
                            console.log('PRONTO: Page finished loading');
                            if(window.setPhoneNumber) {
                                window.setPhoneNumber('$escapedNumber');
                                return 'setPhoneNumber called successfully';
                            } else {
                                console.error('PRONTO: setPhoneNumber not found!');
                                return 'setPhoneNumber NOT FOUND';
                            }
                        })();
                        """.trimIndent(),
                        { result ->
                            android.util.Log.d("CallerIdService", "JS Result: $result")
                        }
                    )
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

        try {
            windowManager?.addView(webView, params)
            android.util.Log.d("CallerIdService", "Overlay added to window manager")
        } catch (e: Exception) {
            android.util.Log.e("CallerIdService", "Error adding overlay: ${e.message}")
        }
    }

    private fun loadMainContent() {
        val assetPath = "file:///android_asset/www/index.html"
        android.util.Log.d("CallerIdService", "Loading URL: $assetPath")
        webView?.loadUrl(assetPath)
        
        // Fallback: se dopo 3 secondi la pagina non è caricata, prova HTML inline
        handler.postDelayed({
            webView?.evaluateJavascript(
                "(function() { return document.readyState; })();",
                { result ->
                    android.util.Log.d("CallerIdService", "Document readyState: $result")
                    if (result == null || result == "null" || result.contains("loading")) {
                        android.util.Log.w("CallerIdService", "Page may not be loaded properly, using fallback")
                        // Non usiamo il fallback immediatamente per evitare flash
                    }
                }
            )
        }, 3000)
    }

    private fun loadFallbackHtml() {
        val fallbackHtml = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <title>PRONTO - Fallback</title>
        <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                min-height: 100vh;
                background: linear-gradient(135deg, #0d9488 0%, #10b981 100%);
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 16px;
            }
            .card {
                background: white;
                border-radius: 32px;
                width: 100%;
                max-width: 360px;
                overflow: hidden;
                box-shadow: 0 25px 50px -12px rgba(0,0,0,0.25);
            }
            .header {
                background: linear-gradient(135deg, #115e59 0%, #0d9488 100%);
                padding: 32px 24px;
                text-align: center;
            }
            .avatar {
                width: 72px;
                height: 72px;
                background: rgba(255,255,255,0.2);
                border-radius: 50%;
                margin: 0 auto 16px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 32px;
            }
            .header p { color: #5eead4; font-size: 13px; margin-bottom: 8px; }
            .header h1 { color: white; font-size: 22px; font-weight: 600; margin-bottom: 4px; }
            .header .number { color: #5eead4; font-size: 15px; }
            .content { padding: 20px; }
            .btn {
                width: 100%;
                padding: 16px;
                border: none;
                border-radius: 16px;
                font-size: 16px;
                font-weight: 600;
                cursor: pointer;
                margin-bottom: 12px;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 8px;
            }
            .btn-whatsapp { background: #25D366; color: white; }
            .btn-answer { background: #f1f5f9; color: #475569; }
            .btn-reject { background: #ef4444; color: white; }
            .btn-close { background: #f1f5f9; color: #64748b; font-size: 14px; padding: 12px; }
            .row { display: flex; gap: 12px; margin-bottom: 12px; }
            .row .btn { flex: 1; margin-bottom: 0; font-size: 14px; }
            .footer { text-align: center; color: #5eead4; font-size: 11px; margin-top: 16px; opacity: 0.6; }
        </style>
        </head>
        <body>
        <div class="card">
            <div class="header">
                <div class="avatar">📞</div>
                <p>Chiamata in arrivo</p>
                <h1 id="callerName">Numero Sconosciuto</h1>
                <p class="number" id="phoneNumber">$incomingNumber</p>
            </div>
            <div class="content">
                <button class="btn btn-whatsapp" onclick="action('WHATSAPP')">
                    📱 Apri WhatsApp
                </button>
                <div class="row">
                    <button class="btn btn-answer" onclick="action('ANSWER')">
                        📞 Rispondi
                    </button>
                    <button class="btn btn-reject" onclick="action('REJECT')">
                        🛏️ Rifiuta
                    </button>
                </div>
                <button class="btn btn-close" onclick="action('CLOSE')">Chiudi</button>
            </div>
        </div>
        <p class="footer">PRONTO • WhatsApp Click-to-Chat (Fallback Mode)</p>
        <script>
            function action(type) {
                console.log('Action:', type);
                if (window.Android && window.Android.performAction) {
                    window.Android.performAction(type);
                }
            }
            // Update number from Android bridge
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
            android.util.Log.d("CallerIdService", "Action received: $action")
            when (action.uppercase()) {
                "WHATSAPP" -> openWhatsApp()
                "ANSWER" -> answerCall()
                "REJECT" -> rejectCall()
                "CLOSE" -> closeOverlay()
            }
        }
        
        @JavascriptInterface
        fun getPhoneNumber(): String {
            return incomingNumber
        }
        
        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("WebViewJS", message)
        }
    }

    private fun openWhatsApp() {
        val cleanNumber = incomingNumber.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$cleanNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        closeOverlay()
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
        closeOverlay()
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
        android.util.Log.d("CallerIdService", "Closing overlay")
        handler.removeCallbacksAndMessages(null)
        webView?.let { 
            try {
                windowManager?.removeView(it) 
            } catch (e: Exception) {
                android.util.Log.e("CallerIdService", "Error removing view: ${e.message}")
            }
        }
        webView = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "caller_id_channel",
            "Caller ID",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "caller_id_channel")
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
