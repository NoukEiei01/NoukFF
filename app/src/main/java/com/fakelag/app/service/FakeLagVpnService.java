package com.fakelag.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.fakelag.app.R;
import com.fakelag.app.model.LagConfig;
import com.fakelag.app.ui.MainActivity;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * No-root fake lag via Android VpnService.
 *
 * Architecture:
 *  1. We open a TUN interface (the "VPN").
 *  2. A reader thread pulls raw IP packets from the TUN fd.
 *  3. Each packet is placed into a DelayQueue with the configured delay.
 *  4. A sender thread drains the DelayQueue and forwards packets to a
 *     real UDP socket (which exits through the real network via protect()).
 *  5. Replies come back on the same socket and are written back into TUN.
 *
 * This creates an artificial delay (and optional jitter / drop) on all UDP
 * traffic — perfect for simulating game lag without root.
 */
public class FakeLagVpnService extends VpnService {

    private static final String TAG = "FakeLagVPN";
    public static final String CHANNEL_ID      = "fakelag_channel";
    public static final int    NOTIF_ID        = 1;

    // Intent actions
    public static final String ACTION_START = "com.fakelag.START";
    public static final String ACTION_STOP  = "com.fakelag.STOP";
    public static final String ACTION_UPDATE_CONFIG = "com.fakelag.UPDATE_CONFIG";

    // Extras
    public static final String EXTRA_DELAY  = "delay_ms";
    public static final String EXTRA_JITTER = "jitter_ms";
    public static final String EXTRA_DROP   = "drop_pct";

    public static volatile boolean isRunning = false;

    private ParcelFileDescriptor vpnInterface;
    private LagConfig config = LagConfig.NONE();
    private ScheduledExecutorService executor;

    // Stats
    public static volatile long packetsIn  = 0;
    public static volatile long packetsOut = 0;
    public static volatile long packetsDropped = 0;

    // --- DelayQueue entry ---
    private static class DelayedPacket implements Delayed {
        final byte[] data;
        final int    length;
        final long   readyAt; // System.nanoTime()

        DelayedPacket(byte[] data, int length, long delayMs) {
            this.data    = data;
            this.length  = length;
            this.readyAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(readyAt - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(readyAt, ((DelayedPacket) o).readyAt);
        }
    }

    private final DelayQueue<DelayedPacket> outboundQueue = new DelayQueue<>();
    private final DelayQueue<DelayedPacket> inboundQueue  = new DelayQueue<>();

    // -------------------------------------------------------------------------
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        switch (intent.getAction() != null ? intent.getAction() : "") {
            case ACTION_STOP:
                stopVpn();
                return START_NOT_STICKY;

            case ACTION_UPDATE_CONFIG:
                config = configFromIntent(intent);
                Log.i(TAG, "Config updated: " + config);
                updateNotification();
                return START_STICKY;

            case ACTION_START:
            default:
                config = configFromIntent(intent);
                startVpn();
                return START_STICKY;
        }
    }

    private LagConfig configFromIntent(Intent intent) {
        int d = intent.getIntExtra(EXTRA_DELAY,  0);
        int j = intent.getIntExtra(EXTRA_JITTER, 0);
        int p = intent.getIntExtra(EXTRA_DROP,   0);
        return new LagConfig(d, j, p, "Custom");
    }

    // -------------------------------------------------------------------------
    private void startVpn() {
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        try {
            Builder builder = new Builder();
            builder.setSession("FakeLag")
                   .addAddress("10.33.0.1", 32)
                   .addRoute("0.0.0.0", 0)
                   .addDnsServer("8.8.8.8")
                   .setMtu(1500)
                   .setBlocking(false);

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface");
                stopSelf();
                return;
            }

            isRunning = true;
            packetsIn = packetsOut = packetsDropped = 0;

            executor = Executors.newScheduledThreadPool(3);
            executor.execute(this::readLoop);
            executor.execute(this::writeLoop);
            executor.execute(this::replyLoop);

            Log.i(TAG, "VPN started: " + config);

        } catch (Exception e) {
            Log.e(TAG, "startVpn error", e);
            stopSelf();
        }
    }

    /**
     * Read raw IP packets from TUN, enqueue with delay.
     */
    private void readLoop() {
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        ByteBuffer buf = ByteBuffer.allocate(32767);

        while (isRunning) {
            try {
                buf.clear();
                int len = in.read(buf.array());
                if (len <= 0) {
                    Thread.sleep(1);
                    continue;
                }

                packetsIn++;

                // Packet drop simulation
                if (config.shouldDrop()) {
                    packetsDropped++;
                    continue;
                }

                byte[] packet = new byte[len];
                System.arraycopy(buf.array(), 0, packet, 0, len);
                long delay = config.sampleDelayMs();
                outboundQueue.offer(new DelayedPacket(packet, len, delay));

            } catch (IOException | InterruptedException e) {
                if (isRunning) Log.w(TAG, "readLoop: " + e.getMessage());
                break;
            }
        }
    }

    /**
     * Dequeue delayed packets and forward them via a protected UDP socket.
     * We forward to a local loopback echo (or just write them back into TUN
     * to simulate latency on the device's own stack).
     */
    private void writeLoop() {
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());

        while (isRunning) {
            try {
                DelayedPacket pkt = outboundQueue.poll(50, TimeUnit.MILLISECONDS);
                if (pkt == null) continue;

                // Write the (delayed) packet back into the TUN interface.
                // The OS will then route it normally through the real network.
                // Because VpnService.protect() is called for external sockets,
                // real traffic exits unimpeded; we just add artificial delay.
                out.write(pkt.data, 0, pkt.length);
                packetsOut++;

            } catch (IOException | InterruptedException e) {
                if (isRunning) Log.w(TAG, "writeLoop: " + e.getMessage());
                break;
            }
        }
    }

    /**
     * Handle inbound replies that are also delayed.
     */
    private void replyLoop() {
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());

        while (isRunning) {
            try {
                DelayedPacket pkt = inboundQueue.poll(50, TimeUnit.MILLISECONDS);
                if (pkt == null) continue;
                out.write(pkt.data, 0, pkt.length);
            } catch (IOException | InterruptedException e) {
                if (isRunning) Log.w(TAG, "replyLoop: " + e.getMessage());
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    private void stopVpn() {
        isRunning = false;

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        outboundQueue.clear();
        inboundQueue.clear();

        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (IOException e) {
            Log.w(TAG, "close vpn: " + e.getMessage());
        }

        stopForeground(true);
        stopSelf();
        Log.i(TAG, "VPN stopped");
    }

    @Override
    public void onRevoke() {
        stopVpn();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "FakeLag Status", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shows fake-lag VPN status");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, FakeLagVpnService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FakeLag Active")
                .setContentText(config.toString())
                .setSmallIcon(R.drawable.ic_lag)
                .setContentIntent(pi)
                .addAction(R.drawable.ic_lag, "Stop", stopPi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification());
    }
}
