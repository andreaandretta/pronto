package com.example.pronto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 101
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Crea UI programmaticamente
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 100, 48, 48)
            setBackgroundColor(0xFF1a1a2e.toInt())
        }

        val title = TextView(this).apply {
            text = "📱 PRONTO"
            textSize = 32f
            setTextColor(0xFF25D366.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val subtitle = TextView(this).apply {
            text = "WhatsApp Click-to-Chat"
            textSize = 16f
            setTextColor(0xFFcccccc.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }

        val statusText = TextView(this).apply {
            text = "Stato: Verifica permessi..."
            textSize = 14f
            setTextColor(0xFFffffff.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val testButton = Button(this).apply {
            text = "🧪 TEST OVERLAY"
            setBackgroundColor(0xFF25D366.toInt())
            setTextColor(0xFFffffff.toInt())
            setPadding(32, 24, 32, 24)
            setOnClickListener {
                testOverlay()
            }
        }

        val permissionButton = Button(this).apply {
            text = "⚙️ VERIFICA PERMESSI"
            setBackgroundColor(0xFF4a4a6a.toInt())
            setTextColor(0xFFffffff.toInt())
            setPadding(32, 24, 32, 24)
            setOnClickListener {
                checkAndRequestPermissions()
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(testButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 16, 0, 16) })
        layout.addView(permissionButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 16, 0, 16) })

        setContentView(layout)

        // Richiedi permessi all'avvio
        checkAndRequestPermissions()
    }

    private fun testOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Abilita permesso overlay!", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        
        val intent = Intent(this, CallerIdService::class.java).apply {
            putExtra("phone_number", "+39 333 1234567")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "Overlay test avviato!", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestPermissions() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }

        // Check other permissions
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Toast.makeText(this, "✅ Tutti i permessi OK!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "✅ Permesso overlay OK!", Toast.LENGTH_SHORT).show()
                checkAndRequestPermissions()
            } else {
                Toast.makeText(this, "❌ Permesso overlay necessario!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "✅ Tutti i permessi concessi!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Alcuni permessi mancanti", Toast.LENGTH_LONG).show()
            }
        }
    }
}
