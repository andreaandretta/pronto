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
        
        // Race condition prevention: atomic flag for service start
        private val isStartingService = AtomicBoolean(false)
        private var lastRingingTime = 0L
    }
    
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
                    // Debounce: prevent duplicate RINGING broadcasts
                    val now = System.currentTimeMillis()
                    if (now - lastRingingTime < DEBOUNCE_MS) {
                        Log.d(TAG, "Debounced duplicate RINGING event")
                        return
                    }
                    lastRingingTime = now
                    
                    // Null-safety for phone number
                    if (phoneNumber.isNullOrEmpty()) {
                        Log.w(TAG, "Number is null/empty, using 'Numero Privato'")
                    }
                    
                    val sanitizedNumber = sanitizePhoneNumber(phoneNumber)
                    Log.d(TAG, "Incoming call from: $sanitizedNumber")
                    startCallerIdService(context, sanitizedNumber)
                }
                TelephonyManager.EXTRA_STATE_IDLE,
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d(TAG, "Call ended or answered")
                    isStartingService.set(false) // Reset atomic flag
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
            Log.d(TAG, "CallerIdService started")
            
            // Reset flag after short delay to allow future starts
            Handler(Looper.getMainLooper()).postDelayed({
                isStartingService.set(false)
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}")
            isStartingService.set(false) // Reset on error
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
