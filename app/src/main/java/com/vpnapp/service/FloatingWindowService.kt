package com.vpnapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vpnapp.R
import com.vpnapp.model.VpnStatus
import com.vpnapp.ui.MainActivity
import com.vpnapp.util.VpnStateManager
import kotlinx.coroutines.*
import kotlin.math.abs

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingView()
        observeVpnState()
    }

    private fun setupFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatView = inflater.inflate(R.layout.layout_floating_window, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        floatView.setOnTouchListener(object : View.OnTouchListener {
            private var startTime = 0L
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startTime = System.currentTimeMillis()
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val elapsed = System.currentTimeMillis() - startTime
                        val dx = abs(event.rawX - initialTouchX)
                        val dy = abs(event.rawY - initialTouchY)
                        if (elapsed < 200 && dx < 10 && dy < 10) {
                            onFloatingButtonClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatView, params)
    }

    private fun onFloatingButtonClick() {
        val status = VpnStateManager.currentStatus.value
        if (status == VpnStatus.CONNECTED) {
            // Quick disconnect
            val intent = Intent(this, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_DISCONNECT
            }
            startService(intent)
        } else {
            // Open main app
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    private fun observeVpnState() {
        scope.launch {
            VpnStateManager.currentStatus.collect { status ->
                updateFloatingUI(status)
            }
        }
        scope.launch {
            VpnStateManager.currentStats.collect { stats ->
                floatView.findViewById<TextView>(R.id.tvFloatStats)?.text =
                    if (VpnStateManager.currentStatus.value == VpnStatus.CONNECTED) {
                        "↑${stats.formattedBytesOut} ↓${stats.formattedBytesIn}"
                    } else ""
            }
        }
    }

    private fun updateFloatingUI(status: VpnStatus) {
        val btn = floatView.findViewById<ImageView>(R.id.ivFloatIcon)
        val dot = floatView.findViewById<View>(R.id.statusDot)
        val label = floatView.findViewById<TextView>(R.id.tvFloatStatus)

        when (status) {
            VpnStatus.CONNECTED -> {
                btn?.setColorFilter(ContextCompat.getColor(this, R.color.connected_green))
                dot?.setBackgroundResource(R.drawable.dot_connected)
                label?.text = "Connected"
            }
            VpnStatus.CONNECTING -> {
                btn?.setColorFilter(ContextCompat.getColor(this, R.color.connecting_yellow))
                dot?.setBackgroundResource(R.drawable.dot_connecting)
                label?.text = "Connecting..."
            }
            VpnStatus.ERROR -> {
                btn?.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
                dot?.setBackgroundResource(R.drawable.dot_error)
                label?.text = "Error"
            }
            else -> {
                btn?.setColorFilter(ContextCompat.getColor(this, R.color.disconnected_gray))
                dot?.setBackgroundResource(R.drawable.dot_disconnected)
                label?.text = "Tap to connect"
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (::floatView.isInitialized) {
            windowManager.removeView(floatView)
        }
        super.onDestroy()
    }
}
