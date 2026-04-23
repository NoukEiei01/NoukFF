package com.vpnapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val autoConnect = prefs.getBoolean("auto_connect_boot", false)
            if (autoConnect) {
                Log.d("BootReceiver", "Auto-connecting VPN on boot")
                // Could start VPN service here if auto-connect is enabled
            }
        }
    }
}
