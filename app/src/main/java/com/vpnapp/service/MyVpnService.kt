package com.vpnapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vpnapp.R
import com.vpnapp.model.VpnServer
import com.vpnapp.model.VpnStats
import com.vpnapp.model.VpnStatus
import com.vpnapp.ui.MainActivity
import com.vpnapp.util.VpnStateManager
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.Socket

class MyVpnService : VpnService() {

    companion object {
        const val CHANNEL_ID = "vpn_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CONNECT = "ACTION_CONNECT"
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
        const val EXTRA_SERVER = "EXTRA_SERVER"
        private const val TAG = "MyVpnService"
    }

    private val binder = VpnBinder()
    private var vpnJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var startTime: Long = 0
    private var statsJob: Job? = null

    inner class VpnBinder : Binder() {
        fun getService(): MyVpnService = this@MyVpnService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val server = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_SERVER, VpnServer::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_SERVER) as? VpnServer
                }
                server?.let { connect(it) }
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    fun connect(server: VpnServer) {
        VpnStateManager.updateStatus(VpnStatus.CONNECTING)
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to ${server.name}..."))

        vpnJob?.cancel()
        vpnJob = scope.launch {
            try {
                Log.d(TAG, "Connecting to ${server.host}:${server.port}")

                // Build VPN tunnel interface
                val builder = Builder()
                    .setSession("VPN App - ${server.name}")
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)  // Route all traffic
                    .setMtu(1500)

                // Exclude our own app from VPN to prevent loop
                builder.addDisallowedApplication(packageName)

                val vpnInterface = builder.establish()
                    ?: throw Exception("Failed to establish VPN interface")

                startTime = System.currentTimeMillis()
                VpnStateManager.updateStatus(VpnStatus.CONNECTED)
                VpnStateManager.updateServer(server)
                startStatsUpdater(server)

                updateNotification("Connected to ${server.name}")
                Log.d(TAG, "VPN connected successfully")

                // Keep VPN alive - in real implementation, handle actual tunnel traffic here
                // This is a local TUN interface setup; integrate with WireGuard/OpenVPN library for real tunnel
                while (isActive && vpnInterface.fileDescriptor.valid()) {
                    delay(1000)
                }

                vpnInterface.close()

            } catch (e: CancellationException) {
                Log.d(TAG, "VPN connection cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "VPN connection error: ${e.message}", e)
                VpnStateManager.updateStatus(VpnStatus.ERROR)
                updateNotification("Connection failed")
            } finally {
                if (VpnStateManager.currentStatus.value != VpnStatus.DISCONNECTING) {
                    VpnStateManager.updateStatus(VpnStatus.DISCONNECTED)
                }
            }
        }
    }

    fun disconnect() {
        VpnStateManager.updateStatus(VpnStatus.DISCONNECTING)
        statsJob?.cancel()
        vpnJob?.cancel()
        VpnStateManager.updateStatus(VpnStatus.DISCONNECTED)
        VpnStateManager.updateStats(VpnStats())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "VPN disconnected")
    }

    private fun startStatsUpdater(server: VpnServer) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var bytesIn = 0L
            var bytesOut = 0L
            while (isActive) {
                delay(1000)
                // Simulate traffic stats - replace with real interface stats in production
                bytesIn += (1024..102400).random()
                bytesOut += (512..51200).random()
                val duration = (System.currentTimeMillis() - startTime) / 1000
                VpnStateManager.updateStats(
                    VpnStats(
                        bytesIn = bytesIn,
                        bytesOut = bytesOut,
                        duration = duration,
                        serverIp = server.host,
                        localIp = "10.0.0.2"
                    )
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN connection status notifications"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            this, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN App")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_vpn_key, "Disconnect", disconnectPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
