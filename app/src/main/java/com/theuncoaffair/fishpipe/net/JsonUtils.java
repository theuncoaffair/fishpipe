package com.theuncoaffair.fishpipe.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtils {
    private static final Pattern SCRIPT = Pattern.compile(
            "(?is)<script[^>]*id=[\"']([^\"']+)[\"'][^>]*>(.*?)</script>");
    private static final Pattern JSON_SCRIPT = Pattern.compile(
            "(?is)<script[^>]*type=[\"']application/json[\"'][^>]*>(.*?)</script>");
    private static final Pattern FIELD = Pattern.compile(
            "\"(playAddr|downloadAddr|cover|originCover|dynamicCover|desc|uniqueId|nickname|id|title)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private JsonUtils() {
    }

    public static String clean(String value) {
        if (value == null) return "";
        return value.replace("\\u002F", "/")
                .replace("\\u002f", "/")
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("\\u0026", "&")
                .replace("\\u003D", "=")
                .replace("\\u003d", "=")
                .trim();
    }

    public static String string(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            Object value = object.opt(key);
            String out = valueString(value);
            if (!out.isEmpty()) return out;
        }
        return "";
    }

    private static String valueString(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String) return clean((String) value);
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            String out = string(obj, "url", "Url", "url_list", "UrlList", "playUrl", "play_url");
            if (!out.isEmpty()) return out;
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String out = valueString(array.opt(i));
                if (!out.isEmpty()) return out;
            }
        }
        return "";
    }

    public static JSONObject object(JSONObject parent, String... keys) {
        if (parent == null) return null;
        for (String key : keys) {
            JSONObject value = parent.optJSONObject(key);
            if (value != null) return value;
        }
        return null;
    }

    public static List<String> scriptJson(String html, String preferredId) {
        List<String> out = new ArrayList<>();
        Matcher m = SCRIPT.matcher(html);
        while (m.find()) {
            String id = m.group(1).toLowerCase(java.util.Locale.ROOT);
            if (id.contains("universal") || id.contains("sigi") || id.contains("next") || id.contains("initial")) {
                out.add(m.group(2));
            }
        }
        Matcher generic = JSON_SCRIPT.matcher(html);
        while (generic.find()) {
            String value = generic.group(1).trim();
            if (value.startsWith("{") && (value.contains("\"video\"") || value.contains("\"itemStruct\""))) {
                out.add(value);
            }
        }
        return out;
    }

    public static JSONObject findVideoObject(JSONObject root, String preferredId) {
        if (root == null) return null;
        List<JSONObject> queue = new ArrayList<>();
        queue.add(root);
        int guard = 0;
        JSONObject fallback = null;
        while (!queue.isEmpty() && guard++ < 4000) {
            JSONObject current = queue.remove(0);
            JSONObject video = current.optJSONObject("video");
            if (video != null && hasVideoData(video)) {
                String id = current.optString("id", "");
                if (!preferredId.isEmpty() && preferredId.equals(id)) return current;
                if (fallback == null) fallback = current;
            }
            Iterator<String> keys = current.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = current.opt(key);
                if (value instanceof JSONObject) {
                    queue.add((JSONObject) value);
                } else if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    for (int i = 0; i < array.length(); i++) {
                        Object child = array.opt(i);
                        if (child instanceof JSONObject) queue.add((JSONObject) child);
                    }
                }
            }
        }
        return fallback;
    }

    public static JSONObject findImageObject(JSONObject root) {
        if (root == null) return null;
        List<JSONObject> queue = new ArrayList<>();
        queue.add(root);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 4000) {
            JSONObject current = queue.remove(0);
            JSONObject post = current.optJSONObject("imagePost");
            if (post == null) post = current.optJSONObject("image_post");
            if (post != null) {
                JSONArray images = post.optJSONArray("images");
                if (images != null && images.length() > 0) return current;
            }
            Iterator<String> keys = current.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = current.opt(key);
                if (value instanceof JSONObject) {
                    queue.add((JSONObject) value);
                } else if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    for (int i = 0; i < array.length(); i++) {
                        Object child = array.opt(i);
                        if (child instanceof JSONObject) queue.add((JSONObject) child);
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasVideoData(JSONObject video) {
        return video.has("playAddr") || video.has("play_addr")
                || video.has("downloadAddr") || video.has("download_addr")
                || video.has("bitrateInfo") || video.has("bitrate_info");
    }

    public static String regexField(String html, String key) {
        Matcher m = FIELD.matcher(html);
        while (m.find()) {
            if (m.group(1).equals(key)) return unescape(m.group(2));
        }
        return "";
    }

    public static String unescape(String raw) {
        try {
            return new JSONObject("{\"v\":\"" + raw + "\"}").getString("v");
        } catch (Exception e) {
            return clean(raw);
        }
    }

    public static String bestBitrate(JSONObject video) {
        JSONArray array = video.optJSONArray("bitrateInfo");
        if (array == null) array = video.optJSONArray("bitrate_info");
        if (array == null) array = video.optJSONArray("bit_rate");
        if (array == null) array = video.optJSONArray("bitRateList");
        if (array == null) return "";
        String best = "";
        long bestRate = -1;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            long rate = item.optLong("Bitrate", item.optLong("bit_rate", 0));
            Object play = item.opt("PlayAddr");
            if (play == null) play = item.opt("play_addr");
            if (play == null) play = item.opt("playAddr");
            String url = valueString(play);
            if (!url.isEmpty() && rate >= bestRate) {
                bestRate = rate;
                best = url;
            }
        }
        return best;
    }
}
