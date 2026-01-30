package com.example.pronto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog

/**
 * Helper class for managing battery optimization settings across different OEMs.
 * Handles Samsung OneUI, Pixel/Stock Android, and other manufacturer customizations.
 */
object BatteryOptimizationHelper {
    
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
        
        // Not optimized - show guided dialog
        showBatteryOptimizationDialog(context, launcher, onStatusChanged)
    }
    
    /**
     * Show dialog explaining why battery optimization is needed.
     */
    private fun showBatteryOptimizationDialog(
        context: Context, 
        launcher: ActivityResultLauncher<Intent>?,
        onStatusChanged: ((Boolean) -> Unit)?
    ) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val isSamsung = manufacturer.contains("samsung")
        
        val message = if (isSamsung) {
            "Per garantire che PRONTO funzioni correttamente durante le chiamate, è necessario disattivare l'ottimizzazione batteria.\n\n" +
            "Su Samsung:\n" +
            "1. Tappa 'Apri Impostazioni'\n" +
            "2. Seleziona 'Batteria'\n" +
            "3. Scegli 'Senza restrizioni'\n\n" +
            "⚠️ Senza questa autorizzazione, l'app potrebbe essere chiusa durante le chiamate."
        } else {
            "Per garantire che PRONTO funzioni correttamente durante le chiamate, è necessario disattivare l'ottimizzazione batteria per questa app.\n\n" +
            "1. Tappa 'Apri Impostazioni'\n" +
            "2. Nella schermata che si apre, seleziona 'Consenti'\n\n" +
            "⚠️ Senza questa autorizzazione, l'app potrebbe essere chiusa durante le chiamate."
        }
        
        AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("⚠️ Ottimizzazione Batteria Richiesta")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Apri Impostazioni") { _, _ ->
                launchBatterySettings(context, launcher)
            }
            .setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
                onStatusChanged?.invoke(false)
            }
            .show()
    }
    
    /**
     * Launch appropriate settings screen for battery optimization.
     * Handles different OEM behaviors (Samsung, Pixel, etc.)
     */
    private fun launchBatterySettings(
        context: Context, 
        launcher: ActivityResultLauncher<Intent>?
    ) {
        try {
            // Method 1: Direct request (works on Pixel/Stock Android)
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
                // Add flag only if context is not Activity
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
            // Fallback for Samsung/OEMs that block the intent
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
                
                // Show toast with manual instructions
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
