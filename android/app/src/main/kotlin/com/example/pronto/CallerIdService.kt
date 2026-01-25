package com.example.pronto

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.view.*
import android.webkit.*
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import android.telephony.TelephonyManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

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
            .setContentText("Chiamata da: $callerNumber")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
        startForeground(1, notification)
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = WebView(this).apply {
            setBackgroundColor(0)
            settings.javaScriptEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface
                fun performAction(action: String) {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    
                    when (action) {
                        "WHATSAPP" -> {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${callerNumber?.replace(" ", "")}"))
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(i)
                        }
                        "ANSWER" -> {
                            if (ActivityCompat.checkSelfPermission(this@CallerIdService, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    telecomManager.acceptRingingCall()
                                }
                            }
                        }
                        "REJECT" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                telecomManager.endCall()
                            }
                        }
                        "CLOSE" -> {
                            // Solo chiude l'overlay senza altre azioni
                        }
                    }
                    stopSelf()
                }
            }, "AndroidBridge")
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, u: String?) {
                    v?.evaluateJavascript("window.updateCallerNumber('$callerNumber')", null)
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
