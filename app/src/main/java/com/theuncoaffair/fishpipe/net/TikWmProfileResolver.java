package com.theuncoaffair.fishpipe.net;

import com.theuncoaffair.fishpipe.data.MediaItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TikWmProfileResolver {
    private static final String[] ENDPOINTS = {
            "https://www.tikwm.com/api/user/posts?unique_id=%s&count=30&cursor=%s",
            "https://tikwm.com/api/user/posts?unique_id=%s&count=30&cursor=%s"
    };
    private static final int MAX_PAGES = 100;
    private static final int PAGE_ATTEMPTS = 1;

    private static final Pattern READER_ITEM = Pattern.compile(
            "\\[!\\[Image[^\\]]*?: (.*?)\\]\\((https?://[^)]+)\\)"
                    + "(?:\\s+\\[Video[^\\]]*\\]\\(blob:[^)]+\\))?"
                    + "(?:\\s+\\*\\*([^*]+)\\*\\*)?"
                    + "\\]\\((https://www\\.tiktok\\.com/@[^/]+/video/(\\d+))\\)");

    public interface PageListener {
        void onPage(List<MediaItem> items);
    }

    public List<MediaItem> fetch(String username) throws Exception {
        return fetch(username, null);
    }

    public List<MediaItem> fetch(String username, PageListener listener) throws Exception {
        String handle = clean(username);
        if (handle.isEmpty()) throw new ResolveException("Creator handle is empty");
        Exception last = null;
        try {
            List<MediaItem> items = fetchAll(handle, listener);
            if (!items.isEmpty()) return items;
        } catch (Exception e) {
            last = e;
        }
        try {
            List<MediaItem> items = fetchReader(handle);
            if (!items.isEmpty()) {
                if (listener != null) listener.onPage(items);
                return items;
            }
        } catch (Exception e) {
            last = e;
        }
        throw new ResolveException("Creator videos unavailable", last);
    }

    private List<MediaItem> fetchAll(String handle, PageListener listener) throws Exception {
        Map<String, MediaItem> items = new LinkedHashMap<>();
        Set<String> cursors = new HashSet<>();
        String cursor = "0";
        cursors.add(cursor);
        Exception last = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            JSONObject data = null;
            Exception pageError = null;
            for (int attempt = 0; attempt < PAGE_ATTEMPTS && data == null; attempt++) {
                for (String template : ENDPOINTS) {
                    try {
                        data = request(String.format(template, Http.encode(handle),
                                Http.encode(cursor)), handle);
                        break;
                    } catch (Exception e) {
                        pageError = e;
                    }
                }
                if (data == null && attempt + 1 < PAGE_ATTEMPTS) sleep(250);
            }
            if (data == null) {
                last = pageError;
                break;
            }
            JSONArray videos = videos(data);
            if (videos == null || videos.length() == 0) break;
            List<MediaItem> fresh = new ArrayList<>();
            for (int i = 0; i < videos.length(); i++) {
                JSONObject raw = videos.optJSONObject(i);
                if (raw == null) continue;
                JSONObject video = raw.optJSONObject("item");
                if (video == null) video = raw.optJSONObject("aweme");
                if (video == null) video = raw;
                try {
                    MediaItem item = TikWmResolver.parse(video, "");
                    if (!item.hasVideo()) continue;
                    if (item.hasImages()) item.images.clear();
                    if (item.author == null || item.author.isEmpty()) item.author = handle;
                    if (item.pageUrl == null || item.pageUrl.isEmpty()) {
                        item.pageUrl = MediaItem.notEmpty(item.id)
                                ? "https://www.tiktok.com/@" + handle + "/video/" + item.id
                                : "https://www.tiktok.com/@" + handle;
                    }
                    String key = MediaItem.notEmpty(item.id) ? item.id : item.pageUrl;
                    if (!items.containsKey(key)) {
                        items.put(key, item);
                        fresh.add(item);
                    }
                } catch (Exception ignored) {
                }
            }
            if (fresh.isEmpty()) break;
            if (listener != null) listener.onPage(fresh);
            String next = nextCursor(data);
            if (next.isEmpty() || next.equals(cursor) || cursors.contains(next)) break;
            cursors.add(next);
            cursor = next;
        }
        if (items.isEmpty() && last != null) throw last;
        return new ArrayList<>(items.values());
    }

    private JSONObject request(String url, String handle) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json,text/plain,*/*");
        headers.put("User-Agent", Http.MOBILE_UA);
        headers.put("Referer", "https://www.tiktok.com/@" + handle);
        Http.Response response = Http.get(url, headers, 8000, 12000);
        if (!response.ok()) throw new ResolveException("Profile resolver returned HTTP " + response.code);

        JSONObject root = new JSONObject(response.body);
        int code = root.optInt("code", -1);
        if (code != 0) {
            String message = root.optString("msg", "Profile resolver error");
            throw new ResolveException(message);
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new ResolveException("Profile resolver returned no data");
        return data;
    }

    private static JSONArray videos(JSONObject data) {
        JSONArray videos = data.optJSONArray("videos");
        if (videos == null) videos = data.optJSONArray("aweme_list");
        if (videos == null) videos = data.optJSONArray("itemList");
        if (videos == null) videos = data.optJSONArray("items");
        return videos;
    }

    private static String nextCursor(JSONObject data) {
        String next = data.optString("cursor", "");
        if (next.isEmpty()) next = data.optString("max_cursor", "");
        if (next.isEmpty()) next = data.optString("maxCursor", "");
        next = next.trim();
        if ("null".equalsIgnoreCase(next)) return "";
        return next;
    }

    private List<MediaItem> fetchReader(String handle) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/plain,text/markdown,*/*");
        headers.put("User-Agent", "Mozilla/5.0");
        Http.Response response = Http.get(
                "https://r.jina.ai/https://www.tiktok.com/@" + Http.encode(handle),
                headers, 8000, 12000);
        if (!response.ok()) throw new ResolveException("Profile reader returned HTTP " + response.code);
        List<MediaItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher matcher = READER_ITEM.matcher(response.body);
        while (matcher.find()) {
            String pageUrl = matcher.group(4);
            if (!seen.add(pageUrl)) continue;
            MediaItem item = new MediaItem();
            item.source = "tiktok";
            item.author = handle;
            item.id = matcher.group(5);
            item.pageUrl = pageUrl;
            item.title = matcher.group(1).replaceFirst("^Image\\s+\\d+:\\s*", "");
            item.coverUrl = matcher.group(2);
            item.diggCount = count(matcher.group(3));
            items.add(item);
        }
        if (items.isEmpty()) throw new ResolveException("Profile reader returned no videos");
        return items;
    }

    private static long count(String value) {
        String text = value == null ? "" : value.trim().replace(",", "").toUpperCase(Locale.ROOT);
        try {
            if (text.endsWith("K")) return (long) (Double.parseDouble(text.substring(0, text.length() - 1)) * 1000);
            if (text.endsWith("M")) return (long) (Double.parseDouble(text.substring(0, text.length() - 1)) * 1000000);
            if (text.endsWith("B")) return (long) (Double.parseDouble(text.substring(0, text.length() - 1)) * 1000000000);
            return Long.parseLong(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String clean(String value) {
        String handle = value == null ? "" : value.trim();
        if (handle.startsWith("@")) handle = handle.substring(1);
        int cut = handle.indexOf('?');
        if (cut >= 0) handle = handle.substring(0, cut);
        cut = handle.indexOf('/');
        if (cut >= 0) handle = handle.substring(0, cut);
        return handle;
    }
}
