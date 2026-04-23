package com.fakelag.app.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile to toggle FakeLag on/off instantly from the notification shade.
 */
public class FakeLagTileService extends TileService {

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        if (FakeLagVpnService.isRunning) {
            Intent stop = new Intent(this, FakeLagVpnService.class);
            stop.setAction(FakeLagVpnService.ACTION_STOP);
            startService(stop);
        } else {
            Intent launch = new Intent(this, com.fakelag.app.ui.MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // startActivityAndCollapse(Intent) deprecated in Android 14 (API 34)
            // Must use PendingIntent overload instead
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent pi = PendingIntent.getActivity(
                        this, 0, launch,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                startActivityAndCollapse(pi);
            } else {
                startActivityAndCollapse(launch);
            }
        }
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (FakeLagVpnService.isRunning) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("Lag ON");
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("Lag OFF");
        }
        tile.updateTile();
    }
}
