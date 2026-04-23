package com.vpnapp.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vpnapp.R
import com.vpnapp.databinding.ActivityMainBinding
import com.vpnapp.model.VpnServer
import com.vpnapp.model.VpnStats
import com.vpnapp.model.VpnStatus
import com.vpnapp.service.FloatingWindowService
import com.vpnapp.service.MyVpnService
import com.vpnapp.util.VpnStateManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vpnService: MyVpnService? = null
    private var pendingServer: VpnServer? = null

    // Sample servers - replace with real API
    private val servers = listOf(
        VpnServer("sg1", "Singapore #1", "Singapore", "SG", "sg1.vpnapp.io", 1194, "UDP", 12, 35),
        VpnServer("us1", "USA East", "United States", "US", "us-east.vpnapp.io", 1194, "UDP", 85, 52),
        VpnServer("jp1", "Japan #1", "Japan", "JP", "jp1.vpnapp.io", 443, "TCP", 28, 20),
        VpnServer("de1", "Germany", "Germany", "DE", "de.vpnapp.io", 1194, "UDP", 145, 67),
        VpnServer("uk1", "United Kingdom", "United Kingdom", "GB", "uk.vpnapp.io", 1194, "UDP", 160, 48),
        VpnServer("nl1", "Netherlands", "Netherlands", "NL", "nl.vpnapp.io", 1194, "UDP", 138, 30)
    )
    private var selectedServer = servers[0]

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingServer?.let { startVpn(it) }
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
            binding.btnConnect.text = "Connect"
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startFloatingService()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            vpnService = (binder as MyVpnService.VpnBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            vpnService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupUI()
        observeVpnState()
    }

    private fun setupUI() {
        // Server selection
        updateServerCard(selectedServer)
        binding.cardServer.setOnClickListener {
            startActivity(Intent(this, ServerListActivity::class.java))
        }

        // Connect button
        binding.btnConnect.setOnClickListener {
            when (VpnStateManager.currentStatus.value) {
                VpnStatus.DISCONNECTED, VpnStatus.ERROR -> requestVpnConnect(selectedServer)
                VpnStatus.CONNECTED -> requestDisconnect()
                VpnStatus.CONNECTING -> requestDisconnect()
                else -> {}
            }
        }

        // Floating button toggle
        binding.fabFloat.setOnClickListener {
            toggleFloatingWindow()
        }
    }

    private fun observeVpnState() {
        lifecycleScope.launch {
            VpnStateManager.currentStatus.collect { status ->
                updateStatusUI(status)
            }
        }
        lifecycleScope.launch {
            VpnStateManager.currentStats.collect { stats ->
                updateStatsUI(stats)
            }
        }
        lifecycleScope.launch {
            VpnStateManager.currentServer.collect { server ->
                server?.let { updateServerCard(it) }
            }
        }
    }

    private fun updateStatusUI(status: VpnStatus) {
        when (status) {
            VpnStatus.DISCONNECTED -> {
                binding.btnConnect.text = "Connect"
                binding.btnConnect.isEnabled = true
                binding.tvStatus.text = "Not Protected"
                binding.tvStatus.setTextColor(getColor(R.color.error_red))
                binding.statusIndicator.setBackgroundResource(R.drawable.dot_disconnected)
                binding.statsCard.visibility = View.GONE
                binding.connectionAnimation.pauseAnimation()
            }
            VpnStatus.CONNECTING -> {
                binding.btnConnect.text = "Connecting..."
                binding.btnConnect.isEnabled = true
                binding.tvStatus.text = "Connecting..."
                binding.tvStatus.setTextColor(getColor(R.color.connecting_yellow))
                binding.statusIndicator.setBackgroundResource(R.drawable.dot_connecting)
                binding.connectionAnimation.playAnimation()
            }
            VpnStatus.CONNECTED -> {
                binding.btnConnect.text = "Disconnect"
                binding.btnConnect.isEnabled = true
                binding.tvStatus.text = "Protected"
                binding.tvStatus.setTextColor(getColor(R.color.connected_green))
                binding.statusIndicator.setBackgroundResource(R.drawable.dot_connected)
                binding.statsCard.visibility = View.VISIBLE
                binding.connectionAnimation.pauseAnimation()
            }
            VpnStatus.DISCONNECTING -> {
                binding.btnConnect.text = "Disconnecting..."
                binding.btnConnect.isEnabled = false
            }
            VpnStatus.ERROR -> {
                binding.btnConnect.text = "Retry"
                binding.btnConnect.isEnabled = true
                binding.tvStatus.text = "Connection Failed"
                binding.tvStatus.setTextColor(getColor(R.color.error_red))
                Toast.makeText(this, "VPN connection failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatsUI(stats: VpnStats) {
        binding.tvDuration.text = stats.formattedDuration
        binding.tvUpload.text = stats.formattedBytesOut
        binding.tvDownload.text = stats.formattedBytesIn
        binding.tvServerIp.text = stats.serverIp
    }

    private fun updateServerCard(server: VpnServer) {
        binding.tvServerName.text = server.name
        binding.tvServerCountry.text = "${server.flagEmoji} ${server.country}"
        binding.tvServerPing.text = if (server.ping > 0) "${server.ping}ms" else "--"
        binding.tvProtocol.text = "${server.protocol}:${server.port}"
        binding.serverLoadBar.progress = server.load
    }

    private fun requestVpnConnect(server: VpnServer) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingServer = server
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn(server)
        }
    }

    private fun startVpn(server: VpnServer) {
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_CONNECT
            putExtra(MyVpnService.EXTRA_SERVER, server)
        }
        startForegroundService(intent)

        Intent(this, MyVpnService::class.java).also { svcIntent ->
            bindService(svcIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun requestDisconnect() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disconnect VPN?")
            .setMessage("Your connection will no longer be protected.")
            .setPositiveButton("Disconnect") { _, _ ->
                val intent = Intent(this, MyVpnService::class.java).apply {
                    action = MyVpnService.ACTION_DISCONNECT
                }
                startService(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        startService(Intent(this, FloatingWindowService::class.java))
        Toast.makeText(this, "Floating widget enabled", Toast.LENGTH_SHORT).show()
        minimize()
    }

    private fun minimize() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        try { unbindService(serviceConnection) } catch (e: Exception) {}
        super.onDestroy()
    }
}
