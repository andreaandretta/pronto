package com.example.pronto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (state == TelephonyManager.EXTRA_STATE_RINGING && number != null) {
                val serviceIntent = Intent(context, CallerIdService::class.java).apply {
                    putExtra("caller_number", number)
                }
                context.startForegroundService(serviceIntent)
            } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                context.stopService(Intent(context, CallerIdService::class.java))
            }
        }
    }
}
