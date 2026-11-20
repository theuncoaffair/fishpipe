package com.theuncoaffair.fishpipe.billing;

import android.content.Context;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

public final class DeviceIdentity {
    private DeviceIdentity() {
    }

    public static String hash(Context context) {
        Context app = context.getApplicationContext();
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(
                    app.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
        }
        if (androidId == null || androidId.trim().isEmpty()
                || "9774d56d682e549c".equalsIgnoreCase(androidId.trim())) {
            com.theuncoaffair.fishpipe.data.Settings settings =
                    new com.theuncoaffair.fishpipe.data.Settings(app);
            androidId = settings.getDeviceFallbackId();
            if (androidId.isEmpty()) {
                androidId = UUID.randomUUID().toString();
                settings.setDeviceFallbackId(androidId);
            }
        }
        String material = "fishpipe:" + app.getPackageName() + ":" + androidId.trim();
        return sha256Hex(material);
    }

    public static String label(Context context) {
        String hash = hash(context);
        if (hash.length() < 12) return hash.toUpperCase(Locale.US);
        return hash.substring(0, 12).toUpperCase(Locale.US);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return out.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
