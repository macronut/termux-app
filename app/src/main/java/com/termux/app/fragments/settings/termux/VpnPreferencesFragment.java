package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.vpn.TermuxVpnPrepareActivity;
import com.termux.app.vpn.TermuxVpnService;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_VPN_SERVICE;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Keep
public class VpnPreferencesFragment extends PreferenceFragmentCompat {

    private TermuxAppSharedPreferences mPreferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        mPreferences = TermuxAppSharedPreferences.build(context, true);
        if (mPreferences == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(VpnPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_vpn_preferences, rootKey);
        configureVpnEnabledPreference();
        configureSplitTunnelingPreferences(context);
        configureRemoteDnsPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        SwitchPreferenceCompat enabled = findPreference("vpn_enabled");
        if (enabled != null) {
            enabled.setChecked(TermuxVpnService.isRunning());
        }
        updateSplitTunnelingPreferences();
        SwitchPreferenceCompat remoteDns = findPreference("vpn_remote_dns_enabled");
        if (remoteDns != null) {
            updateDnsPreferenceVisibility(remoteDns.isChecked());
        }
    }

    private void configureVpnEnabledPreference() {
        SwitchPreferenceCompat enabled = findPreference("vpn_enabled");
        if (enabled == null) return;

        enabled.setChecked(TermuxVpnService.isRunning());
        enabled.setOnPreferenceChangeListener((preference, newValue) -> {
            Context context = getContext();
            if (context == null) return false;
            boolean start = Boolean.TRUE.equals(newValue);
            if (start) {
                if (mPreferences != null
                    && mPreferences.getVpnSplitTunnelingEnabled()
                    && TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(mPreferences.getVpnAppMode())
                    && mPreferences.getVpnAppPackages().isEmpty()) {
                    Toast.makeText(context, R.string.termux_vpn_app_include_empty, Toast.LENGTH_LONG).show();
                    return false;
                }
                context.startActivity(new Intent(context, TermuxVpnPrepareActivity.class)
                    .setAction(TERMUX_VPN_SERVICE.ACTION_START));
            } else {
                TermuxVpnService.startForegroundCompat(context, TermuxVpnService.newStopIntent(context));
            }
            enabled.setChecked(start);
            return false;
        });
    }

