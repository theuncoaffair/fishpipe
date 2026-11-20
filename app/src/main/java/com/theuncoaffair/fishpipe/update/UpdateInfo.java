package com.theuncoaffair.fishpipe.update;

import com.theuncoaffair.fishpipe.net.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateInfo {
    public final int versionCode;
    public final String versionName;
    public final String apkUrl;
    public final String sha256;
    public final String whatsNew;
    public final String bugFixes;

    private UpdateInfo(int versionCode, String versionName, String apkUrl, String sha256,
                       String whatsNew, String bugFixes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.whatsNew = whatsNew;
        this.bugFixes = bugFixes;
    }

    private static final Pattern CHECKSUM = Pattern.compile(
            "(?i)(?:sha-?256|checksum)\\s*[:=]\\s*([a-f0-9]{64})");

    public static UpdateInfo load(String manifestUrl) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        Http.Response response = Http.get(manifestUrl, headers, 15000, 20000);
        if (response.code < 200 || response.code >= 300) {
            throw new IllegalStateException("Update manifest returned " + response.code);
        }
        JSONObject json = new JSONObject(response.body);
        if (json.has("assets")) return fromRelease(json);
        return new UpdateInfo(
                json.optInt("versionCode", 0),
                json.optString("versionName", ""),
                first(json, "apkUrl", "downloadUrl", "url"),
                first(json, "sha256", "sha256sum"),
                first(json, "whatsNew", "whats_new", "releaseNotes"),
                first(json, "bugFixes", "bug_fixes"));
    }

    private static UpdateInfo fromRelease(JSONObject json) {
        String tag = json.optString("tag_name", json.optString("name", "")).trim();
        String versionName = tag.startsWith("v") || tag.startsWith("V")
                ? tag.substring(1) : tag;
        int versionCode = json.optInt("version_code",
                json.optInt("versionCode", versionCode(versionName)));
        String apkUrl = "";
        String sha256 = "";
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (!name.endsWith(".apk")) continue;
                String url = asset.optString("browser_download_url", "").trim();
                if (url.isEmpty()) continue;
                apkUrl = url;
                String digest = asset.optString("digest", "").trim();
                if (digest.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
                    sha256 = digest.substring(7).trim();
                } else {
                    sha256 = first(asset, "sha256", "sha256sum");
                }
                if (name.equals("fishpipe-release.apk")) break;
            }
        }
        String body = json.optString("body", "");
        String whatsNew = section(body, "what's new", "whats new", "what’s new");
        String bugFixes = section(body, "bug fixes", "bugfixes");
        if (whatsNew.isEmpty() && bugFixes.isEmpty()) whatsNew = body;
        if (sha256.isEmpty()) sha256 = checksum(body);
        return new UpdateInfo(versionCode, versionName, apkUrl, sha256, whatsNew, bugFixes);
    }

    private static int versionCode(String versionName) {
        String[] parts = versionName.split("[^0-9]+");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                try {
                    return Integer.parseInt(parts[i]);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String section(String body, String... names) {
        StringBuilder out = new StringBuilder();
        boolean inside = false;
        for (String line : body.split("\\r?\\n", -1)) {
            String heading = line.replaceFirst("^#{1,6}\\s+", "").trim().toLowerCase(Locale.ROOT);
            if (!heading.equals(line.trim().toLowerCase(Locale.ROOT))) {
                boolean match = false;
                for (String name : names) {
                    if (heading.equals(name)) {
                        match = true;
                        break;
                    }
                }
                if (match) {
                    inside = true;
                    continue;
                }
                if (inside) break;
            }
            if (inside) out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private static String checksum(String body) {
        Matcher matcher = CHECKSUM.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String first(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
