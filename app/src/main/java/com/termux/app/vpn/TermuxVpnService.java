package com.termux.app.vpn;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Captures device traffic and forwards matching packets to a SOCKS5 proxy in Termux.
 * <p>
 * Remote DNS on: only Fake-IP ({@code 240.0.0.0/4}) and mapdns reach the TUN. Remote DNS off:
 * full IPv4/IPv6 routing. Application inclusion is controlled independently by the configured
 * application routing mode.
 */
public class TermuxVpnService extends VpnService {

    private static final String LOG_TAG = "TermuxVpnService";
    private static final String CONFIG_FILE_NAME = "hev-socks5-tunnel.yml";

    private static volatile boolean sRunning = false;

    private ParcelFileDescriptor mTunFd;
    private TermuxVpnConfig mConfig;

    public static boolean isRunning() {
        return sRunning;
    }

    public static Intent newStartIntent(Context context, @Nullable TermuxVpnConfig config) {
        Intent intent = new Intent(context, TermuxVpnService.class).setAction(TERMUX_VPN_SERVICE.ACTION_START);
        if (config != null) config.putExtras(intent);
        return intent;
    }

    public static Intent newStopIntent(Context context) {
        return new Intent(context, TermuxVpnService.class).setAction(TERMUX_VPN_SERVICE.ACTION_STOP);
    }

