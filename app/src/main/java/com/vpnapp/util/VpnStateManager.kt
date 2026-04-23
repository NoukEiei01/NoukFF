package com.vpnapp.util

import com.vpnapp.model.VpnServer
import com.vpnapp.model.VpnStats
import com.vpnapp.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStateManager {

    private val _currentStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
    val currentStatus: StateFlow<VpnStatus> = _currentStatus.asStateFlow()

    private val _currentServer = MutableStateFlow<VpnServer?>(null)
    val currentServer: StateFlow<VpnServer?> = _currentServer.asStateFlow()

    private val _currentStats = MutableStateFlow(VpnStats())
    val currentStats: StateFlow<VpnStats> = _currentStats.asStateFlow()

    fun updateStatus(status: VpnStatus) {
        _currentStatus.value = status
    }

    fun updateServer(server: VpnServer?) {
        _currentServer.value = server
    }

    fun updateStats(stats: VpnStats) {
        _currentStats.value = stats
    }
}
