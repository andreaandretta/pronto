package com.example.pronto

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import android.content.pm.PackageManager

/**
 * Helper class for managing battery optimization settings across different OEMs.
 * Handles Samsung OneUI, Xiaomi MIUI, Pixel/Stock Android, and other manufacturer customizations.
 */
object BatteryOptimizationHelper {
    
    // OEM Constants
    private const val SAMSUNG = "samsung"
    private const val XIAOMI = "xiaomi"
    private const val REDMI = "redmi"
    private const val POCO = "poco"
    private const val HUAWEI = "huawei"
    private const val OPPO = "oppo"
    private const val VIVO = "vivo"
    private const val ONEPLUS = "oneplus"
    
    /**
     * Check if battery optimization is disabled for this app.
     * If not, show dialog to request user action.
     */
    fun checkAndRequestBatteryOptimization(
        context: Context, 
        launcher: ActivityResultLauncher<Intent>? = null,
        onStatusChanged: ((Boolean) -> Unit)? = null
    ) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // Already optimized
            onStatusChanged?.invoke(true)
            return
        }
        
        // Not optimized - show guided dialog with OEM-specific instructions
        showBatteryOptimizationDialog(context, launcher, onStatusChanged)
    }
    
    /**
     * Detect device manufacturer
     */
    private fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
    
    /**
     * Show dialog explaining why battery optimization is needed.
     * Includes OEM-specific instructions.
     */
    private fun showBatteryOptimizationDialog(
        context: Context, 
        launcher: ActivityResultLauncher<Intent>?,
        onStatusChanged: ((Boolean) -> Unit)?
    ) {
        val manufacturer = getManufacturer()
        val message = getOEMSpecificMessage(manufacturer)
        val title = getOEMSpecificTitle(manufacturer)
        
        AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Apri Impostazioni") { _, _ ->
                launchOEMSpecificBatterySettings(context, launcher, manufacturer)
            }
            .setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
                onStatusChanged?.invoke(false)
            }
            .show()
    }
    
    /**
     * Get OEM-specific dialog title
     */
    private fun getOEMSpecificTitle(manufacturer: String): String {
        return when {
            manufacturer.contains(SAMSUNG) -> "⚠️ Ottimizzazione Samsung (OneUI)"
            manufacturer.contains(XIAOMI) || 
            manufacturer.contains(REDMI) || 
            manufacturer.contains(POCO) -> "⚠️ Ottimizzazione Xiaomi (MIUI)"
            manufacturer.contains(HUAWEI) -> "⚠️ Ottimizzazione Huawei (EMUI)"
            else -> "⚠️ Ottimizzazione Batteria Richiesta"
        }
    }
    
    /**
     * Get OEM-specific instructions message
     */
    private fun getOEMSpecificMessage(manufacturer: String): String {
        return when {
            manufacturer.contains(SAMSUNG) -> {
                "Per garantire che PRONTO funzioni correttamente durante le chiamate, è necessario disattivare l'ottimizzazione batteria.\n\n" +
                "ISTRUZIONI SAMSUNG:\n" +
                "1. Tappa 'Apri Impostazioni'\n" +
                "2. Vai su 'Device Care' o 'Manutenzione dispositivo'\n" +
                "3. Seleziona 'Batteria' → 'Utilizzo batteria'\n" +
                "4. Trova PRONTO nell'elenco e seleziona 'Non ottimizzata'\n\n" +
                "⚠️ Su alcuni modelli: Impostazioni → App → PRONTO → Batteria → Senza restrizioni"
            }
            manufacturer.contains(XIAOMI) || 
            manufacturer.contains(REDMI) || 
            manufacturer.contains(POCO) -> {
                "Per garantire che PRONTO funzioni correttamente durante le chiamate, è necessario configurare l'avvio automatico.\n\n" +
                "ISTRUZIONI XIAOMI/MIUI:\n" +
                "1. Tappa 'Apri Impostazioni'\n" +
                "2. Vai su 'App' → 'Autorizzazioni' → 'Avvio automatico'\n" +
                "3. Trova PRONTO e ABILITA l'avvio automatico\n" +
                "4. Poi vai su 'Risparmio energetico' → 'Nessuna restrizione'\n\n" +
                "⚠️ Questi passaggi sono OBBLIGATORI su Xiaomi/Redmi/POCO"
            }
            manufacturer.contains(HUAWEI) -> {
                "Per garantire che PRONTO funzioni correttamente durante le chiamate, è necessario disattivare l'ottimizzazione batteria.\n\n" +
                "ISTRUZIONI HUAWEI:\n" +
                "1. Tappa 'Apri Impostazioni'\n" +
                "2. Vai su 'Batteria' → 'Avvio app'\n" +
                "3. Trova PRONTO e imposta su 'Gestisci manualmente'\n" +
                "4. Abilita tutte le opzioni (Avvio secondario, ecc.)\n\n" +
                "⚠️ Su alcuni modelli: Impostazioni → App → PRONTO → Batteria"
            }
            else -> {
                "Per garantire che PRONTO funzioni correttamente durante le chiamate, è necessario disattivare l'ottimizzazione batteria per questa app.\n\n" +
                "1. Tappa 'Apri Impostazioni'\n" +
                "2. Cerca 'Ottimizzazione batteria' o 'Risparmio energetico'\n" +
                "3. Trova PRONTO e seleziona 'Non ottimizzata' o 'Senza restrizioni'\n\n" +
                "⚠️ Senza questa autorizzazione, l'app potrebbe essere chiusa durante le chiamate."
            }
        }
    }
    
    /**
     * Launch OEM-specific battery settings with fallback chain
     */
    private fun launchOEMSpecificBatterySettings(
        context: Context, 
        launcher: ActivityResultLauncher<Intent>?,
        manufacturer: String
    ) {
        when {
            manufacturer.contains(SAMSUNG) -> launchSamsungBatterySettings(context, launcher)
            manufacturer.contains(XIAOMI) || 
            manufacturer.contains(REDMI) || 
            manufacturer.contains(POCO) -> launchXiaomiBatterySettings(context, launcher)
            else -> launchGenericBatterySettings(context, launcher)
        }
    }
    
    /**
     * Launch Samsung-specific battery settings (Device Care / Smart Manager)
     */
    private fun launchSamsungBatterySettings(
        context: Context,
        launcher: ActivityResultLauncher<Intent>?
    ) {
        val packageManager = context.packageManager
        
        // Try Samsung Device Care intent first
        val samsungIntents = listOf(
            // Device Care (newer Samsung devices)
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.lool.MainActivity"
                )
            },
            // Alternative Device Care path
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            },
            // Smart Manager (older Samsung devices)
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.MainActivity"
                )
            },
            // Battery settings directly
            Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$BatterySettingsActivity"
                )
            }
        )
        
        // Try each intent
        for (intent in samsungIntents) {
            try {
                if (intent.resolveActivity(packageManager) != null) {
                    android.util.Log.d("BatteryOptimization", "Launching Samsung intent: ${intent.component}")
                    if (context !is Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launcher != null && context is Activity) {
                        launcher.launch(intent)
                    } else {
                        context.startActivity(intent)
                    }
                    // Show specific toast for Samsung
                    Toast.makeText(
                        context,
                        "Cerca 'PRONTO' e imposta 'Non ottimizzata'",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("BatteryOptimization", "Samsung intent failed: ${e.message}")
                continue
            }
        }
        
        // All Samsung-specific intents failed, use generic fallback
        android.util.Log.w("BatteryOptimization", "All Samsung intents failed, using generic fallback")
        launchGenericBatterySettings(context, launcher)
    }
    
    /**
     * Launch Xiaomi-specific battery settings (MIUI AutoStart + BatterySaver)
     */
    private fun launchXiaomiBatterySettings(
        context: Context,
        launcher: ActivityResultLauncher<Intent>?
    ) {
        val packageManager = context.packageManager
        
        // Try Xiaomi-specific intents
        val xiaomiIntents = listOf(
            // Auto-start manager (most important for MIUI)
            Intent("miui.intent.action.OP_AUTO_START"),
            // Permission settings
            Intent("miui.intent.action.APP_PERM_EDITOR"),
            // Battery optimization
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            },
            // Alternative battery settings
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.appmanager.ApplicationsDetailsActivity"
                )
                putExtra("package_name", context.packageName)
            },
            // Security app
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
        )
        
        // Try each intent
        for (intent in xiaomiIntents) {
            try {
                if (intent.resolveActivity(packageManager) != null) {
                    android.util.Log.d("BatteryOptimization", "Launching Xiaomi intent: $intent")
                    if (context !is Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launcher != null && context is Activity) {
                        launcher.launch(intent)
                    } else {
                        context.startActivity(intent)
                    }
                    // Show specific toast for Xiaomi (need both steps)
                    Toast.makeText(
                        context,
                        "Abilita 'Avvio automatico' E 'Nessuna restrizione' batteria",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("BatteryOptimization", "Xiaomi intent failed: ${e.message}")
                continue
            }
        }
        
        // All Xiaomi-specific intents failed
        android.util.Log.w("BatteryOptimization", "All Xiaomi intents failed, using generic fallback")
        launchGenericBatterySettings(context, launcher)
    }
    
    /**
     * Launch generic battery settings (fallback for all devices)
     */
    private fun launchGenericBatterySettings(
        context: Context, 
        launcher: ActivityResultLauncher<Intent>?
    ) {
        try {
            // Method 1: Direct request (works on Pixel/Stock Android)
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            
            if (launcher != null && context is Activity) {
                launcher.launch(intent)
            } else {
                context.startActivity(intent)
            }
            
        } catch (e: Exception) {
            // Fallback for OEMs that block the intent
            try {
                val fallbackIntent = Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (launcher != null && context is Activity) {
                    launcher.launch(fallbackIntent)
                } else {
                    context.startActivity(fallbackIntent)
                }
                
                Toast.makeText(
                    context, 
                    "Seleziona 'Batteria' > 'Senza restrizioni'", 
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e2: Exception) {
                // Last resort: open general settings
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                
                Toast.makeText(
                    context,
                    "Vai in Impostazioni > App > PRONTO > Batteria > Senza restrizioni",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Check battery optimization status.
     * @return true if ignoring optimizations (good), false if still optimized (bad)
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Pre-Marshmallow doesn't have this restriction
        }
    }
    
    /**
     * Check status on resume and invoke callback.
     * Call this in Activity.onResume()
     */
    fun onResumeCheck(context: Context, onStatusChanged: ((Boolean) -> Unit)? = null): Boolean {
        val isIgnored = isIgnoringBatteryOptimizations(context)
        onStatusChanged?.invoke(isIgnored)
        return isIgnored
    }
}
