package com.example.pronto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PRONTO"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 101
        private const val BATTERY_OPTIMIZATION_REQUEST_CODE = 102
        private const val PREFS_NAME = "pronto_settings"
        private const val KEY_ENABLED = "enabled"
    }
    
    // UI Elements
    private lateinit var mainToggle: Switch
    private lateinit var statusText: TextView
    private lateinit var statusIcon: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var permissionsCard: LinearLayout
    
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
        createModernUI()
        loadSavedState()
        updatePermissionStatus()
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
    
    private fun createModernUI() {
        // Main container with dark theme
        val mainLayout = ScrollView(this).apply {
            setBackgroundColor(0xFF0f172a.toInt()) // slate-900
        }
        
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }
        
        // === HEADER ===
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 48)
        }
        
        val logoText = TextView(this).apply {
            text = "📞"
            textSize = 40f
            setPadding(0, 0, 16, 0)
        }
        
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val title = TextView(this).apply {
            text = "PRONTO"
            textSize = 28f
            setTextColor(0xFF14b8a6.toInt()) // teal-500
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }
        
        val subtitle = TextView(this).apply {
            text = "WhatsApp Click-to-Chat"
            textSize = 14f
            setTextColor(0xFF94a3b8.toInt()) // slate-400
        }
        
        titleLayout.addView(title)
        titleLayout.addView(subtitle)
        headerLayout.addView(logoText)
        headerLayout.addView(titleLayout)
        
        // === MAIN TOGGLE CARD ===
        val toggleCard = createCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
        }
        
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        statusIcon = TextView(this).apply {
            text = "⚡"
            textSize = 32f
            setPadding(0, 0, 24, 0)
        }
        
        val toggleTextLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val toggleTitle = TextView(this).apply {
            text = "PRONTO"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        
        statusText = TextView(this).apply {
            text = "In attesa..."
            textSize = 13f
            setTextColor(0xFF94a3b8.toInt())
        }
        
        toggleTextLayout.addView(toggleTitle)
        toggleTextLayout.addView(statusText)
        
        mainToggle = Switch(this).apply {
            isChecked = true
            scaleX = 1.3f
            scaleY = 1.3f
            setOnCheckedChangeListener { _, isChecked ->
                onToggleChanged(isChecked)
            }
        }
        
        toggleRow.addView(statusIcon)
        toggleRow.addView(toggleTextLayout)
        toggleRow.addView(mainToggle)
        toggleCard.addView(toggleRow)
        
        // === PERMISSIONS CARD ===
        permissionsCard = createCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        
        val permissionsTitle = TextView(this).apply {
            text = "Configurazione"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 24)
        }
        
        // Overlay permission row
        val overlayRow = createPermissionRow("Overlay su altre app")
        overlayStatus = overlayRow.second
        
        // Battery permission row
        val batteryRow = createPermissionRow("Ottimizzazione batteria")
        batteryStatus = batteryRow.second
        
        // Configure button
        val configureButton = Button(this).apply {
            text = "Configura Permessi"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonBackground(0xFF14b8a6.toInt())
            setPadding(32, 24, 32, 24)
            setOnClickListener { startPermissionWizard() }
        }
        
        permissionsCard.addView(permissionsTitle)
        permissionsCard.addView(overlayRow.first)
        permissionsCard.addView(batteryRow.first)
        permissionsCard.addView(configureButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 32 })
        
        // === INFO CARD ===
        val infoCard = createCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        
        val infoTitle = TextView(this).apply {
            text = "Cos'è PRONTO?"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        
        val infoText = TextView(this).apply {
            text = "Quando ricevi una chiamata, PRONTO mostra un pulsante per aprire rapidamente una chat WhatsApp con quel numero.\n\nPerfetto per rispondere via messaggio quando non puoi parlare."
            textSize = 13f
            setTextColor(0xFF94a3b8.toInt())
            lineSpacingMultiplier = 1.4f
        }
        
        val versionText = TextView(this).apply {
            text = "Versione 1.0.0"
            textSize = 11f
            setTextColor(0xFF64748b.toInt())
            setPadding(0, 24, 0, 0)
        }
        
        infoCard.addView(infoTitle)
        infoCard.addView(infoText)
        infoCard.addView(versionText)
        
        // === ASSEMBLE LAYOUT ===
        contentLayout.addView(headerLayout)
        contentLayout.addView(toggleCard, createCardLayoutParams())
        contentLayout.addView(permissionsCard, createCardLayoutParams())
        contentLayout.addView(infoCard, createCardLayoutParams())
        
        mainLayout.addView(contentLayout)
        setContentView(mainLayout)
    }
    
    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1e293b.toInt()) // slate-800
                cornerRadius = 32f
            }
        }
    }
    
    private fun createCardLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 }
    }
    
    private fun createPermissionRow(label: String): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }
        
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFFe2e8f0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val statusView = TextView(this).apply {
            text = "⏳"
            textSize = 14f
        }
        
        row.addView(labelView)
        row.addView(statusView)
        
        return Pair(row, statusView)
    }
    
    private fun createButtonBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 24f
        }
    }
    
    private fun loadSavedState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_ENABLED, true)
        mainToggle.isChecked = isEnabled
        onToggleChanged(isEnabled)
    }
    
    private fun onToggleChanged(isEnabled: Boolean) {
        // Save state
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, isEnabled)
            .apply()
        
        if (isEnabled) {
            statusText.text = "Attivo - In ascolto chiamate"
            statusText.setTextColor(0xFF10b981.toInt()) // green
            statusIcon.text = "⚡"
            android.util.Log.d(TAG, "PRONTO enabled")
        } else {
            statusText.text = "In pausa - Overlay disattivato"
            statusText.setTextColor(0xFF64748b.toInt()) // gray
            statusIcon.text = "💤"
            android.util.Log.d(TAG, "PRONTO disabled")
        }
    }
    
    private fun updatePermissionStatus() {
        // Check overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        overlayStatus.text = if (hasOverlay) "✅" else "❌"
        
        // Check battery optimization
        val hasBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
        batteryStatus.text = if (hasBattery) "✅" else "❌"
        
        // Hide permissions card if all granted
        val allGranted = hasOverlay && hasBattery && checkRuntimePermissions()
        permissionsCard.visibility = if (allGranted) View.GONE else View.VISIBLE
    }
    
    private fun checkRuntimePermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun startPermissionWizard() {
        // Step 1: Check overlay
        if (!Settings.canDrawOverlays(this)) {
            showOverlayDialog()
            return
        }
        
        // Step 2: Check battery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryDialog()
                return
            }
        }
        
        // Step 3: Check runtime permissions
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
            return
        }
        
        // All done!
        Toast.makeText(this, "✅ Configurazione completata!", Toast.LENGTH_SHORT).show()
        updatePermissionStatus()
    }
    
    private fun showOverlayDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Permesso Overlay")
            .setMessage("PRONTO deve mostrare un pulsante sopra le altre app durante le chiamate in arrivo.\n\nNella prossima schermata, trova PRONTO e attiva 'Consenti visualizzazione sopra altre app'.")
            .setPositiveButton("Apri Impostazioni") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
    
    private fun showBatteryDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Ottimizzazione Batteria")
            .setMessage("Per funzionare quando il telefono è in standby, PRONTO deve ignorare le ottimizzazioni batteria.\n\nAltrimenti Android potrebbe chiudere l'app durante le chiamate.")
            .setPositiveButton("Disattiva Ottimizzazione") { _, _ ->
                requestBatteryOptimizationExemption()
            }
            .setNegativeButton("Dopo", null)
            .show()
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "✅ Permesso overlay concesso!", Toast.LENGTH_SHORT).show()
                    // Continue wizard
                    startPermissionWizard()
                } else {
                    Toast.makeText(this, "❌ Permesso overlay necessario", Toast.LENGTH_LONG).show()
                }
                updatePermissionStatus()
            }
            BATTERY_OPTIMIZATION_REQUEST_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (pm.isIgnoringBatteryOptimizations(packageName)) {
                        Toast.makeText(this, "✅ Ottimizzazione batteria disattivata!", Toast.LENGTH_SHORT).show()
                        // Continue wizard
                        startPermissionWizard()
                    } else {
                        Toast.makeText(this, "⚠️ L'ottimizzazione è ancora attiva", Toast.LENGTH_LONG).show()
                    }
                }
                updatePermissionStatus()
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
            updatePermissionStatus()
        }
    }
    
    /**
     * Check if PRONTO is currently enabled (for CallReceiver to check)
     */
    fun isEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }
}