    private void configureSplitTunnelingPreferences(Context context) {
        SwitchPreferenceCompat splitTunneling = findPreference("vpn_split_tunneling_enabled");
        RadioButtonPreference excludeMode = findPreference("vpn_split_tunneling_exclude");
        RadioButtonPreference includeMode = findPreference("vpn_split_tunneling_include");
        MultiSelectListPreference excludedPackages = findPreference("vpn_excluded_packages");
        MultiSelectListPreference includedPackages = findPreference("vpn_included_packages");
        if (splitTunneling == null || excludeMode == null || includeMode == null
            || excludedPackages == null || includedPackages == null || mPreferences == null) return;

        splitTunneling.setChecked(mPreferences.getVpnSplitTunnelingEnabled());
        splitTunneling.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = Boolean.TRUE.equals(newValue);
            if (enabled && !mPreferences.isVpnSplitTunnelingInitialized()
                && TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE.equals(mPreferences.getVpnAppMode())) {
                ensureTermuxExcluded();
            }
            updateSplitTunnelingPreferences(enabled, mPreferences.getVpnAppMode());
            return true;
        });

        excludeMode.setOnPreferenceClickListener(preference -> {
            selectSplitTunnelingMode(splitTunneling.isChecked(), TERMUX_VPN_SERVICE.APP_MODE_EXCLUDE);
            return true;
        });
        includeMode.setOnPreferenceClickListener(preference -> {
            selectSplitTunnelingMode(splitTunneling.isChecked(), TERMUX_VPN_SERVICE.APP_MODE_INCLUDE);
            return true;
        });

        Set<String> excluded = mPreferences.getVpnExcludedPackages();
        Set<String> included = mPreferences.getVpnIncludedPackages();
        if (!mPreferences.isVpnSplitTunnelingInitialized() && excluded.isEmpty()) {
            excluded.add(TermuxConstants.TERMUX_PACKAGE_NAME);
        }
        populateApplicationList(context, excludedPackages, excluded, false);
        populateApplicationList(context, includedPackages, included, true);
        excludedPackages.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Set) {
                Set<String> selected = stringSetFrom(newValue);
                mPreferences.setVpnExcludedPackages(selected);
            }
            updateSplitTunnelingPreferences(splitTunneling.isChecked(), mPreferences.getVpnAppMode());
            return true;
        });
        includedPackages.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Set) {
                Set<String> selected = stringSetFrom(newValue);
                mPreferences.setVpnIncludedPackages(selected);
            }
            updateSplitTunnelingPreferences(splitTunneling.isChecked(), mPreferences.getVpnAppMode());
            return true;
        });
        updateSplitTunnelingPreferences();
    }

    private void selectSplitTunnelingMode(boolean enabled, String appMode) {
        if (!appMode.equals(mPreferences.getVpnAppMode())) {
            mPreferences.setVpnAppMode(appMode);
        }
        updateSplitTunnelingPreferences(enabled, appMode);
    }

    private void populateApplicationList(Context context, MultiSelectListPreference packages,
                                         Set<String> selected, boolean includeMode) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchers = packageManager.queryIntentActivities(launcherIntent, 0);
        Map<String, String> applicationLabels = new HashMap<>();

        for (ResolveInfo resolveInfo : launchers) {
            if (resolveInfo.activityInfo == null || resolveInfo.activityInfo.applicationInfo == null) continue;
            ApplicationInfo applicationInfo = resolveInfo.activityInfo.applicationInfo;
            String packageName = applicationInfo.packageName;
            CharSequence loadedLabel = applicationInfo.loadLabel(packageManager);
            String label = loadedLabel == null ? packageName : loadedLabel.toString();
            if (label.length() == 0) label = packageName;
            applicationLabels.put(packageName, label);
        }

        List<String> packageNames = new ArrayList<>(applicationLabels.keySet());
        Collections.sort(packageNames, (left, right) -> {
            int labelComparison = String.CASE_INSENSITIVE_ORDER.compare(
                applicationLabels.get(left), applicationLabels.get(right));
            return labelComparison != 0 ? labelComparison : left.compareTo(right);
        });

        CharSequence[] entries = new CharSequence[packageNames.size()];
        for (int i = 0; i < packageNames.size(); i++) {
            entries[i] = applicationLabels.get(packageNames.get(i));
        }
        packages.setEntries(entries);
        packages.setEntryValues(packageNames.toArray(new String[0]));

        Set<String> selectedPackages = new HashSet<>(selected);
        selectedPackages.retainAll(applicationLabels.keySet());
        if (includeMode) {
            mPreferences.setVpnIncludedPackages(selectedPackages);
        } else {
            mPreferences.setVpnExcludedPackages(selectedPackages);
        }
        packages.setValues(selectedPackages);
        updateAppPackagesSummary(packages, selectedPackages);
    }

    private void ensureTermuxExcluded() {
        if (mPreferences == null) return;
        Set<String> selected = mPreferences.getVpnExcludedPackages();
        if (selected.add(TermuxConstants.TERMUX_PACKAGE_NAME)) {
            mPreferences.setVpnExcludedPackages(selected);
        }
    }

    private void updateSplitTunnelingPreferences() {
        if (mPreferences == null) return;
        updateSplitTunnelingPreferences(mPreferences.getVpnSplitTunnelingEnabled(),
            mPreferences.getVpnAppMode());
    }

    private void updateSplitTunnelingPreferences(boolean enabled, @Nullable String appMode) {
        SwitchPreferenceCompat splitTunneling = findPreference("vpn_split_tunneling_enabled");
        RadioButtonPreference excludeMode = findPreference("vpn_split_tunneling_exclude");
        RadioButtonPreference includeMode = findPreference("vpn_split_tunneling_include");
        MultiSelectListPreference excludedPackages = findPreference("vpn_excluded_packages");
        MultiSelectListPreference includedPackages = findPreference("vpn_included_packages");
        if (splitTunneling == null || excludeMode == null || includeMode == null
            || excludedPackages == null || includedPackages == null || mPreferences == null) return;

        if (appMode == null) appMode = mPreferences.getVpnAppMode();
        splitTunneling.setChecked(enabled);
        boolean includeModeSelected = TERMUX_VPN_SERVICE.APP_MODE_INCLUDE.equals(appMode);
        excludeMode.setChecked(!includeModeSelected);
        includeMode.setChecked(includeModeSelected);
        excludeMode.setVisible(enabled);
        includeMode.setVisible(enabled);
        excludeMode.setEnabled(enabled);
        includeMode.setEnabled(enabled);
        excludedPackages.setVisible(enabled && !includeModeSelected);
        includedPackages.setVisible(enabled && includeModeSelected);
        excludedPackages.setEnabled(enabled && !includeModeSelected);
        includedPackages.setEnabled(enabled && includeModeSelected);

        excludedPackages.setTitle(R.string.termux_vpn_app_packages_title);
        excludedPackages.setDialogTitle(R.string.termux_vpn_app_packages_title);
        includedPackages.setTitle(R.string.termux_vpn_app_packages_included_title);
        includedPackages.setDialogTitle(R.string.termux_vpn_app_packages_included_title);
        Set<String> excluded = mPreferences.getVpnExcludedPackages();
        Set<String> included = mPreferences.getVpnIncludedPackages();
        excludedPackages.setValues(excluded);
        includedPackages.setValues(included);
        updateAppPackagesSummary(excludedPackages, excluded);
        updateAppPackagesSummary(includedPackages, included);

        if (enabled && includeModeSelected && included.contains(TermuxConstants.TERMUX_PACKAGE_NAME)) {
            includedPackages.setSummary(R.string.termux_vpn_app_termux_warning);
        } else if (enabled && !includeModeSelected
            && !excluded.contains(TermuxConstants.TERMUX_PACKAGE_NAME)) {
            excludedPackages.setSummary(R.string.termux_vpn_app_termux_warning);
        }
    }

    private void updateAppPackagesSummary(MultiSelectListPreference packages, Set<String> selected) {
        if (selected.isEmpty()) {
            packages.setSummary(R.string.termux_vpn_app_packages_none);
        } else {
            packages.setSummary(getString(R.string.termux_vpn_app_packages_summary, selected.size()));
        }
    }

    private static Set<String> stringSetFrom(Object value) {
        Set<String> result = new HashSet<>();
        if (!(value instanceof Set)) return result;
        for (Object item : (Set<?>) value) {
            if (item instanceof String) result.add((String) item);
        }
        return result;
    }

    private void configureRemoteDnsPreferences() {
        SwitchPreferenceCompat remoteDns = findPreference("vpn_remote_dns_enabled");
        if (remoteDns == null) return;

        remoteDns.setOnPreferenceChangeListener((preference, newValue) -> {
            updateDnsPreferenceVisibility(Boolean.TRUE.equals(newValue));
            return true;
        });
        updateDnsPreferenceVisibility(remoteDns.isChecked());
    }

    private void updateDnsPreferenceVisibility(boolean remoteDnsEnabled) {
        SwitchPreferenceCompat remoteDns = findPreference("vpn_remote_dns_enabled");
        EditTextPreference dns4 = findPreference("vpn_dns_ipv4");
        EditTextPreference dns6 = findPreference("vpn_dns_ipv6");
        if (remoteDns == null) return;

        setPreferenceVisible(dns4, !remoteDnsEnabled);
        setPreferenceVisible(dns6, !remoteDnsEnabled);
    }

    private static void setPreferenceVisible(@Nullable Preference preference, boolean visible) {
        if (preference != null) {
            preference.setVisible(visible);
        }
    }
}

