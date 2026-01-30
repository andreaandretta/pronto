package com.example.pronto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class CallReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CallReceiver"
        private const val DEBOUNCE_MS = 500L
        private const val PRIVATE_NUMBER_DELAY_MS = 1500L // Wait 1.5s for real number
        
        // Race condition prevention: atomic flag for service start
        private val isStartingService = AtomicBoolean(false)
        private var lastRingingTime = 0L
        
        // Track service state
        private var serviceStarted = false
        private var currentServiceNumber: String? = null
    }
    
    // Handler for delayed operations - cancellable
    private val handler = Handler(Looper.getMainLooper())
    private var pendingNumberRunnable: Runnable? = null
    
    // Sanitize phone number to prevent injection
    private fun sanitizePhoneNumber(input: String?): String {
        if (input.isNullOrBlank()) return "Numero Privato"
        // Whitelist: only digits, +, spaces, dashes, parentheses
        return input.replace(Regex("[^0-9+\\s\\-()]"), "").take(20)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            Log.d(TAG, "onReceive triggered")
            
            // Null-safety checks
            if (context == null) {
                Log.e(TAG, "CRASH PREVENTED: context is null")
                return
            }
            
            if (intent == null) {
                Log.e(TAG, "CRASH PREVENTED: intent is null")
                return
            }
            
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                Log.d(TAG, "Ignoring action: ${intent.action}")
                return
            }
            
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            Log.d(TAG, "State: $state, Number: $phoneNumber")
            
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // CRITICAL FIX: Android sends RINGING twice:
                    // 1st event: number=null (broadcast before number is known)
                    // 2nd event: number=real (after system resolves the number)
                    // We must WAIT for the real number, not show "Numero Privato" immediately
                    
                    if (phoneNumber.isNullOrEmpty()) {
                        Log.d(TAG, "RINGING with null number - scheduling 'Numero Privato' in ${PRIVATE_NUMBER_DELAY_MS}ms")
                        
                        // Cancel any existing timer
                        pendingNumberRunnable?.let { handler.removeCallbacks(it) }
                        
                        // Schedule delayed "Numero Privato" - will be cancelled if real number arrives
                        pendingNumberRunnable = Runnable {
                            if (!serviceStarted) {
                                Log.d(TAG, "No real number received after delay - showing 'Numero Privato'")
                                startCallerIdService(context, "Numero Privato")
                            }
                        }
                        handler.postDelayed(pendingNumberRunnable!!, PRIVATE_NUMBER_DELAY_MS)
                        
                        return
                    }
                    
                    // We have a real number!
                    val sanitizedNumber = sanitizePhoneNumber(phoneNumber)
                    Log.d(TAG, "Real number received: $sanitizedNumber")
                    
                    // Cancel pending "Numero Privato" timer
                    pendingNumberRunnable?.let { 
                        handler.removeCallbacks(it)
                        Log.d(TAG, "Cancelled 'Numero Privato' timer - real number arrived")
                    }
                    pendingNumberRunnable = null
                    
                    // Check if service already running with "Numero Privato" - update it!
                    if (serviceStarted && currentServiceNumber == "Numero Privato") {
                        Log.d(TAG, "Service already running with 'Numero Privato' - updating to: $sanitizedNumber")
                        updateCallerIdService(context, sanitizedNumber)
                        return
                    }
                    
                    // Check if service already running with same number - ignore duplicate
                    if (serviceStarted && currentServiceNumber == sanitizedNumber) {
                        Log.d(TAG, "Service already running with same number - ignoring")
                        return
                    }
                    
                    // Debounce: prevent duplicate RINGING with same number
                    val now = System.currentTimeMillis()
                    if (now - lastRingingTime < DEBOUNCE_MS) {
                        Log.d(TAG, "Debounced duplicate RINGING event with number")
                        return
                    }
                    lastRingingTime = now
                    
                    Log.d(TAG, "Starting service for: $sanitizedNumber")
                    startCallerIdService(context, sanitizedNumber)
                }
                TelephonyManager.EXTRA_STATE_IDLE,
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d(TAG, "Call ended or answered")
                    // Cancel any pending timers
                    pendingNumberRunnable?.let { handler.removeCallbacks(it) }
                    pendingNumberRunnable = null
                    // Reset state flags
                    isStartingService.set(false)
                    serviceStarted = false
                    currentServiceNumber = null
                    stopCallerIdService(context)
                }
                else -> {
                    Log.d(TAG, "Unknown state: $state")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRASH in onReceive: ${e.message}")
            e.printStackTrace()
            // Reset flags on crash to prevent stuck state
            isStartingService.set(false)
            pendingNumberRunnable?.let { handler.removeCallbacks(it) }
        }
    }

    private fun startCallerIdService(context: Context, phoneNumber: String) {
        // Atomic check to prevent duplicate service starts (race condition fix)
        if (!isStartingService.compareAndSet(false, true)) {
            Log.d(TAG, "Service already starting, skipping duplicate")
            return
        }
        
        val serviceIntent = Intent(context, CallerIdService::class.java).apply {
            putExtra("phone_number", phoneNumber)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            serviceStarted = true
            currentServiceNumber = phoneNumber
            Log.d(TAG, "CallerIdService started with number: $phoneNumber")
            
            // Reset flag after short delay to allow future starts
            Handler(Looper.getMainLooper()).postDelayed({
                isStartingService.set(false)
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}")
            isStartingService.set(false)
            serviceStarted = false
        }
    }
    
    private fun updateCallerIdService(context: Context, phoneNumber: String) {
        // Send update to existing service
        val updateIntent = Intent(context, CallerIdService::class.java).apply {
            action = "UPDATE_NUMBER"
            putExtra("phone_number", phoneNumber)
        }
        
        try {
            context.startService(updateIntent)
            currentServiceNumber = phoneNumber
            Log.d(TAG, "CallerIdService updated with new number: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating service: ${e.message}")
        }
    }

    private fun stopCallerIdService(context: Context) {
        try {
            context.stopService(Intent(context, CallerIdService::class.java))
            Log.d(TAG, "CallerIdService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}")
        }
    }
}