    public static void startForegroundCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        setupNotificationChannel();
        startForeground(TermuxConstants.TERMUX_VPN_NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_vpn_starting)));

        String action = intent != null ? intent.getAction() : TERMUX_VPN_SERVICE.ACTION_START;
        if (TERMUX_VPN_SERVICE.ACTION_STOP.equals(action)) {
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (prepare(this) != null) {
            Logger.logError(LOG_TAG, "VPN permission has not been granted");
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(this);
        if (preferences == null) {
            Logger.logError(LOG_TAG, "Failed to load preferences");
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }

        mConfig = TermuxVpnConfig.from(preferences, intent);
        mConfig.saveTo(preferences);

        if (!startTunnel()) {
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }

        sRunning = true;
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        Logger.logWarn(LOG_TAG, "VPN revoked");
        stopTunnel();
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }

    private boolean startTunnel() {
        stopTunnelLocked();

        Builder builder = new Builder()
            .setSession(getString(R.string.application_name) + " VPN")
            .setMtu(TERMUX_VPN_SERVICE.TUN_MTU)
            .addAddress(TERMUX_VPN_SERVICE.TUN_IPV4_ADDRESS, TERMUX_VPN_SERVICE.TUN_IPV4_PREFIX_LENGTH);

        if (mConfig.remoteDnsEnabled) {
            configureRemoteDnsTunnel(builder);
        } else {
            configureFullTunnel(builder);
        }

        if (!configureApplicationRouting(builder)) {
            return false;
        }

        ParcelFileDescriptor tunFd;
        try {
            tunFd = builder.establish();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to establish VPN", e);
            return false;
        }
        if (tunFd == null) {
            Logger.logError(LOG_TAG, "VpnService.Builder.establish() returned null");
            return false;
        }

        File configFile = new File(getCacheDir(), CONFIG_FILE_NAME);
        File logFile = new File(getCacheDir(), "hev-socks5-tunnel.log");
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            out.write(mConfig.toHevConfigYaml(logFile.getAbsolutePath()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write tun2socks config", e);
            closeQuietly(tunFd);
            return false;
        }

        if (!TermuxTun2socks.TProxyStartService(configFile.getAbsolutePath(), tunFd.getFd())) {
            Logger.logError(LOG_TAG, "Failed to start hev-socks5-tunnel");
            closeQuietly(tunFd);
            return false;
        }

        mTunFd = tunFd;
        Logger.logInfo(LOG_TAG, "VPN started ("
            + (mConfig.remoteDnsEnabled ? "Fake-IP routes only" : "full tunnel")
            + "), forwarding to " + mConfig.describe());
        return true;
    }

    /**
     * Applies the application UID filter. Android does not allow allowed and disallowed
     * application lists to be combined, so each mode configures exactly one kind of filter.
     */
    private boolean configureApplicationRouting(Builder builder) {
        if (!mConfig.splitTunnelingEnabled) {
            // Split tunneling off is the safe default: all applications use the VPN except the
            // local Termux SOCKS5 process that provides the tunnel.
            return addDisallowedApplication(builder, getPackageName());
        }

        boolean includeSelected = TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(mConfig.appMode);
        int applied = 0;
        for (String packageName : mConfig.appPackages) {
            try {
                if (includeSelected) {
                    builder.addAllowedApplication(packageName);
                } else {
                    builder.addDisallowedApplication(packageName);
                }
                applied++;
            } catch (PackageManager.NameNotFoundException | IllegalArgumentException e) {
                Logger.logWarn(LOG_TAG, "Ignoring unavailable VPN application \"" + packageName + "\"");
            }
        }

        if (includeSelected && applied == 0) {
            Logger.logError(LOG_TAG, "No valid applications selected for include mode");
            return false;
        }
        return true;
    }

    private boolean addDisallowedApplication(Builder builder, String packageName) {
        try {
            builder.addDisallowedApplication(packageName);
            return true;
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException e) {
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Failed to exclude VPN application \"" + packageName + "\"", e);
            return false;
        }
    }

    /** Fake-IP + mapdns only; application inclusion is controlled by the routing mode. */
    private void configureRemoteDnsTunnel(Builder builder) {
        addRoute(builder, TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_NETWORK, TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_PREFIX_LENGTH);
        addRoute(builder, TERMUX_VPN_SERVICE.TUN_IPV4_ROUTE, TERMUX_VPN_SERVICE.TUN_IPV4_PREFIX_LENGTH);
        addDnsServer(builder, mConfig.mapdnsAddress, TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_ADDRESS);
        // Remote DNS only tunnels IPv4 Fake-IP traffic. Without this, Android blocks IPv6
        // traffic for applications included in the VPN when no IPv6 route is configured.
        builder.allowFamily(OsConstants.AF_INET6);
    }

    /** All IPv4/IPv6 through the TUN. */
    private void configureFullTunnel(Builder builder) {
        builder.addAddress(TERMUX_VPN_SERVICE.TUN_IPV6_ADDRESS, TERMUX_VPN_SERVICE.TUN_IPV6_PREFIX_LENGTH);
        addRoute(builder, "0.0.0.0", 0);
        addRoute(builder, "::", 0);
        addDnsServer(builder, mConfig.dnsIpv4, TERMUX_VPN_SERVICE.DEFAULT_DNS_IPV4);
        addDnsServer(builder, mConfig.dnsIpv6, TERMUX_VPN_SERVICE.DEFAULT_DNS_IPV6);
    }

    private static void addRoute(Builder builder, String address, int prefixLength) {
        try {
            builder.addRoute(address, prefixLength);
        } catch (IllegalArgumentException e) {
            Logger.logWarn(LOG_TAG, "Invalid route \"" + address + "/" + prefixLength + "\", skipping");
        }
    }

    /** {@link Builder#addDnsServer} rejects anything that is not an IP literal, so a bad user
     * value must not be allowed to take down the whole tunnel. */
    private static void addDnsServer(Builder builder, String address, String fallback) {
        try {
            builder.addDnsServer(address);
        } catch (IllegalArgumentException e) {
            Logger.logWarn(LOG_TAG, "Invalid DNS server \"" + address + "\", falling back to " + fallback);
            builder.addDnsServer(fallback);
        }
    }

    private void stopTunnel() {
        sRunning = false;
        stopTunnelLocked();
        stopForeground(true);
    }

    private void stopTunnelLocked() {
        if (TermuxTun2socks.TProxyIsRunning()) {
            TermuxTun2socks.TProxyStopService();
        }
        if (mTunFd != null) {
            closeQuietly(mTunFd);
            mTunFd = null;
        }
    }

    private static void closeQuietly(@Nullable ParcelFileDescriptor fd) {
        if (fd == null) return;
        try {
            fd.close();
        } catch (IOException ignored) {}
    }

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationUtils.setupNotificationChannel(this, TermuxConstants.TERMUX_VPN_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_VPN_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
    }

    private Notification buildNotification(String text) {
        Intent contentIntent = TermuxActivity.newInstance(this);
        PendingIntent contentPending = PendingIntent.getActivity(this, 0, contentIntent, 0);

        Notification.Builder builder = NotificationUtils.geNotificationBuilder(this,
            TermuxConstants.TERMUX_VPN_NOTIFICATION_CHANNEL_ID, Notification.PRIORITY_LOW,
            getString(R.string.notification_vpn_title), text, null,
            contentPending, null, NotificationUtils.NOTIFICATION_MODE_SILENT);
        if (builder == null) return new Notification();

        builder.setShowWhen(false);
        builder.setSmallIcon(R.drawable.ic_service_notification);
        builder.setColor(0xFF607D8B);
        builder.setOngoing(true);

        Intent stopIntent = newStopIntent(this);
        builder.addAction(android.R.drawable.ic_delete, getString(R.string.notification_action_vpn_stop),
            PendingIntent.getService(this, 1, stopIntent, 0));

        return builder.build();
    }

    private void updateNotification() {
        String text = getString(R.string.notification_vpn_running, mConfig.describe());
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(TermuxConstants.TERMUX_VPN_NOTIFICATION_ID, buildNotification(text));
        }
    }
}