class VpnPreferencesDataStore extends PreferenceDataStore {

    private final TermuxAppSharedPreferences mPreferences;

    private static VpnPreferencesDataStore mInstance;

    private VpnPreferencesDataStore(Context context) {
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized VpnPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new VpnPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        if (mPreferences == null || key == null) return defValue;
        switch (key) {
            case "vpn_socks_host":
                return mPreferences.getVpnSocksHost();
            case "vpn_socks_port":
                return String.valueOf(mPreferences.getVpnSocksPort());
            case "vpn_dns_ipv4":
                return mPreferences.getVpnDnsIpv4();
            case "vpn_dns_ipv6":
                return mPreferences.getVpnDnsIpv6();
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (mPreferences == null || key == null || value == null) return;
        switch (key) {
            case "vpn_socks_host":
                mPreferences.setVpnSocksHost(value.trim());
                break;
            case "vpn_socks_port":
                try {
                    mPreferences.setVpnSocksPort(Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {}
                break;
            case "vpn_dns_ipv4":
                mPreferences.setVpnDnsIpv4(value.trim());
                break;
            case "vpn_dns_ipv6":
                mPreferences.setVpnDnsIpv6(value.trim());
                break;
            default:
                break;
        }
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (mPreferences == null || key == null) return defValues;
        if ("vpn_excluded_packages".equals(key)) {
            return mPreferences.getVpnExcludedPackages();
        }
        if ("vpn_included_packages".equals(key)) {
            return mPreferences.getVpnIncludedPackages();
        }
        if ("vpn_app_packages".equals(key)) {
            return mPreferences.getVpnAppPackages();
        }
        return defValues;
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        if (mPreferences == null || key == null) return;
        if ("vpn_excluded_packages".equals(key)) {
            mPreferences.setVpnExcludedPackages(values);
        } else if ("vpn_included_packages".equals(key)) {
            mPreferences.setVpnIncludedPackages(values);
        } else if ("vpn_app_packages".equals(key)) {
            mPreferences.setVpnAppPackages(values);
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null || key == null) return defValue;
        if ("vpn_split_tunneling_enabled".equals(key)) {
            return mPreferences.getVpnSplitTunnelingEnabled();
        }
        if ("vpn_remote_dns_enabled".equals(key)) {
            return mPreferences.getVpnRemoteDnsEnabled();
        }
        return defValue;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null || key == null) return;
        if ("vpn_split_tunneling_enabled".equals(key)) {
            mPreferences.setVpnSplitTunnelingEnabled(value);
            return;
        }
        if ("vpn_remote_dns_enabled".equals(key)) {
            mPreferences.setVpnRemoteDnsEnabled(value);
        }
    }
}
