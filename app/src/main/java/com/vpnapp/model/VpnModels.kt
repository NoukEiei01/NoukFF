package com.vpnapp.model

data class VpnServer(
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String,
    val host: String,
    val port: Int = 1194,
    val protocol: String = "UDP",
    val ping: Int = -1,
    val load: Int = 0,
    val isFavorite: Boolean = false,
    val isPremium: Boolean = false
) {
    val flagEmoji: String
        get() = countryCode
            .uppercase()
            .map { char -> '\uD83C' + (char.code - 'A'.code + '\uDDE6'.code).toChar() }
            .joinToString("")
}

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class VpnStats(
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val duration: Long = 0,
    val serverIp: String = "",
    val localIp: String = ""
) {
    val formattedBytesIn: String get() = formatBytes(bytesIn)
    val formattedBytesOut: String get() = formatBytes(bytesOut)
    val formattedDuration: String get() {
        val h = duration / 3600
        val m = (duration % 3600) / 60
        val s = duration % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
