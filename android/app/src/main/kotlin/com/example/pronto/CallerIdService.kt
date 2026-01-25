package com.example.pronto

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat

class CallerIdService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var callerNumber: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("pronto_ch", "PRONTO Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        callerNumber = intent?.getStringExtra("caller_number")
        val notification = NotificationCompat.Builder(this, "pronto_ch")
            .setContentTitle("PRONTO")
            .setContentText("Badge attivo per: \$callerNumber")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
        startForeground(1, notification)
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        // Layout compatto: solo in alto, altezza minima, NON blocca i tocchi sotto
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 50 // Un po' di margine dal bordo superiore
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0)
            settings.javaScriptEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface
                fun performAction(action: String) {
                    if (action == "WHATSAPP") {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/\${callerNumber?.replace(\" \", \"\")}"))
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(i)
                    }
                    stopSelf()
                }
            }, "AndroidBridge")
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, u: String?) {
                    v?.evaluateJavascript("window.updateCallerNumber('\$callerNumber')", null)
                }
            }
            loadUrl("file:///android_asset/www/index.html")
        }
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}
