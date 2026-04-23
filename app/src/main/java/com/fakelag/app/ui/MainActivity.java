package com.fakelag.app.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.fakelag.app.R;
import com.fakelag.app.databinding.ActivityMainBinding;
import com.fakelag.app.model.LagConfig;
import com.fakelag.app.service.FakeLagVpnService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    private int delayMs  = 80;
    private int jitterMs = 20;
    private int dropPct  = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statsUpdater = new Runnable() {
        @Override
        public void run() {
            updateStats();
            handler.postDelayed(this, 500);
        }
    };

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                startVpn();
            } else {
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupSliders();
        setupPresetButtons();
        setupToggleButton();
        updateUiState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUiState();
        handler.post(statsUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statsUpdater);
    }

    // -------------------------------------------------------------------------
    private void setupSliders() {
        // Delay slider
        binding.seekDelay.setMax(LagConfig.MAX_DELAY);
        binding.seekDelay.setProgress(delayMs);
        binding.labelDelay.setText("Delay: " + delayMs + " ms");
        binding.seekDelay.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean b) {
                delayMs = v;
                binding.labelDelay.setText("Delay: " + v + " ms");
                liveUpdate();
            }
        });

        // Jitter slider
        binding.seekJitter.setMax(LagConfig.MAX_JITTER);
        binding.seekJitter.setProgress(jitterMs);
        binding.labelJitter.setText("Jitter: ±" + jitterMs + " ms");
        binding.seekJitter.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean b) {
                jitterMs = v;
                binding.labelJitter.setText("Jitter: ±" + v + " ms");
                liveUpdate();
            }
        });

        // Drop slider
        binding.seekDrop.setMax(LagConfig.MAX_DROP);
        binding.seekDrop.setProgress(dropPct);
        binding.labelDrop.setText("Drop: " + dropPct + "%");
        binding.seekDrop.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean b) {
                dropPct = v;
                binding.labelDrop.setText("Drop: " + v + "%");
                liveUpdate();
            }
        });
    }

    private void setupPresetButtons() {
        binding.btnPresetNone.setOnClickListener(v -> applyPreset(LagConfig.NONE()));
        binding.btnPresetLow.setOnClickListener(v  -> applyPreset(LagConfig.LOW()));
        binding.btnPresetMed.setOnClickListener(v  -> applyPreset(LagConfig.MEDIUM()));
        binding.btnPresetHigh.setOnClickListener(v -> applyPreset(LagConfig.HIGH()));
        binding.btnPresetRage.setOnClickListener(v -> applyPreset(LagConfig.RAGE()));
    }

    private void applyPreset(LagConfig c) {
        delayMs  = c.delayMs;
        jitterMs = c.jitterMs;
        dropPct  = c.dropPct;
        binding.seekDelay.setProgress(delayMs);
        binding.seekJitter.setProgress(jitterMs);
        binding.seekDrop.setProgress(dropPct);
        binding.labelDelay.setText("Delay: " + delayMs + " ms");
        binding.labelJitter.setText("Jitter: ±" + jitterMs + " ms");
        binding.labelDrop.setText("Drop: " + dropPct + "%");
        liveUpdate();
    }

    private void setupToggleButton() {
        binding.btnToggle.setOnClickListener(v -> {
            if (FakeLagVpnService.isRunning) {
                stopVpn();
            } else {
                requestVpnPermission();
            }
        });
    }

    // -------------------------------------------------------------------------
    private void requestVpnPermission() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            vpnPermissionLauncher.launch(intent);
        } else {
            startVpn();
        }
    }

    private void startVpn() {
        Intent i = new Intent(this, FakeLagVpnService.class);
        i.setAction(FakeLagVpnService.ACTION_START);
        i.putExtra(FakeLagVpnService.EXTRA_DELAY,  delayMs);
        i.putExtra(FakeLagVpnService.EXTRA_JITTER, jitterMs);
        i.putExtra(FakeLagVpnService.EXTRA_DROP,   dropPct);
        startService(i);
        handler.postDelayed(this::updateUiState, 300);
    }

    private void stopVpn() {
        Intent i = new Intent(this, FakeLagVpnService.class);
        i.setAction(FakeLagVpnService.ACTION_STOP);
        startService(i);
        handler.postDelayed(this::updateUiState, 300);
    }

    /** Push updated config to running service without restart */
    private void liveUpdate() {
        if (!FakeLagVpnService.isRunning) return;
        Intent i = new Intent(this, FakeLagVpnService.class);
        i.setAction(FakeLagVpnService.ACTION_UPDATE_CONFIG);
        i.putExtra(FakeLagVpnService.EXTRA_DELAY,  delayMs);
        i.putExtra(FakeLagVpnService.EXTRA_JITTER, jitterMs);
        i.putExtra(FakeLagVpnService.EXTRA_DROP,   dropPct);
        startService(i);
    }

    private void updateUiState() {
        boolean running = FakeLagVpnService.isRunning;
        binding.btnToggle.setText(running ? "⏹  STOP LAG" : "▶  START LAG");
        binding.statusDot.setBackgroundResource(
                running ? R.drawable.dot_green : R.drawable.dot_red);
        binding.statusText.setText(running ? "ACTIVE" : "INACTIVE");
    }

    private void updateStats() {
        binding.statIn.setText(String.valueOf(FakeLagVpnService.packetsIn));
        binding.statOut.setText(String.valueOf(FakeLagVpnService.packetsOut));
        binding.statDrop.setText(String.valueOf(FakeLagVpnService.packetsDropped));
    }

    // -------------------------------------------------------------------------
    /** Convenience no-op SeekBar listener */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar sb) {}
        @Override public void onStopTrackingTouch(SeekBar sb) {}
    }
}
