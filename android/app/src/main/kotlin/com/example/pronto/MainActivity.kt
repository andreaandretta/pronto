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
    private lateinit var phoneStatus: TextView
    private lateinit var notificationStatus: TextView
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
        // Always refresh permission status when returning to app
        android.util.Log.d(TAG, "onResume: Refreshing all permission statuses")
        updatePermissionStatus()
        
        // Force UI refresh
        permissionsCard.invalidate()
        permissionsCard.requestLayout()
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
            text = "Autorizzazioni Richieste"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        
        val permissionsSubtitle = TextView(this).apply {
            text = "Tutte le autorizzazioni sono necessarie per funzionare"
            textSize = 12f
            setTextColor(0xFF94a3b8.toInt())
            setPadding(0, 0, 0, 24)
        }
        
        // 1. Overlay permission row
        val overlayRow = createPermissionRow("Overlay su altre app", "Mostra pulsante durante chiamate")
        overlayStatus = overlayRow.second
        overlayRow.first.setOnClickListener { showOverlayDialog() }
        
        // 2. Phone state permission row
        val phoneRow = createPermissionRow("Stato telefono", "Rileva chiamate in arrivo")
        phoneStatus = phoneRow.second
        phoneRow.first.setOnClickListener { requestPhonePermissions() }
        
        // 3. Notification permission row (Android 13+)
        val notificationRow = createPermissionRow("Notifiche", "Mostra notifica servizio attivo")
        notificationStatus = notificationRow.second
        notificationRow.first.setOnClickListener { requestNotificationPermission() }
        
        // 4. Battery permission row
        val batteryRow = createPermissionRow("Ottimizzazione batteria", "Funziona in background")
        batteryStatus = batteryRow.second
        batteryRow.first.setOnClickListener { showBatteryDialog() }
        
        // Configure ALL button
        val configureButton = Button(this).apply {
            text = "🔧 Configura Tutti i Permessi"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonBackground(0xFF14b8a6.toInt())
            setPadding(32, 24, 32, 24)
            setOnClickListener { startPermissionWizard() }
        }
        
        permissionsCard.addView(permissionsTitle)
        permissionsCard.addView(permissionsSubtitle)
        permissionsCard.addView(overlayRow.first)
        permissionsCard.addView(phoneRow.first)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsCard.addView(notificationRow.first)
        }
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
            text = "Quando ricevi una chiamata, PRONTO mostra " +
                   "un pulsante per aprire WhatsApp con quel numero. " +
                   "Perfetto per rispondere via messaggio."
            textSize = 13f
            setTextColor(0xFF94a3b8.toInt())
            setLineSpacing(0f, 1.4f)
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
    
    private fun createPermissionRow(label: String, subtitle: String): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                setColor(0xFF334155.toInt())
                cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFFe2e8f0.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(0xFF94a3b8.toInt())
        }
        
        textContainer.addView(labelView)
        textContainer.addView(subtitleView)
        
        val statusView = TextView(this).apply {
            text = "⏳"
            textSize = 20f
            setPadding(16, 0, 0, 0)
        }
        
        row.addView(textContainer)
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
        var isEnabled = prefs.getBoolean(KEY_ENABLED, true)
        
        // CRITICAL FIX: If permissions not granted, force disable
        if (isEnabled && !checkAllPermissionsGranted()) {
            android.util.Log.w(TAG, "Saved state was ENABLED but permissions missing - forcing disable")
            isEnabled = false
            // Update saved state to match
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        }
        
        mainToggle.isChecked = isEnabled
        onToggleChanged(isEnabled)
    }
    
    private fun onToggleChanged(isEnabled: Boolean) {
        // CRITICAL FIX: Check permissions before enabling
        if (isEnabled && !checkAllPermissionsGranted()) {
            // If permissions missing, disable toggle and show error
            mainToggle.isChecked = false
            statusText.text = "❌ Concedi tutti i permessi prima di attivare"
            statusText.setTextColor(0xFFef4444.toInt()) // red
            statusIcon.text = "🚫"
            permissionsCard.visibility = View.VISIBLE
            Toast.makeText(this, "⚠️ Concedi tutti i permessi in 'Configura Tutti i Permessi'", Toast.LENGTH_LONG).show()
            android.util.Log.w(TAG, "User tried to enable PRONTO without all permissions")
            
            // Save as disabled
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, false)
                .apply()
            return
        }
        
        // Save state
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, isEnabled)
            .apply()
        
        if (isEnabled) {
            statusText.text = "Attivo - In ascolto chiamate"
            statusText.setTextColor(0xFF10b981.toInt()) // green
            statusIcon.text = "⚡"
            Toast.makeText(this, "✅ PRONTO attivo!", Toast.LENGTH_SHORT).show()
            android.util.Log.d(TAG, "PRONTO enabled")
        } else {
            statusText.text = "In pausa - Overlay disattivato"
            statusText.setTextColor(0xFF64748b.toInt()) // gray
            statusIcon.text = "💤"
            android.util.Log.d(TAG, "PRONTO disabled")
        }
    }
    
    private fun checkAllPermissionsGranted(): Boolean {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                       ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val hasBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
        
        return hasOverlay && hasPhone && hasNotification && hasBattery
    }
    
    private fun updatePermissionStatus() {
        android.util.Log.d(TAG, "updatePermissionStatus() called")
        
        // Check overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        overlayStatus.text = if (hasOverlay) "✅" else "❌"
        android.util.Log.d(TAG, "  Overlay: $hasOverlay")
        
        // Check phone permissions
        val hasPhone = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                       ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        phoneStatus.text = if (hasPhone) "✅" else "❌"
        android.util.Log.d(TAG, "  Phone: $hasPhone")
        
        // Check notification permission (Android 13+)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        notificationStatus.text = if (hasNotification) "✅" else "❌"
        android.util.Log.d(TAG, "  Notification: $hasNotification")
        
        // Check battery optimization
        val hasBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
            android.util.Log.d(TAG, "  Battery optimization ignored: $ignoring")
            ignoring
        } else true
        batteryStatus.text = if (hasBattery) "✅" else "❌"
        
        // Force immediate UI update
        overlayStatus.invalidate()
        phoneStatus.invalidate()
        notificationStatus.invalidate()
        batteryStatus.invalidate()
        
        // Hide permissions card if all granted
        val allGranted = hasOverlay && hasPhone && hasNotification && hasBattery
        permissionsCard.visibility = if (allGranted) View.GONE else View.VISIBLE
        
        android.util.Log.d(TAG, "  All granted: $allGranted, Card visible: ${!allGranted}")
        
        // If all permissions granted, show success toast
        if (allGranted) {
            Toast.makeText(this, "✅ Tutte le autorizzazioni OK!", Toast.LENGTH_SHORT).show()
        }
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
        
        // Step 2: Check phone permissions
        val hasPhone = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                       ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (!hasPhone) {
            requestPhonePermissions()
            return
        }
        
        // Step 3: Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotification = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!hasNotification) {
                requestNotificationPermission()
                return
            }
        }
        
        // Step 4: Check battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryDialog()
                return
            }
        }
        
        // All done!
        Toast.makeText(this, "✅ Configurazione completata!", Toast.LENGTH_SHORT).show()
        updatePermissionStatus()
    }
    
    private fun requestPhonePermissions() {
        val phonePermissions = arrayOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.ANSWER_PHONE_CALLS
        )
        ActivityCompat.requestPermissions(this, phonePermissions, PERMISSION_REQUEST_CODE)
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, 
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 
                PERMISSION_REQUEST_CODE
            )
        }
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
                // Fix Kimi: Check battery optimization status and update UI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (pm.isIgnoringBatteryOptimizations(packageName)) {
                        Toast.makeText(this, "✅ Ottimizzazione batteria disattivata!", Toast.LENGTH_SHORT).show()
                        // Continue wizard
                        startPermissionWizard()
                    } else {
                        // Fix Kimi: More specific message to guide user
                        Toast.makeText(this, "⚠️ Devi ancora disattivare l'ottimizzazione per PRONTO nelle impostazioni", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, "✅ Permessi concessi!", Toast.LENGTH_SHORT).show()
                // Continue wizard to get remaining permissions
                startPermissionWizard()
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
