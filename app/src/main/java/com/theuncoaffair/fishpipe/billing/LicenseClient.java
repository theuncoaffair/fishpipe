package com.theuncoaffair.fishpipe.billing;

import com.theuncoaffair.fishpipe.BuildConfig;
import com.theuncoaffair.fishpipe.net.Http;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class LicenseClient {
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 25000;

    private LicenseClient() {
    }

    public static boolean isConfigured() {
        String base = baseUrl();
        return base.startsWith("https://");
    }

    public static Result register(String deviceHash) {
        try {
            JSONObject body = new JSONObject().put("device_hash", deviceHash);
            Result result = post("/api/v1/device/register", body, "");
            if (result.ok) {
                result.deviceToken = result.data.optString("device_token", "");
                result.used = result.data.optInt("used", 0);
                if (result.deviceToken.isEmpty()) {
                    return Result.error("server_error", "Could not register this device.");
                }
            }
            return result;
        } catch (Exception e) {
            return Result.error("network", message(e));
        }
    }

    public static Result usage(String deviceToken, String deviceHash) {
        try {
            Map<String, String> headers = headers(deviceToken);
            Http.Response response = Http.get(
                    url("/api/v1/usage?device_hash=" + deviceHash),
                    headers, CONNECT_TIMEOUT, READ_TIMEOUT);
            return Result.from(response);
        } catch (Exception e) {
            return Result.error("network", message(e));
        }
    }

    public static Result consume(String deviceToken, String deviceHash, String jobId) {
        try {
            JSONObject body = new JSONObject()
                    .put("device_hash", deviceHash)
                    .put("job_id", jobId);
            return post("/api/v1/usage/consume", body, deviceToken);
        } catch (Exception e) {
            return Result.error("network", message(e));
        }
    }

    public static Result requestCode(String email, String deviceHash) {
        try {
            JSONObject body = new JSONObject()
                    .put("email", normalizeEmail(email))
                    .put("device_hash", deviceHash);
            return post("/api/v1/auth/request-code", body, "");
        } catch (Exception e) {
            return Result.error("network", message(e));
        }
    }

    public static Result verifyCode(String email, String code, String deviceHash) {
        try {
            JSONObject body = new JSONObject()
                    .put("email", normalizeEmail(email))
                    .put("code", code == null ? "" : code.trim())
                    .put("device_hash", deviceHash);
            Result result = post("/api/v1/auth/verify-code", body, "");
            if (result.ok) {
                result.status = result.data.optString("status", "");
                result.email = result.data.optString("email", normalizeEmail(email));
                result.sessionToken = result.data.optString("session_token", "");
                result.expiresAt = parseIso(result.data.optString("expires_at", ""));
                result.licenseExpiresAt = parseIso(
                        result.data.optString("license_expires_at", ""));
                if (result.sessionToken.isEmpty()) {
                    return Result.error("server_error", "Activation did not return a session.");
                }
            }
            return result;
        } catch (Exception e) {
            return Result.error("network", message(e));
        }
    }

    public static Result validateSession(String sessionToken, String deviceHash) {
        try {
            JSONObject body = new JSONObject().put("device_hash", deviceHash);
            Result result = post("/api/v1/session/validate", body, sessionToken);
            if (result.ok) {
                result.expiresAt = parseIso(result.data.optString("expires_at", ""));
                result.licenseExpiresAt = parseIso(
                        result.data.optString("license_expires_at", ""));
            }
            return result;
        } catch (Exception e) {
            return Result.error("network", message(e));
        }
    }

    private static Result post(String path, JSONObject body, String bearer) {
        if (!isConfigured()) {
            return Result.error("not_configured", "Activation is not configured yet.");
        }
        try {
            Http.Response response = Http.request(
                    url(path), "POST",
                    body.toString().getBytes(StandardCharsets.UTF_8),
                    headers(bearer), CONNECT_TIMEOUT, READ_TIMEOUT);
            return Result.from(response);
        } catch (Exception e) {
            return Result.error("network", message(e));
        }
    }

    private static Map<String, String> headers(String bearer) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        if (bearer != null && !bearer.trim().isEmpty()) {
            headers.put("Authorization", "Bearer " + bearer.trim());
        }
        return headers;
    }

    private static String baseUrl() {
        String base = BuildConfig.LICENSE_API_BASE_URL == null
                ? "" : BuildConfig.LICENSE_API_BASE_URL.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static String url(String path) {
        return baseUrl() + path;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.US);
    }

    private static String message(Exception e) {
        String text = e == null ? "" : e.getMessage();
        return text == null || text.trim().isEmpty() ? "Network error. Try again." : text;
    }

    private static long parseIso(String value) {
        if (value == null) return 0L;
        String text = value.trim();
        if (text.isEmpty()) return 0L;
        if (text.endsWith("Z")) text = text.substring(0, text.length() - 1);
        int dot = text.indexOf('.');
        if (dot >= 0) text = text.substring(0, dot);
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(text);
            return date == null ? 0L : date.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static final class Result {
        public boolean ok;
        public String code = "";
        public String message = "";
        public String status = "";
        public String email = "";
        public String deviceToken = "";
        public String sessionToken = "";
        public long expiresAt;
        public long licenseExpiresAt;
        public int used;
        public boolean allowed;
        public JSONObject data = new JSONObject();

        static Result from(Http.Response response) {
            Result result = new Result();
            String body = response == null || response.body == null ? "{}" : response.body;
            try {
                JSONObject json = new JSONObject(body);
                if (response != null && response.code >= 200 && response.code < 300
                        && json.optBoolean("ok", false)) {
                    result.ok = true;
                    result.data = json.optJSONObject("data");
                    if (result.data == null) result.data = new JSONObject();
                    result.used = result.data.optInt("used", result.used);
                    result.allowed = result.data.optBoolean("allowed", result.allowed);
                } else {
                    JSONObject error = json.optJSONObject("error");
                    if (error != null) {
                        result.code = error.optString("code", "");
                        result.message = error.optString("message", "");
                    }
                    if (result.message.isEmpty()) {
                        result.message = "Request failed (" + (response == null ? 0 : response.code) + ").";
                    }
                }
            } catch (Exception ignored) {
                result.message = "Server returned an invalid response.";
            }
            return result;
        }

        static Result error(String code, String message) {
            Result result = new Result();
            result.ok = false;
            result.code = code == null ? "" : code;
            result.message = message == null ? "" : message;
            return result;
        }
    }
}
