package com.theuncoaffair.fishpipe.billing;

import android.content.Context;
import android.content.Intent;

import com.theuncoaffair.fishpipe.PaywallActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LicenseManager {
    public static final int FREE_LIMIT = Settings.FREE_LIMIT;
    private static final String DEVELOPER_DEVICE_HASH = "";
    private static final long OFFLINE_GRACE_MS = 24L * 60L * 60L * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "fishpipe-license");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean BOOTSTRAPPING = new AtomicBoolean(false);
    private static volatile boolean lastSyncFailed;
    private static long lastPaywallLaunch;

    private LicenseManager() {
    }

    public static void bootstrap(Context context) {
        if (context == null || !LicenseClient.isConfigured()) return;
        if (!BOOTSTRAPPING.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                Settings settings = AppGraph.get(app).settings();
                String hash = DeviceIdentity.hash(app);
                String token = ensureDeviceToken(app, settings, hash);
                if (!token.isEmpty()) {
                    LicenseClient.Result usage = LicenseClient.usage(token, hash);
                    if (usage.ok) {
                        settings.setUsage(Math.max(usage.used, settings.getDownloadUses()), true);
                        lastSyncFailed = false;
                    } else {
                        lastSyncFailed = true;
                        handleAuthFailure(settings, usage);
                    }
                }
                if (settings.isProUnlocked()) {
                    validateSession(settings, hash);
                }
            } finally {
                BOOTSTRAPPING.set(false);
            }
        });
    }

    public static boolean allowDownload(Context context) {
        if (context == null) return false;
        Settings settings = AppGraph.get(context).settings();
        if (isDeveloper(context) && settings.isPaywallDismissed()) return true;

        if (settings.isProUnlocked()) {
            if (hasUsableSession(settings)) return true;
            bootstrap(context);
            Ui.toast(context, R.string.paywall_unauthorized);
            showPaywall(context, true);
            return false;
        }

        if (LicenseClient.isConfigured() && !settings.isUsageSynced()) {
            bootstrap(context);
            Ui.toast(context, lastSyncFailed
                    ? R.string.paywall_sync_error
                    : R.string.paywall_sync_pending);
            return false;
        }

        if (settings.getDownloadUses() < FREE_LIMIT) return true;
        showPaywall(context);
        return false;
    }

    public static void resetUsage(Context context) {
        if (context == null) return;
        Settings settings = AppGraph.get(context).settings();
        settings.setUsage(0, settings.isUsageSynced());
    }

    public static void recordUse(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        Settings settings = AppGraph.get(app).settings();
        if (settings.isProUnlocked()) return;
        if (isDeveloper(app) && settings.isPaywallDismissed()) return;

        int used = settings.getDownloadUses();
        if (used >= FREE_LIMIT) return;
        settings.setUsage(used + 1, settings.isUsageSynced());
        if (!LicenseClient.isConfigured()) return;

        IO.execute(() -> {
            String hash = DeviceIdentity.hash(app);
            Settings latest = AppGraph.get(app).settings();
            String token = ensureDeviceToken(app, latest, hash);
            if (token.isEmpty()) return;

            LicenseClient.Result result = LicenseClient.consume(
                    token, hash, UUID.randomUUID().toString());
            if (result.ok) {
                Settings current = AppGraph.get(app).settings();
                int serverUsed = Math.max(result.used, current.getDownloadUses());
                if (!result.allowed) serverUsed = FREE_LIMIT;
                current.setUsage(serverUsed, true);
                lastSyncFailed = false;
            } else {
                lastSyncFailed = true;
                handleAuthFailure(latest, result);
            }
        });
    }

    public static boolean isProActive(Context context) {
        if (context == null) return false;
        Settings settings = AppGraph.get(context).settings();
        return settings.isProUnlocked() && hasUsableSession(settings);
    }

    public static int remaining(Context context) {
        if (context == null) return 0;
        Settings settings = AppGraph.get(context).settings();
        if (isProActive(context)) return FREE_LIMIT;
        if (isDeveloper(context) && settings.isPaywallDismissed()) return FREE_LIMIT;
        return Math.max(0, FREE_LIMIT - settings.getDownloadUses());
    }

    public static boolean isDeveloper(Context context) {
        if (DEVELOPER_DEVICE_HASH.isEmpty()) return false;
        return DEVELOPER_DEVICE_HASH.equalsIgnoreCase(DeviceIdentity.hash(context));
    }

    public static String deviceHash(Context context) {
        return DeviceIdentity.hash(context);
    }

    public static String deviceLabel(Context context) {
        return DeviceIdentity.label(context);
    }

    private static boolean hasUsableSession(Settings settings) {
        String token = settings.getSessionToken();
        if (token.isEmpty()) return false;
        long now = System.currentTimeMillis();
        long licenseExpiresAt = settings.getLicenseExpiresAt();
        if (licenseExpiresAt > 0L && licenseExpiresAt <= now) return false;
        long expiresAt = settings.getSessionExpiresAt();
        if (expiresAt > now) return true;
        long lastCheck = settings.getSessionLastCheck();
        return lastCheck > 0L && now - lastCheck < OFFLINE_GRACE_MS;
    }

    private static String ensureDeviceToken(Context app, Settings settings, String hash) {
        String token = settings.getDeviceToken();
        if (!token.isEmpty() && hash.equals(settings.getRegisteredDeviceHash())) {
            return token;
        }
        LicenseClient.Result result = LicenseClient.register(hash);
        if (!result.ok) {
            lastSyncFailed = true;
            handleAuthFailure(settings, result);
            return "";
        }
        settings.setDeviceToken(result.deviceToken);
        settings.setRegisteredDeviceHash(hash);
        settings.setUsage(Math.max(result.used, settings.getDownloadUses()), true);
        lastSyncFailed = false;
        return result.deviceToken;
    }

    private static void validateSession(Settings settings, String hash) {
        String token = settings.getSessionToken();
        if (token.isEmpty()) {
            settings.setProUnlocked(false, settings.getProEmail());
            return;
        }
        LicenseClient.Result result = LicenseClient.validateSession(token, hash);
        if (result.ok) {
            settings.setSessionExpiry(result.expiresAt);
            settings.setLicenseExpiry(result.licenseExpiresAt);
            return;
        }
        handleAuthFailure(settings, result);
    }

    private static void handleAuthFailure(Settings settings, LicenseClient.Result result) {
        if (result == null) return;
        if ("unauthorized".equals(result.code)
                || "invalid_device".equals(result.code)
                || "session_expired".equals(result.code)
                || "session_revoked".equals(result.code)
                || "device_mismatch".equals(result.code)
                || "license_expired".equals(result.code)
                || "not_paid".equals(result.code)) {
            settings.setDeviceToken("");
            settings.setUsage(settings.getDownloadUses(), false);
            settings.clearSession();
            settings.setProUnlocked(false, settings.getProEmail());
        }
    }

    public static void showPaywall(Context context) {
        showPaywall(context, false);
    }

    private static void showPaywall(Context context, boolean voluntary) {
        long now = System.currentTimeMillis();
        if (now - lastPaywallLaunch < 400L) return;
        lastPaywallLaunch = now;
        Intent intent = new Intent(context, PaywallActivity.class);
        intent.putExtra(PaywallActivity.EXTRA_VOLUNTARY, voluntary);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
