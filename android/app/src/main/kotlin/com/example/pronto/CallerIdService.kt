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
import android.os.IBinder
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class CallerIdService : Service() {
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    private var incomingNumber: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        incomingNumber = intent?.getStringExtra("phone_number") ?: ""
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
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(
                        "window.updateCallerInfo && window.updateCallerInfo('$incomingNumber', 'Numero Sconosciuto')",
                        null
                    )
                }
            }
            loadUrl("file:///android_asset/www/index.html")
        }

        windowManager?.addView(webView, params)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun performAction(action: String) {
            when (action) {
                "whatsapp" -> openWhatsApp()
                "answer" -> answerCall()
                "reject" -> rejectCall()
                "close" -> closeOverlay()
            }
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
        webView?.let { windowManager?.removeView(it) }
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
        closeOverlay()
        super.onDestroy()
    }
}
