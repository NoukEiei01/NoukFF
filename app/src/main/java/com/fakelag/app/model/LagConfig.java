package com.fakelag.app.model;

/**
 * Configuration for the fake-lag VPN tunnel.
 * All delay values are in milliseconds.
 */
public class LagConfig {

    public static final int MIN_DELAY   = 0;
    public static final int MAX_DELAY   = 2000;
    public static final int MIN_JITTER  = 0;
    public static final int MAX_JITTER  = 500;
    public static final int MIN_DROP    = 0;    // percent
    public static final int MAX_DROP    = 90;   // percent

    // --- presets ---
    public static LagConfig NONE()   { return new LagConfig(0,   0,  0,  "None");       }
    public static LagConfig LOW()    { return new LagConfig(20,  10, 0,  "Low (20ms)");  }
    public static LagConfig MEDIUM() { return new LagConfig(80,  30, 2,  "Medium (80ms)"); }
    public static LagConfig HIGH()   { return new LagConfig(200, 80, 5,  "High (200ms)"); }
    public static LagConfig RAGE()   { return new LagConfig(500, 150,15, "Rage (500ms)"); }

    public final int delayMs;    // base latency added to every packet
    public final int jitterMs;   // ±random extra delay
    public final int dropPct;    // packet drop percent (0-90)
    public final String label;

    public LagConfig(int delayMs, int jitterMs, int dropPct, String label) {
        this.delayMs  = Math.max(MIN_DELAY,  Math.min(MAX_DELAY,  delayMs));
        this.jitterMs = Math.max(MIN_JITTER, Math.min(MAX_JITTER, jitterMs));
        this.dropPct  = Math.max(MIN_DROP,   Math.min(MAX_DROP,   dropPct));
        this.label    = label != null ? label : "Custom";
    }

    /** Compute an actual delay for one packet: base + random jitter */
    public long sampleDelayMs() {
        if (delayMs == 0 && jitterMs == 0) return 0;
        int jitter = jitterMs > 0 ? (int)(Math.random() * jitterMs * 2) - jitterMs : 0;
        return Math.max(0, delayMs + jitter);
    }

    /** Returns true if this packet should be dropped */
    public boolean shouldDrop() {
        return dropPct > 0 && Math.random() * 100 < dropPct;
    }

    @Override
    public String toString() {
        return label + " [delay=" + delayMs + "ms jitter=±" + jitterMs + "ms drop=" + dropPct + "%]";
    }
}
