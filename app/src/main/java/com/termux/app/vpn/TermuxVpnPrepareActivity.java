package com.termux.app.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.theme.NightMode;
import com.termux.shared.activity.media.AppCompatActivityUtils;

/**
 * Requests the system VPN consent dialog, then starts {@link TermuxVpnService}.
 * Can be launched from Settings or from Termux via {@code am start}.
 */
public class TermuxVpnPrepareActivity extends AppCompatActivity {

    private static final String LOG_TAG = "TermuxVpnPrepareActivity";
    private static final int REQUEST_VPN_PREPARE = 1;

    private TermuxVpnConfig mConfig;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        if (TERMUX_VPN_SERVICE.ACTION_STOP.equals(getIntent().getAction())) {
            TermuxVpnService.startForegroundCompat(this, TermuxVpnService.newStopIntent(this));
            finish();
            return;
        }

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(this, true);
        if (preferences == null) {
            finish();
            return;
        }

        mConfig = TermuxVpnConfig.from(preferences, getIntent());
        mConfig.saveTo(preferences);

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, REQUEST_VPN_PREPARE);
        } else {
            startVpnAndFinish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VPN_PREPARE) return;
        if (resultCode == Activity.RESULT_OK) {
            startVpnAndFinish();
        } else {
            Logger.logWarn(LOG_TAG, "VPN permission denied");
            finish();
        }
    }

    private void startVpnAndFinish() {
        TermuxVpnService.startForegroundCompat(this, TermuxVpnService.newStartIntent(this, mConfig));
        finish();
    }
}
