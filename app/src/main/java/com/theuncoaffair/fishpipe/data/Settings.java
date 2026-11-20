package com.theuncoaffair.fishpipe.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class Settings {
    public static final String LANG_SYSTEM = "system";
    public static final String LANG_EN = "en";
    public static final String LANG_VI = "vi";
    public static final String LANG_ES = "es";
    public static final String LANG_PT = "pt";
    public static final String LANG_FR = "fr";
    public static final String LANG_DE = "de";
    public static final String LANG_IT = "it";
    public static final String LANG_NL = "nl";
    public static final String LANG_PL = "pl";
    public static final String LANG_RU = "ru";
    public static final String LANG_TR = "tr";
    public static final String LANG_AR = "ar";
    public static final String LANG_HI = "hi";
    public static final String LANG_BN = "bn";
    public static final String LANG_UR = "ur";
    public static final String LANG_FA = "fa";
    public static final String LANG_ID = "id";
    public static final String LANG_MS = "ms";
    public static final String LANG_TH = "th";
    public static final String LANG_JA = "ja";
    public static final String LANG_KO = "ko";
    public static final String LANG_ZH = "zh";
    public static final String LANG_FIL = "fil";
    public static final String LANG_SW = "sw";
    public static final String LANG_AM = "am";
    public static final String LANG_HA = "ha";
    public static final String LANG_YO = "yo";
    public static final String LANG_IG = "ig";
    public static final String LANG_ZU = "zu";
    public static final String LANG_AF = "af";
    public static final String LANG_SO = "so";
    public static final String LANG_SN = "sn";
    public static final String LANG_XH = "xh";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_AMOLED = "amoled";

    public static final String NETWORK_ANY = "any";
    public static final String NETWORK_WIFI = "wifi";
    public static final String NETWORK_MOBILE = "mobile";

    public static final String DEFAULT_ASK = "ask";
    public static final String VIDEO_BEST = "best";
    public static final String VIDEO_HD = "hd";
    public static final String VIDEO_STANDARD = "standard";
    public static final String VIDEO_WM = "watermark";
    public static final String VIDEO_AUDIO = "audio";
    public static final String VIDEO_COVER = "cover";
    public static final String AUDIO_MUSIC = "music";
    public static final String PHOTO_ONLY = "photos";
    public static final String PHOTO_SLIDESHOW = "slideshow";
    public static final String PHOTO_AUDIO = "audio";

    public static final String FOLDER_FLAT = "flat";
    public static final String FOLDER_CREATOR = "creator";

    public static final String CAPTION_SRT = "srt";
    public static final String CAPTION_EMBED = "embed";

    public static final int FREE_LIMIT = 3;

    private static final String PREFS = "fishpipe_settings";
    private static final String PREFS_SECURE = "fishpipe_settings_secure";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THEME = "theme";
    private static final String KEY_PLAYBACK_URI = "playback_uri";
    private static final String KEY_PLAYBACK_POSITION = "playback_position";
    private static final String KEY_PLAYBACK_REPEAT = "playback_repeat";
    private static final String KEY_AUTO_HD = "auto_hd";
    private static final String KEY_AUTO_DETECT = "auto_detect";
    private static final String KEY_CREATOR_FOLDERS = "creator_folders";
    private static final String KEY_FOLDER_MODE = "folder_mode";
    private static final String KEY_DEFAULT_VIDEO = "default_video";
    private static final String KEY_DEFAULT_AUDIO = "default_audio";
    private static final String KEY_DEFAULT_PHOTOS = "default_photos";
    private static final String KEY_CUSTOM_RESOLVER = "custom_resolver";
    private static final String KEY_RESOLVER_KEY = "resolver_key";
    private static final String KEY_LAST_CLIPBOARD = "last_clipboard";
    private static final String KEY_LAST_COPIED_LINK = "last_copied_link";
    private static final String KEY_LAST_CLIPBOARD_SEEN = "last_clipboard_seen";
    private static final String KEY_LAST_CLIPBOARD_TIMESTAMP = "last_clipboard_timestamp";
    private static final String KEY_FLOATING_COPIED_LINK = "floating_copied_link";
    private static final String KEY_FLOATING_PAGE_LINK = "floating_page_link";
    private static final String KEY_DOWNLOAD_PATH = "download_path";
    private static final String KEY_MAX_TASKS = "max_tasks";
    private static final String KEY_SPEED_LIMIT = "speed_limit";
    private static final String KEY_NETWORK = "network";
    private static final String KEY_NOTIFY_PROGRESS = "notify_progress";
    private static final String KEY_NOTIFY_COMPLETE = "notify_complete";
    private static final String KEY_SHOW_DOWNLOAD_OPTIONS = "show_download_options";
    private static final String KEY_TOAST_DOWNLOAD_START = "toast_download_start";
    private static final String KEY_TOAST_DOWNLOAD_END = "toast_download_end";
    private static final String KEY_NOTIFY_EMBED_START = "notify_embed_start";
    private static final String KEY_NOTIFY_EMBED_FINISHED = "notify_embed_finished";
    private static final String KEY_TOAST_EMBED_START = "toast_embed_start";
    private static final String KEY_TOAST_EMBED_FINISHED = "toast_embed_finished";
    private static final String KEY_CAPTIONS_ENABLED = "captions_enabled";
    private static final String KEY_CAPTION_MODE = "caption_mode";
    private static final String KEY_REGION_CODE = "region_code";
    private static final String KEY_REGION_NAME = "region_name";
    private static final String KEY_STAY_IN_APP = "stay_in_app";
    private static final String KEY_FLOAT_X = "float_x";
    private static final String KEY_FLOAT_Y = "float_y";
    private static final String KEY_FLOAT_SCALE = "float_scale";
    private static final String KEY_FLOAT_OPACITY = "float_opacity";
    private static final String KEY_WHATS_NEW_SEEN = "whats_new_seen";
    private static final String KEY_WHATS_NEW_DISMISSED = "whats_new_dismissed";
    private static final String KEY_PRO_UNLOCKED = "pro_unlocked";
    private static final String KEY_PRO_EMAIL = "pro_email";
    private static final String KEY_DOWNLOAD_USES = "download_uses";
    private static final String KEY_USAGE_SYNCED = "usage_synced";
    private static final String KEY_PAYWALL_DISMISSED = "paywall_dismissed";
    private static final String KEY_DEVICE_FALLBACK_ID = "device_fallback_id";
    private static final String KEY_DEVICE_TOKEN = "device_token";
    private static final String KEY_REGISTERED_DEVICE_HASH = "registered_device_hash";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_SESSION_EXPIRES_AT = "session_expires_at";
    private static final String KEY_SESSION_LAST_CHECK = "session_last_check";
    private static final String KEY_LICENSE_EXPIRES_AT = "license_expires_at";

    private final SharedPreferences prefs;

    public Settings(Context context) {
        prefs = createPrefs(context.getApplicationContext());
    }

    private static SharedPreferences createPrefs(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences secure = EncryptedSharedPreferences.create(
                    context,
                    PREFS_SECURE,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            migratePlainPrefs(context, secure);
            return secure;
        } catch (Exception e) {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    @SuppressWarnings("unchecked")
    private static void migratePlainPrefs(Context context, SharedPreferences secure) {
        if (!secure.getAll().isEmpty()) return;
        SharedPreferences plain = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (plain.getAll().isEmpty()) return;
        SharedPreferences.Editor editor = secure.edit();
        for (java.util.Map.Entry<String, ?> entry : plain.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            } else if (value instanceof java.util.Set) {
                editor.putStringSet(entry.getKey(), (java.util.Set<String>) value);
            }
        }
        if (editor.commit()) plain.edit().clear().apply();
    }

    public String getWhatsNewSeen() {
        return prefs.getString(KEY_WHATS_NEW_SEEN, "");
    }

    public void setWhatsNewSeen(String version) {
        prefs.edit().putString(KEY_WHATS_NEW_SEEN, version == null ? "" : version).apply();
    }

    public boolean isWhatsNewDismissed() {
        return prefs.getBoolean(KEY_WHATS_NEW_DISMISSED, false);
    }

    public void setWhatsNewDismissed(boolean dismissed) {
        prefs.edit().putBoolean(KEY_WHATS_NEW_DISMISSED, dismissed).apply();
    }

    public boolean isProUnlocked() {
        return prefs.getBoolean(KEY_PRO_UNLOCKED, false);
    }

    public void setProUnlocked(boolean unlocked, String identifier) {
        prefs.edit()
                .putBoolean(KEY_PRO_UNLOCKED, unlocked)
                .putString(KEY_PRO_EMAIL, identifier == null ? "" : identifier)
                .apply();
    }

    public String getProEmail() {
        String value = prefs.getString(KEY_PRO_EMAIL, "");
        return value == null ? "" : value.trim();
    }

    public int getDownloadUses() {
        return Math.max(0, Math.min(FREE_LIMIT, prefs.getInt(KEY_DOWNLOAD_USES, 0)));
    }

    public boolean isUsageSynced() {
        return prefs.getBoolean(KEY_USAGE_SYNCED, false);
    }

    public void setUsage(int used, boolean synced) {
        prefs.edit()
                .putInt(KEY_DOWNLOAD_USES, Math.max(0, Math.min(FREE_LIMIT, used)))
                .putBoolean(KEY_USAGE_SYNCED, synced)
                .apply();
    }

    public boolean isPaywallDismissed() {
        return prefs.getBoolean(KEY_PAYWALL_DISMISSED, false);
    }

    public void setPaywallDismissed(boolean dismissed) {
        prefs.edit().putBoolean(KEY_PAYWALL_DISMISSED, dismissed).apply();
    }

    public String getDeviceFallbackId() {
        String value = prefs.getString(KEY_DEVICE_FALLBACK_ID, "");
        return value == null ? "" : value.trim();
    }

    public void setDeviceFallbackId(String value) {
        prefs.edit().putString(KEY_DEVICE_FALLBACK_ID,
                value == null ? "" : value.trim()).apply();
    }

    public String getDeviceToken() {
        String value = prefs.getString(KEY_DEVICE_TOKEN, "");
        return value == null ? "" : value.trim();
    }

    public void setDeviceToken(String token) {
        prefs.edit().putString(KEY_DEVICE_TOKEN,
                token == null ? "" : token.trim()).apply();
    }

    public String getRegisteredDeviceHash() {
        String value = prefs.getString(KEY_REGISTERED_DEVICE_HASH, "");
        return value == null ? "" : value.trim();
    }

    public void setRegisteredDeviceHash(String hash) {
        prefs.edit().putString(KEY_REGISTERED_DEVICE_HASH,
                hash == null ? "" : hash.trim()).apply();
    }

    public String getSessionToken() {
        String value = prefs.getString(KEY_SESSION_TOKEN, "");
        return value == null ? "" : value.trim();
    }

    public long getSessionExpiresAt() {
        return prefs.getLong(KEY_SESSION_EXPIRES_AT, 0L);
    }

    public long getSessionLastCheck() {
        return prefs.getLong(KEY_SESSION_LAST_CHECK, 0L);
    }

    public long getLicenseExpiresAt() {
        return prefs.getLong(KEY_LICENSE_EXPIRES_AT, 0L);
    }

    public void setSession(String token, long expiresAt) {
        prefs.edit()
                .putString(KEY_SESSION_TOKEN, token == null ? "" : token.trim())
                .putLong(KEY_SESSION_EXPIRES_AT, expiresAt)
                .putLong(KEY_SESSION_LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    public void setSessionExpiry(long expiresAt) {
        prefs.edit()
                .putLong(KEY_SESSION_EXPIRES_AT, expiresAt)
                .putLong(KEY_SESSION_LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    public void setLicenseExpiry(long expiresAt) {
        prefs.edit().putLong(KEY_LICENSE_EXPIRES_AT, expiresAt).apply();
    }

    public void clearSession() {
        prefs.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_SESSION_EXPIRES_AT)
                .remove(KEY_SESSION_LAST_CHECK)
                .remove(KEY_LICENSE_EXPIRES_AT)
                .apply();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme == null ? THEME_SYSTEM : theme).apply();
    }

    public int getPlaybackPosition(String uri) {
        if (uri == null || !uri.equals(prefs.getString(KEY_PLAYBACK_URI, ""))) return 0;
        return Math.max(0, prefs.getInt(KEY_PLAYBACK_POSITION, 0));
    }

    public void setPlaybackPosition(String uri, int positionMs) {
        if (uri == null || uri.trim().isEmpty()) return;
        prefs.edit()
                .putString(KEY_PLAYBACK_URI, uri)
                .putInt(KEY_PLAYBACK_POSITION, Math.max(0, positionMs))
                .apply();
    }

    public int getPlaybackRepeat() {
        return Math.max(0, Math.min(2, prefs.getInt(KEY_PLAYBACK_REPEAT, 0)));
    }

    public void setPlaybackRepeat(int mode) {
        prefs.edit().putInt(KEY_PLAYBACK_REPEAT,
                Math.max(0, Math.min(2, mode))).apply();
    }

    public boolean isAutoHd() {
        return prefs.getBoolean(KEY_AUTO_HD, true);
    }

    public void setAutoHd(boolean value) {
        prefs.edit().putBoolean(KEY_AUTO_HD, value).apply();
    }

    public String getDefaultVideo() {
        String value = prefs.getString(KEY_DEFAULT_VIDEO, VIDEO_HD);
        if (VIDEO_HD.equals(value) || VIDEO_STANDARD.equals(value)
                || VIDEO_AUDIO.equals(value) || VIDEO_COVER.equals(value)) return value;
        if (VIDEO_BEST.equals(value) || VIDEO_WM.equals(value)) return VIDEO_HD;
        return VIDEO_HD;
    }

    public void setDefaultVideo(String value) {
        String normalized = value == null ? "" : value.trim();
        if (VIDEO_WM.equals(normalized)) normalized = VIDEO_STANDARD;
        if (VIDEO_BEST.equals(normalized)) normalized = VIDEO_HD;
        if (!VIDEO_HD.equals(normalized) && !VIDEO_STANDARD.equals(normalized)
                && !VIDEO_AUDIO.equals(normalized) && !VIDEO_COVER.equals(normalized)) {
            normalized = VIDEO_HD;
        }
        prefs.edit().putString(KEY_DEFAULT_VIDEO, normalized).apply();
    }

    public String getDefaultAudio() {
        return prefs.getString(KEY_DEFAULT_AUDIO, AUDIO_MUSIC);
    }

    public void setDefaultAudio(String value) {
        prefs.edit().putString(KEY_DEFAULT_AUDIO,
                value == null || value.trim().isEmpty() ? AUDIO_MUSIC : value.trim()).apply();
    }

    public String getDefaultPhotos() {
        String value = prefs.getString(KEY_DEFAULT_PHOTOS, PHOTO_ONLY);
        if (PHOTO_ONLY.equals(value) || PHOTO_SLIDESHOW.equals(value)
                || PHOTO_AUDIO.equals(value)) return value;
        if ("photos_audio".equals(value)) return PHOTO_SLIDESHOW;
        return PHOTO_ONLY;
    }

    public void setDefaultPhotos(String value) {
        String normalized = value == null ? "" : value.trim();
        if ("photos_audio".equals(normalized)) normalized = PHOTO_SLIDESHOW;
        if (!PHOTO_ONLY.equals(normalized) && !PHOTO_SLIDESHOW.equals(normalized)
                && !PHOTO_AUDIO.equals(normalized)) {
            normalized = PHOTO_ONLY;
        }
        prefs.edit().putString(KEY_DEFAULT_PHOTOS, normalized).apply();
    }

    public String getFolderMode() {
        String mode = prefs.getString(KEY_FOLDER_MODE, "");
        if (FOLDER_FLAT.equals(mode) || FOLDER_CREATOR.equals(mode)) return mode;
        return prefs.getBoolean(KEY_CREATOR_FOLDERS, false) ? FOLDER_CREATOR : FOLDER_FLAT;
    }

    public void setFolderMode(String mode) {
        String value = FOLDER_CREATOR.equals(mode) ? FOLDER_CREATOR : FOLDER_FLAT;
        prefs.edit()
                .putString(KEY_FOLDER_MODE, value)
                .putBoolean(KEY_CREATOR_FOLDERS, FOLDER_CREATOR.equals(value))
                .apply();
    }

    public boolean isCreatorFolders() {
        return FOLDER_CREATOR.equals(getFolderMode());
    }

    public void setCreatorFolders(boolean value) {
        setFolderMode(value ? FOLDER_CREATOR : FOLDER_FLAT);
    }

    public boolean isAutoDetect() {
        return prefs.getBoolean(KEY_AUTO_DETECT, true);
    }

    public void setAutoDetect(boolean value) {
        prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply();
    }

    public String getDownloadPath() {
        String value = prefs.getString(KEY_DOWNLOAD_PATH, "");
        return value == null ? "" : value.trim();
    }

    public void setDownloadPath(String value) {
        prefs.edit().putString(KEY_DOWNLOAD_PATH, value == null ? "" : value.trim()).apply();
    }

    public int getMaxTasks() {
        return Math.max(1, Math.min(5, prefs.getInt(KEY_MAX_TASKS, 3)));
    }

    public void setMaxTasks(int value) {
        prefs.edit().putInt(KEY_MAX_TASKS, Math.max(1, Math.min(5, value))).apply();
    }

    public int getSpeedLimitKbps() {
        return Math.max(0, prefs.getInt(KEY_SPEED_LIMIT, 0));
    }

    public void setSpeedLimitKbps(int value) {
        prefs.edit().putInt(KEY_SPEED_LIMIT, Math.max(0, value)).apply();
    }

    public String getNetworkPolicy() {
        return prefs.getString(KEY_NETWORK, NETWORK_ANY);
    }

    public void setNetworkPolicy(String value) {
        prefs.edit().putString(KEY_NETWORK, value == null ? NETWORK_ANY : value).apply();
    }

    public boolean isNotifyProgress() {
        return prefs.getBoolean(KEY_NOTIFY_PROGRESS, true);
    }

    public void setNotifyProgress(boolean value) {
        prefs.edit().putBoolean(KEY_NOTIFY_PROGRESS, value).apply();
    }

    public boolean isNotifyComplete() {
        return prefs.getBoolean(KEY_NOTIFY_COMPLETE, true);
    }

    public void setNotifyComplete(boolean value) {
        prefs.edit().putBoolean(KEY_NOTIFY_COMPLETE, value).apply();
    }

    public boolean isShowDownloadOptions() {
        return prefs.getBoolean(KEY_SHOW_DOWNLOAD_OPTIONS, true);
    }

    public void setShowDownloadOptions(boolean value) {
        prefs.edit().putBoolean(KEY_SHOW_DOWNLOAD_OPTIONS, value).apply();
    }

    public boolean isToastDownloadStart() {
        return prefs.getBoolean(KEY_TOAST_DOWNLOAD_START, true);
    }

    public void setToastDownloadStart(boolean value) {
        prefs.edit().putBoolean(KEY_TOAST_DOWNLOAD_START, value).apply();
    }

    public boolean isToastDownloadEnd() {
        return prefs.getBoolean(KEY_TOAST_DOWNLOAD_END, true);
    }

    public void setToastDownloadEnd(boolean value) {
        prefs.edit().putBoolean(KEY_TOAST_DOWNLOAD_END, value).apply();
    }

    public boolean isNotifyEmbedStart() {
        return prefs.getBoolean(KEY_NOTIFY_EMBED_START, true);
    }

    public void setNotifyEmbedStart(boolean value) {
        prefs.edit().putBoolean(KEY_NOTIFY_EMBED_START, value).apply();
    }

    public boolean isNotifyEmbedFinished() {
        return prefs.getBoolean(KEY_NOTIFY_EMBED_FINISHED, true);
    }

    public void setNotifyEmbedFinished(boolean value) {
        prefs.edit().putBoolean(KEY_NOTIFY_EMBED_FINISHED, value).apply();
    }

    public boolean isToastEmbedStart() {
        return prefs.getBoolean(KEY_TOAST_EMBED_START, true);
    }

    public void setToastEmbedStart(boolean value) {
        prefs.edit().putBoolean(KEY_TOAST_EMBED_START, value).apply();
    }

    public boolean isToastEmbedFinished() {
        return prefs.getBoolean(KEY_TOAST_EMBED_FINISHED, true);
    }

    public void setToastEmbedFinished(boolean value) {
        prefs.edit().putBoolean(KEY_TOAST_EMBED_FINISHED, value).apply();
    }

    public boolean isCaptionsEnabled() {
        return prefs.getBoolean(KEY_CAPTIONS_ENABLED, false);
    }

    public void setCaptionsEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_CAPTIONS_ENABLED, value).apply();
    }

    public String getCaptionMode() {
        return CAPTION_SRT;
    }

    public String getRegionCode() {
        String value = prefs.getString(KEY_REGION_CODE, "");
        return value == null ? "" : value;
    }

    public String getRegionName() {
        String value = prefs.getString(KEY_REGION_NAME, "");
        return value == null ? "" : value;
    }

    public void setRegion(String code, String name) {
        prefs.edit()
                .putString(KEY_REGION_CODE, code == null ? "" : code.toUpperCase(java.util.Locale.US))
                .putString(KEY_REGION_NAME, name == null ? "" : name)
                .apply();
    }

    public String getCustomResolver() {
        return prefs.getString(KEY_CUSTOM_RESOLVER, "");
    }

    public void setCustomResolver(String value) {
        prefs.edit().putString(KEY_CUSTOM_RESOLVER, value == null ? "" : value.trim()).apply();
    }

    public String getResolverKey() {
        return prefs.getString(KEY_RESOLVER_KEY, "");
    }

    public void setResolverKey(String value) {
        prefs.edit().putString(KEY_RESOLVER_KEY, value == null ? "" : value.trim()).apply();
    }

    public boolean isStayInApp() {
        return prefs.getBoolean(KEY_STAY_IN_APP, true);
    }

    public void setStayInApp(boolean value) {
        prefs.edit().putBoolean(KEY_STAY_IN_APP, value).apply();
    }

    public String getLastClipboard() {
        return prefs.getString(KEY_LAST_CLIPBOARD, "");
    }

    public void setLastClipboard(String value) {
        prefs.edit().putString(KEY_LAST_CLIPBOARD, value == null ? "" : value).apply();
    }

    public String getLastCopiedLink() {
        return prefs.getString(KEY_LAST_COPIED_LINK, "");
    }

    public void setLastCopiedLink(String value) {
        prefs.edit().putString(KEY_LAST_COPIED_LINK, value == null ? "" : value).apply();
    }

    public String getLastClipboardSeen() {
        return prefs.getString(KEY_LAST_CLIPBOARD_SEEN, "");
    }

    public void setLastClipboardSeen(String value) {
        prefs.edit().putString(KEY_LAST_CLIPBOARD_SEEN, value == null ? "" : value).apply();
    }

    public long getLastClipboardTimestamp() {
        return prefs.getLong(KEY_LAST_CLIPBOARD_TIMESTAMP, 0L);
    }

    public void setLastClipboardTimestamp(long value) {
        prefs.edit().putLong(KEY_LAST_CLIPBOARD_TIMESTAMP, value).apply();
    }

    public void setFloatingDownload(String copiedLink, String pageLink) {
        prefs.edit()
                .putString(KEY_FLOATING_COPIED_LINK, copiedLink == null ? "" : copiedLink)
                .putString(KEY_FLOATING_PAGE_LINK, pageLink == null ? "" : pageLink)
                .apply();
    }

    public void clearFloatingDownload(String pageLink) {
        String pending = prefs.getString(KEY_FLOATING_PAGE_LINK, "");
        if (pending.isEmpty() || pageLink == null || !pending.equals(pageLink)) return;
        prefs.edit()
                .remove(KEY_FLOATING_PAGE_LINK)
                .remove(KEY_FLOATING_COPIED_LINK)
                .apply();
    }

    public boolean completeFloatingDownload(String pageLink) {
        String pendingPage = prefs.getString(KEY_FLOATING_PAGE_LINK, "");
        if (pendingPage.isEmpty() || pageLink == null || !pendingPage.equals(pageLink)) return false;
        String pendingCopied = prefs.getString(KEY_FLOATING_COPIED_LINK, "");
        SharedPreferences.Editor editor = prefs.edit()
                .remove(KEY_FLOATING_PAGE_LINK)
                .remove(KEY_FLOATING_COPIED_LINK);
        if (!pendingCopied.isEmpty() && pendingCopied.equals(getLastCopiedLink())) {
            editor.remove(KEY_LAST_COPIED_LINK);
        }
        editor.apply();
        return true;
    }

    public float getFloatX() {
        return prefs.getFloat(KEY_FLOAT_X, -1f);
    }

    public float getFloatY() {
        return prefs.getFloat(KEY_FLOAT_Y, -1f);
    }

    public void setFloatPosition(float x, float y) {
        prefs.edit().putFloat(KEY_FLOAT_X, x).putFloat(KEY_FLOAT_Y, y).apply();
    }

    public float getFloatScale() {
        return prefs.getFloat(KEY_FLOAT_SCALE, 1f);
    }

    public void setFloatScale(float value) {
        prefs.edit().putFloat(KEY_FLOAT_SCALE, value).apply();
    }

    public float getFloatOpacity() {
        return prefs.getFloat(KEY_FLOAT_OPACITY, 1f);
    }

    public void setFloatOpacity(float value) {
        prefs.edit().putFloat(KEY_FLOAT_OPACITY, value).apply();
    }
}
