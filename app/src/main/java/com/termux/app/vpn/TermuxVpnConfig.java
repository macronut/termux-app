package com.termux.app.vpn;

import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runtime VPN parameters. TCP/UDP go to SOCKS5. When remote DNS is enabled, hev {@code mapdns}
 * Fake-IP is used so the proxy receives domain names; otherwise DNS UDP/53 uses SOCKS5 UDP ASSOCIATE.
 */
final class TermuxVpnConfig {

    /** Hostname/IP literal characters. Anything else could break out of the generated YAML line. */
    private static final Pattern HOST_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,255}");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("[A-Za-z0-9_.]+");

    public final String socksHost;
    public final int socksPort;
    public final boolean splitTunnelingEnabled;
    public final String appMode;
    public final Set<String> appPackages;
    public final boolean remoteDnsEnabled;
    public final String mapdnsAddress;
    public final String dnsIpv4;
    public final String dnsIpv6;

    TermuxVpnConfig(@NonNull String socksHost, int socksPort, boolean splitTunnelingEnabled,
                    @NonNull String appMode, @NonNull Set<String> appPackages, boolean remoteDnsEnabled,
                    @NonNull String mapdnsAddress, @NonNull String dnsIpv4, @NonNull String dnsIpv6) {
        this.socksHost = socksHost;
        this.socksPort = socksPort;
        this.splitTunnelingEnabled = splitTunnelingEnabled;
        this.appMode = appMode;
        this.appPackages = Collections.unmodifiableSet(new HashSet<>(appPackages));
        this.remoteDnsEnabled = remoteDnsEnabled;
        this.mapdnsAddress = mapdnsAddress;
        this.dnsIpv4 = dnsIpv4;
        this.dnsIpv6 = dnsIpv6;
    }

    @NonNull
    static TermuxVpnConfig from(@NonNull TermuxAppSharedPreferences preferences, @Nullable Intent intent) {
        String socksHost = preferences.getVpnSocksHost();
        int socksPort = preferences.getVpnSocksPort();
        boolean splitTunnelingEnabled = preferences.getVpnSplitTunnelingEnabled();
        String appMode = preferences.getVpnAppMode();
        Set<String> appPackages = preferences.getVpnAppPackages();
        boolean remoteDnsEnabled = preferences.getVpnRemoteDnsEnabled();
        String mapdnsAddress = TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_ADDRESS;
        String dnsIpv4 = preferences.getVpnDnsIpv4();
        String dnsIpv6 = preferences.getVpnDnsIpv6();

        if (intent != null) {
            boolean forceExcludeTermux = false;
            String extraHost = intent.getStringExtra(TERMUX_VPN_SERVICE.EXTRA_SOCKS_HOST);
            if (!TextUtils.isEmpty(extraHost)) socksHost = extraHost.trim();

            int extraPort = intent.getIntExtra(TERMUX_VPN_SERVICE.EXTRA_SOCKS_PORT, -1);
            if (extraPort > 0 && extraPort <= 65535) socksPort = extraPort;

            boolean hasSplitTunnelingExtra = intent.hasExtra(TERMUX_VPN_SERVICE.EXTRA_SPLIT_TUNNELING);
            if (hasSplitTunnelingExtra) {
                splitTunnelingEnabled = intent.getBooleanExtra(
                    TERMUX_VPN_SERVICE.EXTRA_SPLIT_TUNNELING,
                    TERMUX_VPN_SERVICE.DEFAULT_SPLIT_TUNNELING_ENABLED);
            }

            String extraAppMode = intent.getStringExtra(TERMUX_VPN_SERVICE.EXTRA_APP_MODE);
            if (!TextUtils.isEmpty(extraAppMode)) {
                extraAppMode = extraAppMode.trim();
                if (TERMUX_VPN_SERVICE.APP_MODE_GLOBAL.equals(extraAppMode)) {
                    splitTunnelingEnabled = false;
                    appMode = TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE;
                } else {
                    if (TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_TERMUX.equals(extraAppMode)) {
                        forceExcludeTermux = true;
                    }
                    appMode = normalizeAppMode(extraAppMode);
                    if (!hasSplitTunnelingExtra) splitTunnelingEnabled = true;
                }
            } else if (intent.hasExtra(TERMUX_VPN_SERVICE.EXTRA_GLOBAL)) {
                // Keep accepting the old command-line/API extra.
                boolean global = intent.getBooleanExtra(TERMUX_VPN_SERVICE.EXTRA_GLOBAL,
                    TERMUX_VPN_SERVICE.DEFAULT_GLOBAL);
                splitTunnelingEnabled = !global;
                if (!global) {
                    appMode = TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE;
                    forceExcludeTermux = true;
                }
            }

            ArrayList<String> extraPackages = intent.getStringArrayListExtra(
                TERMUX_VPN_SERVICE.EXTRA_APP_PACKAGES);
            if (extraPackages != null) {
                appPackages = new HashSet<>(extraPackages);
            }
            if (forceExcludeTermux) {
                appPackages = withTermux(appPackages);
            }

            if (intent.hasExtra(TERMUX_VPN_SERVICE.EXTRA_REMOTE_DNS_ENABLED)) {
                remoteDnsEnabled = intent.getBooleanExtra(TERMUX_VPN_SERVICE.EXTRA_REMOTE_DNS_ENABLED,
                    TERMUX_VPN_SERVICE.DEFAULT_REMOTE_DNS_ENABLED);
            }

            String extraDns4 = intent.getStringExtra(TERMUX_VPN_SERVICE.EXTRA_DNS_IPV4);
            if (!TextUtils.isEmpty(extraDns4)) dnsIpv4 = extraDns4.trim();

            String extraDns6 = intent.getStringExtra(TERMUX_VPN_SERVICE.EXTRA_DNS_IPV6);
            if (!TextUtils.isEmpty(extraDns6)) dnsIpv6 = extraDns6.trim();
        }

        socksHost = validHostOrDefault(socksHost, TERMUX_VPN_SERVICE.DEFAULT_SOCKS_HOST);
        if (socksPort <= 0 || socksPort > 65535) socksPort = TERMUX_VPN_SERVICE.DEFAULT_SOCKS_PORT;
        appMode = normalizeAppMode(appMode);
        appPackages = validPackages(appPackages);
        dnsIpv4 = validHostOrDefault(dnsIpv4, TERMUX_VPN_SERVICE.DEFAULT_DNS_IPV4);
        dnsIpv6 = validHostOrDefault(dnsIpv6, TERMUX_VPN_SERVICE.DEFAULT_DNS_IPV6);

        return new TermuxVpnConfig(socksHost, socksPort, splitTunnelingEnabled, appMode,
            appPackages, remoteDnsEnabled,
            mapdnsAddress, dnsIpv4, dnsIpv6);
    }

    @NonNull
    private static String validHostOrDefault(@Nullable String value, @NonNull String fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        return HOST_PATTERN.matcher(value).matches() ? value : fallback;
    }

    @NonNull
    private static String normalizeAppMode(@Nullable String value) {
        if (TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(value)
            || TERMUX_VPN_SERVICE.APP_MODE_INCLUDE_SELECTED.equals(value)) {
            return TERMUX_VPN_SERVICE.APP_MODE_INCLUDE;
        }
        if (TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE.equals(value)
            || TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_SELECTED.equals(value)
            || TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_TERMUX.equals(value)) {
            return TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE;
        }
        return TERMUX_VPN_SERVICE.DEFAULT_APP_MODE;
    }

    @NonNull
    private static Set<String> validPackages(@Nullable Set<String> packages) {
        Set<String> valid = new HashSet<>();
        if (packages == null) return valid;
        for (String packageName : packages) {
            if (packageName != null && PACKAGE_PATTERN.matcher(packageName).matches()) {
                valid.add(packageName);
            }
        }
        return valid;
    }

    @NonNull
    private static Set<String> withTermux(@NonNull Set<String> packages) {
        Set<String> result = new HashSet<>(packages);
        result.add(TermuxConstants.TERMUX_PACKAGE_NAME);
        return result;
    }

    void saveTo(@NonNull TermuxAppSharedPreferences preferences) {
        preferences.setVpnSocksHost(socksHost);
        preferences.setVpnSocksPort(socksPort);
        preferences.setVpnSplitTunnelingEnabled(splitTunnelingEnabled);
        preferences.setVpnAppMode(appMode);
        preferences.setVpnAppPackages(appPackages);
        preferences.setVpnRemoteDnsEnabled(remoteDnsEnabled);
        preferences.setVpnDnsIpv4(dnsIpv4);
        preferences.setVpnDnsIpv6(dnsIpv6);
    }

    void putExtras(@NonNull Intent intent) {
        intent.putExtra(TERMUX_VPN_SERVICE.EXTRA_SOCKS_HOST, socksHost);
        intent.putExtra(TERMUX_VPN_SERVICE.EXTRA_SOCKS_PORT, socksPort);
        intent.putExtra(TERMUX_VPN_SERVICE.EXTRA_SPLIT_TUNNELING, splitTunnelingEnabled);
        intent.putExtra(TERMUX_VPN_SERVICE.EXTRA_APP_MODE, appMode);
        intent.putStringArrayListExtra(TERMUX_VPN_SERVICE.EXTRA_APP_PACKAGES,
            new ArrayList<>(appPackages));
        intent.putExtra(TERMUX_VPN_SERVICE.EXTRA_REMOTE_DNS_ENABLED, remoteDnsEnabled);
        intent.putExtra(TERMUX_VPN_SERVICE.EXTRA_DNS_IPV4, dnsIpv4);
        intent.putExtra(TERMUX_VPN_SERVICE.EXTRA_DNS_IPV6, dnsIpv6);
    }

    /**
     * hev-socks5-tunnel YAML. With remote DNS, {@code mapdns} returns Fake-IPs for A queries and
     * hev restores the domain when relaying through SOCKS5. Without it, DNS is ordinary UDP/53 via
     * SOCKS5 UDP ASSOCIATE to the configured upstream resolvers.
     */
    @NonNull
    String toHevConfigYaml(@NonNull String logFilePath) {
        String yaml = "tunnel:\n" +
            "  mtu: " + TERMUX_VPN_SERVICE.TUN_MTU + "\n" +
            "socks5:\n" +
            "  port: " + socksPort + "\n" +
            "  address: " + socksHost + "\n" +
            "  udp: udp\n" +
            "  udp-address: " + socksHost + "\n";
        if (remoteDnsEnabled) {
            yaml += "mapdns:\n" +
                "  address: " + mapdnsAddress + "\n" +
                "  port: " + TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_PORT + "\n" +
                "  network: " + TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_NETWORK + "\n" +
                "  netmask: " + TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_NETMASK + "\n" +
                "  cache-size: " + TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_CACHE_SIZE + "\n";
        }
        yaml += "misc:\n" +
            "  log-file: " + logFilePath + "\n" +
            "  log-level: warn\n";
        return yaml;
    }

    @NonNull
    String describe() {
        String routing;
        if (!splitTunnelingEnabled) {
            routing = "Exclude Termux";
        } else if (TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(appMode)) {
            routing = "Include selected (" + appPackages.size() + ")";
        } else {
            routing = "Exclude selected (" + appPackages.size() + ")";
        }
        if (remoteDnsEnabled) {
            return socksHost + ":" + socksPort + "  Remote DNS " + mapdnsAddress + "  " + routing;
        }
        return socksHost + ":" + socksPort + "  DNS " + dnsIpv4 + " / " + dnsIpv6 + "  " + routing;
    }
}
