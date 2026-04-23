package com.vpnapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vpnapp.R
import com.vpnapp.databinding.ActivityServerListBinding
import com.vpnapp.model.VpnServer

class ServerListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerListBinding

    private val servers = listOf(
        VpnServer("sg1", "Singapore #1", "Singapore", "SG", "sg1.vpnapp.io", 1194, "UDP", 12, 35),
        VpnServer("sg2", "Singapore #2", "Singapore", "SG", "sg2.vpnapp.io", 1194, "UDP", 15, 22),
        VpnServer("us1", "USA East", "United States", "US", "us-east.vpnapp.io", 1194, "UDP", 85, 52),
        VpnServer("us2", "USA West", "United States", "US", "us-west.vpnapp.io", 1194, "UDP", 95, 40),
        VpnServer("jp1", "Japan #1", "Japan", "JP", "jp1.vpnapp.io", 443, "TCP", 28, 20),
        VpnServer("de1", "Germany", "Germany", "DE", "de.vpnapp.io", 1194, "UDP", 145, 67),
        VpnServer("uk1", "United Kingdom", "United Kingdom", "GB", "uk.vpnapp.io", 1194, "UDP", 160, 48),
        VpnServer("nl1", "Netherlands", "Netherlands", "NL", "nl.vpnapp.io", 1194, "UDP", 138, 30),
        VpnServer("ca1", "Canada", "Canada", "CA", "ca.vpnapp.io", 1194, "UDP", 110, 45),
        VpnServer("au1", "Australia", "Australia", "AU", "au.vpnapp.io", 1194, "UDP", 180, 38)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Select Server"

        binding.rvServers.apply {
            layoutManager = LinearLayoutManager(this@ServerListActivity)
            adapter = ServerAdapter(servers) { server ->
                // Set selected server and return
                setResult(RESULT_OK, android.content.Intent().apply {
                    putExtra("selected_server_id", server.id)
                })
                finish()
            }
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class ServerAdapter(
        private val servers: List<VpnServer>,
        private val onSelect: (VpnServer) -> Unit
    ) : RecyclerView.Adapter<ServerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvFlag: TextView = view.findViewById(R.id.tvFlag)
            val tvName: TextView = view.findViewById(R.id.tvServerName)
            val tvCountry: TextView = view.findViewById(R.id.tvCountry)
            val tvPing: TextView = view.findViewById(R.id.tvPing)
            val tvProtocol: TextView = view.findViewById(R.id.tvProtocol)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_server, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val server = servers[position]
            holder.tvFlag.text = server.flagEmoji
            holder.tvName.text = server.name
            holder.tvCountry.text = server.country
            holder.tvPing.text = if (server.ping > 0) "${server.ping}ms" else "--"
            holder.tvProtocol.text = server.protocol
            holder.itemView.setOnClickListener { onSelect(server) }
        }

        override fun getItemCount() = servers.size
    }
}
