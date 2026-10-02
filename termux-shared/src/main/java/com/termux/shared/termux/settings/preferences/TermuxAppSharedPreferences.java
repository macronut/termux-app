package com.termux.shared.termux.settings.preferences;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.logger.Logger;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TermuxAppSharedPreferences extends AppSharedPreferences {

    private int MIN_FONTSIZE;
    private int MAX_FONTSIZE;
    private int DEFAULT_FONTSIZE;

    private static final String LOG_TAG = "TermuxAppSharedPreferences";

    private TermuxAppSharedPreferences(@NonNull Context context) {
        super(context,
            SharedPreferenceUtils.getPrivateSharedPreferences(context,
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION),
            SharedPreferenceUtils.getPrivateAndMultiProcessSharedPreferences(context,
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION));

        setFontVariables(context);
    }

    /**
     * Get {@link TermuxAppSharedPreferences}.
     *
     * @param context The {@link Context} to use to get the {@link Context} of the
     *                {@link TermuxConstants#TERMUX_PACKAGE_NAME}.
     * @return Returns the {@link TermuxAppSharedPreferences}. This will {@code null} if an exception is raised.
     */
    @Nullable
    public static TermuxAppSharedPreferences build(@NonNull final Context context) {
        Context termuxPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_PACKAGE_NAME);
        if (termuxPackageContext == null)
            return null;
        else
            return new TermuxAppSharedPreferences(termuxPackageContext);
    }

    /**
     * Get {@link TermuxAppSharedPreferences}.
     *
     * @param context The {@link Context} to use to get the {@link Context} of the
     *                {@link TermuxConstants#TERMUX_PACKAGE_NAME}.
     * @param exitAppOnError If {@code true} and failed to get package context, then a dialog will
     *                       be shown which when dismissed will exit the app.
     * @return Returns the {@link TermuxAppSharedPreferences}. This will {@code null} if an exception is raised.
     */
    public static TermuxAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_PACKAGE_NAME, exitAppOnError);
        if (termuxPackageContext == null)
            return null;
        else
            return new TermuxAppSharedPreferences(termuxPackageContext);
    }



    public boolean shouldShowTerminalToolbar() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_SHOW_TERMINAL_TOOLBAR, TERMUX_APP.DEFAULT_VALUE_SHOW_TERMINAL_TOOLBAR);
    }

    public void setShowTerminalToolbar(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SHOW_TERMINAL_TOOLBAR, value, false);
    }

    public boolean toogleShowTerminalToolbar() {
        boolean currentValue = shouldShowTerminalToolbar();
        setShowTerminalToolbar(!currentValue);
        return !currentValue;
    }



    public boolean isTerminalMarginAdjustmentEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT, TERMUX_APP.DEFAULT_TERMINAL_MARGIN_ADJUSTMENT);
    }

    public void setTerminalMarginAdjustment(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_MARGIN_ADJUSTMENT, value, false);
    }



    public boolean isSoftKeyboardEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED, TERMUX_APP.DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED);
    }

    public void setSoftKeyboardEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED, value, false);
    }

    public boolean isSoftKeyboardEnabledOnlyIfNoHardware() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE, TERMUX_APP.DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE);
    }

    public void setSoftKeyboardEnabledOnlyIfNoHardware(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE, value, false);
    }



    public boolean shouldKeepScreenOn() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_KEEP_SCREEN_ON, TERMUX_APP.DEFAULT_VALUE_KEEP_SCREEN_ON);
    }

    public void setKeepScreenOn(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_KEEP_SCREEN_ON, value, false);
    }



    public static int[] getDefaultFontSizes(Context context) {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());

        int[] sizes = new int[3];

        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        sizes[1] = (int) (4f * dipInPixels); // min

        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        int defaultFontSize = Math.round(12 * dipInPixels);
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (defaultFontSize % 2 == 1) defaultFontSize--;

        sizes[0] = defaultFontSize; // default

        sizes[2] = 256; // max

        return sizes;
    }

    public void setFontVariables(Context context) {
        int[] sizes = getDefaultFontSizes(context);

        DEFAULT_FONTSIZE = sizes[0];
        MIN_FONTSIZE = sizes[1];
        MAX_FONTSIZE = sizes[2];
    }

    public int getFontSize() {
        int fontSize = SharedPreferenceUtils.getIntStoredAsString(mSharedPreferences, TERMUX_APP.KEY_FONTSIZE, DEFAULT_FONTSIZE);
        return DataUtils.clamp(fontSize, MIN_FONTSIZE, MAX_FONTSIZE);
    }

    public void setFontSize(int value) {
        SharedPreferenceUtils.setIntStoredAsString(mSharedPreferences, TERMUX_APP.KEY_FONTSIZE, value, false);
    }

    public void changeFontSize(boolean increase) {
        int fontSize = getFontSize();

        fontSize += (increase ? 1 : -1) * 2;
        fontSize = Math.max(MIN_FONTSIZE, Math.min(fontSize, MAX_FONTSIZE));

        setFontSize(fontSize);
    }



    public String getCurrentSession() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_CURRENT_SESSION, null, true);
    }

    public void setCurrentSession(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_CURRENT_SESSION, value, false);
    }



    public int getLogLevel() {
        return SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    public void setLogLevel(Context context, int logLevel) {
        logLevel = Logger.setLogLevel(context, logLevel);
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_LOG_LEVEL, logLevel, false);
    }



    public int getLastNotificationId() {
        return SharedPreferenceUtils.getInt(mSharedPreferences, TERMUX_APP.KEY_LAST_NOTIFICATION_ID, TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID);
    }

    public void setLastNotificationId(int notificationId) {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_LAST_NOTIFICATION_ID, notificationId, false);
    }


    public synchronized int getAndIncrementAppShellNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        return SharedPreferenceUtils.getAndIncrementInt(mSharedPreferences, TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT, true, Integer.MAX_VALUE);
    }

    public synchronized void resetAppShellNumberSinceBoot() {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT, true);
    }

    public synchronized int getAndIncrementTerminalSessionNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        return SharedPreferenceUtils.getAndIncrementInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT, true, Integer.MAX_VALUE);
    }

    public synchronized void resetTerminalSessionNumberSinceBoot() {
        SharedPreferenceUtils.setInt(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT,
            TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT, true);
    }


    public boolean isTerminalViewKeyLoggingEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, TERMUX_APP.DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED);
    }

    public void setTerminalViewKeyLoggingEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, value, false);
    }



    public boolean arePluginErrorNotificationsEnabled(boolean readFromFile) {
        if (readFromFile)
            return SharedPreferenceUtils.getBoolean(mMultiProcessSharedPreferences, TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED);
        else
            return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED);
    }

    public void setPluginErrorNotificationsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, value, false);
    }



    public boolean areCrashReportNotificationsEnabled(boolean readFromFile) {
        if (readFromFile)
            return SharedPreferenceUtils.getBoolean(mMultiProcessSharedPreferences, TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED);
       else
            return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED);
    }

    public void setCrashReportNotificationsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, value, false);
    }



    public String getVpnSocksHost() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_VPN_SOCKS_HOST,
            TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_SOCKS_HOST, true);
    }

    public void setVpnSocksHost(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_VPN_SOCKS_HOST, value, false);
    }

    public int getVpnSocksPort() {
        String stored = SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_VPN_SOCKS_PORT,
            String.valueOf(TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_SOCKS_PORT), true);
        try {
            int port = Integer.parseInt(stored);
            if (port > 0 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {}
        return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_SOCKS_PORT;
    }

    public void setVpnSocksPort(int value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_VPN_SOCKS_PORT, String.valueOf(value), false);
    }

    public boolean getVpnSplitTunnelingEnabled() {
        if (mSharedPreferences.contains(TERMUX_APP.KEY_VPN_SPLIT_TUNNELING_ENABLED)) {
            return SharedPreferenceUtils.getBoolean(mSharedPreferences,
                TERMUX_APP.KEY_VPN_SPLIT_TUNNELING_ENABLED,
                TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_SPLIT_TUNNELING_ENABLED);
        }

        String legacyMode = getLegacyVpnAppMode();
        if (legacyMode == null) {
            return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_SPLIT_TUNNELING_ENABLED;
        }
        return !TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_GLOBAL.equals(legacyMode);
    }

    public void setVpnSplitTunnelingEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences,
            TERMUX_APP.KEY_VPN_SPLIT_TUNNELING_ENABLED, value, false);
    }

    public boolean isVpnSplitTunnelingInitialized() {
        return mSharedPreferences.contains(TERMUX_APP.KEY_VPN_SPLIT_TUNNELING_ENABLED);
    }

    public String getVpnAppMode() {
        String mode = SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_VPN_SPLIT_TUNNELING_MODE, null, true);
        if (isCurrentVpnAppMode(mode)) return mode;

        String legacyMode = getLegacyVpnAppMode();
        if (TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_INCLUDE_SELECTED.equals(legacyMode)) {
            return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_INCLUDE;
        }
        if (TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_SELECTED.equals(legacyMode)
            || TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_TERMUX.equals(legacyMode)) {
            return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE;
        }
        return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_APP_MODE;
    }

    public void setVpnAppMode(String value) {
        if (!isCurrentVpnAppMode(value)) {
            value = TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_APP_MODE;
        }
        SharedPreferenceUtils.setString(mSharedPreferences,
            TERMUX_APP.KEY_VPN_SPLIT_TUNNELING_MODE, value, false);
    }

    public Set<String> getVpnExcludedPackages() {
        return getVpnPackages(TERMUX_APP.KEY_VPN_EXCLUDED_PACKAGES,
            TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE);
    }

    public void setVpnExcludedPackages(Set<String> packages) {
        setVpnPackages(TERMUX_APP.KEY_VPN_EXCLUDED_PACKAGES, packages);
    }

    public Set<String> getVpnIncludedPackages() {
        return getVpnPackages(TERMUX_APP.KEY_VPN_INCLUDED_PACKAGES,
            TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_INCLUDE);
    }

    public void setVpnIncludedPackages(Set<String> packages) {
        setVpnPackages(TERMUX_APP.KEY_VPN_INCLUDED_PACKAGES, packages);
    }

    /**
     * Returns the package list for the currently selected routing mode.
     */
    public Set<String> getVpnAppPackages() {
        return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(getVpnAppMode())
            ? getVpnIncludedPackages() : getVpnExcludedPackages();
    }

    /**
     * Stores the package list for the currently selected routing mode.
     */
    public void setVpnAppPackages(Set<String> packages) {
        if (TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(getVpnAppMode())) {
            setVpnIncludedPackages(packages);
        } else {
            setVpnExcludedPackages(packages);
        }
    }

    private void setVpnPackages(String key, Set<String> packages) {
        Set<String> values = packages == null ? Collections.emptySet() : new HashSet<>(packages);
        SharedPreferenceUtils.setStringSet(mSharedPreferences, key, values, false);
    }

    private Set<String> getVpnPackages(String key, String mode) {
        if (mSharedPreferences.contains(key)) {
            Set<String> packages = SharedPreferenceUtils.getStringSet(mSharedPreferences, key,
                Collections.emptySet());
            return packages == null ? new HashSet<>() : new HashSet<>(packages);
        }

        // Migrate the old single list into the mode it represented. Keep the old key untouched
        // until the new list is written, so reads remain backward-compatible.
        boolean hasNewPackageLists = mSharedPreferences.contains(TERMUX_APP.KEY_VPN_EXCLUDED_PACKAGES)
            || mSharedPreferences.contains(TERMUX_APP.KEY_VPN_INCLUDED_PACKAGES);
        if (hasNewPackageLists || !mode.equals(getVpnAppMode())) {
            return new HashSet<>();
        }

        Set<String> packages = SharedPreferenceUtils.getStringSet(mSharedPreferences,
            TERMUX_APP.KEY_VPN_APP_PACKAGES, Collections.emptySet());
        Set<String> values = packages == null ? new HashSet<>() : new HashSet<>(packages);
        if (values.isEmpty()
            && TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_TERMUX.equals(
                getLegacyVpnAppMode())
            && TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE.equals(mode)) {
            values.add(TermuxConstants.TERMUX_PACKAGE_NAME);
        }
        return values;
    }

    @Deprecated
    public boolean getVpnGlobal() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_VPN_GLOBAL,
            TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_GLOBAL);
    }

    @Deprecated
    public void setVpnGlobal(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_VPN_GLOBAL, value, false);
    }

    public boolean getVpnRemoteDnsEnabled() {
        return SharedPreferenceUtils.getBoolean(mSharedPreferences, TERMUX_APP.KEY_VPN_REMOTE_DNS_ENABLED,
            TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_REMOTE_DNS_ENABLED);
    }

    public void setVpnRemoteDnsEnabled(boolean value) {
        SharedPreferenceUtils.setBoolean(mSharedPreferences, TERMUX_APP.KEY_VPN_REMOTE_DNS_ENABLED, value, false);
    }

    /**
     * @deprecated Mapped DNS is fixed to the VPN's internal address.
     */
    @Deprecated
    public String getVpnMapdnsAddress() {
        return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_MAPDNS_ADDRESS;
    }

    /**
     * @deprecated Mapped DNS is fixed to the VPN's internal address.
     */
    @Deprecated
    public void setVpnMapdnsAddress(String value) {
        // Kept as a no-op for callers compiled against the old configurable setting.
    }

    public String getVpnDnsIpv4() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_VPN_DNS_IPV4,
            TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_DNS_IPV4, true);
    }

    public void setVpnDnsIpv4(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_VPN_DNS_IPV4, value, false);
    }

    public String getVpnDnsIpv6() {
        return SharedPreferenceUtils.getString(mSharedPreferences, TERMUX_APP.KEY_VPN_DNS_IPV6,
            TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.DEFAULT_DNS_IPV6, true);
    }

    public void setVpnDnsIpv6(String value) {
        SharedPreferenceUtils.setString(mSharedPreferences, TERMUX_APP.KEY_VPN_DNS_IPV6, value, false);
    }

    private String getLegacyVpnAppMode() {
        String mode = SharedPreferenceUtils.getString(mSharedPreferences,
            TERMUX_APP.KEY_VPN_APP_MODE, null, true);
        if (isLegacyVpnAppMode(mode)) return mode;

        if (mSharedPreferences.contains(TERMUX_APP.KEY_VPN_GLOBAL)) {
            return getVpnGlobal()
                ? TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_GLOBAL
                : TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_TERMUX;
        }
        return null;
    }

    private static boolean isCurrentVpnAppMode(String mode) {
        return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(mode)
            || TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE.equals(mode);
    }

    private static boolean isLegacyVpnAppMode(String mode) {
        return TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_GLOBAL.equals(mode)
            || TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_TERMUX.equals(mode)
            || TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_INCLUDE_SELECTED.equals(mode)
            || TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE_SELECTED.equals(mode);
    }

}
