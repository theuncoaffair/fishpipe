package com.theuncoaffair.fishpipe.net;

import com.theuncoaffair.fishpipe.data.MediaItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class CustomResolver {
    private final String endpoint;
    private final String key;

    public CustomResolver(String endpoint, String key) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.key = key == null ? "" : key.trim();
    }

    public boolean isConfigured() {
        return !endpoint.isEmpty();
    }

    public boolean isSecure() {
        return endpoint.regionMatches(true, 0, "https://", 0, 8);
    }

    public MediaItem resolve(String input) throws Exception {
        if (!isConfigured()) throw new ResolveException("No custom resolver configured");
        if (!isSecure()) throw new ResolveException("Custom resolver must use HTTPS");
        String target = LinkUtils.normalize(input);
        if (target.isEmpty()) target = input;
        String url = endpoint.contains("{url}")
                ? endpoint.replace("{url}", Http.encode(target))
                : endpoint + (endpoint.contains("?") ? "&" : "?") + "url=" + Http.encode(target);
        if (endpoint.contains("{key}")) {
            url = url.replace("{key}", Http.encode(key));
        } else if (!key.isEmpty()) {
            url += "&key=" + Http.encode(key);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json,text/plain,*/*");
        headers.put("User-Agent", Http.MOBILE_UA);
        Http.Response response = Http.get(url, headers);
        if (!response.ok()) throw new ResolveException("Resolver returned HTTP " + response.code);

        String body = response.body.trim();
        if (body.startsWith("http://") || body.startsWith("https://")) {
            MediaItem item = new MediaItem();
            item.id = LinkUtils.extractId(target);
            item.title = "";
            item.videoUrl = body;
            item.pageUrl = target;
            item.source = "custom";
            return item;
        }
        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        if (data == null) data = root.optJSONObject("result");
        if (data == null) data = root;

        MediaItem item = new MediaItem();
        item.id = JsonUtils.string(data, "id", "aweme_id", "video_id", "media_id");
        if (item.id.isEmpty()) item.id = LinkUtils.extractId(target);
        item.title = JsonUtils.string(data, "title", "desc", "description", "text");
        item.author = JsonUtils.string(data, "author", "author_name", "username", "unique_id");
        item.coverUrl = JsonUtils.string(data, "cover", "thumbnail", "thumbnail_url", "cover_url", "poster", "origin_cover");
        item.pageUrl = target;
        item.source = "custom";
        item.createTime = MediaItem.seconds(data.optLong("create_time",
                data.optLong("createTime", data.optLong("timestamp", 0))));

        JSONObject video = data.optJSONObject("video");
        String videoUrl = video == null ? "" : JsonUtils.string(video, "url", "video", "play", "play_url", "playAddr", "download", "download_url", "no_watermark", "nowatermark");
        if (videoUrl.isEmpty()) {
            videoUrl = JsonUtils.string(data, "url", "video", "video_url", "videoUrl", "play", "play_url", "playAddr", "download", "download_url", "no_watermark", "nowatermark", "hd");
        }
        String wm = JsonUtils.string(data, "wm", "watermark", "watermark_url", "wm_url", "download_wm");
        String hd = JsonUtils.string(data, "hd", "hd_url", "hdUrl", "video_hd", "high_quality");
        if (hd.isEmpty() && video != null) hd = JsonUtils.string(video, "hd", "hd_url", "high_quality");
        item.videoUrl = videoUrl;
        item.videoWmUrl = wm.equals(videoUrl) ? "" : wm;
        item.videoHdUrl = hd.equals(videoUrl) ? "" : hd;

        JSONObject music = data.optJSONObject("music");
        if (music != null) {
            item.musicUrl = JsonUtils.string(music, "url", "play_url", "playUrl", "music_url", "musicUrl");
            item.musicTitle = JsonUtils.string(music, "title", "name");
            item.musicAuthor = JsonUtils.string(music, "author", "artist", "author_name");
            item.musicCoverUrl = JsonUtils.string(music, "cover", "cover_url", "cover_large", "coverLarge");
        } else {
            item.musicUrl = JsonUtils.string(data, "music", "music_url", "musicUrl");
        }

        JSONArray images = data.optJSONArray("images");
        if (images == null) images = data.optJSONArray("image_list");
        if (images == null) images = data.optJSONArray("photos");
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                Object raw = images.opt(i);
                String imageUrl = "";
                if (raw instanceof JSONObject) {
                    imageUrl = JsonUtils.string((JSONObject) raw, "url", "url_list", "image", "src", "download_url");
                } else if (raw instanceof String) {
                    imageUrl = JsonUtils.clean((String) raw);
                }
                if (imageUrl.startsWith("http") && !item.images.contains(imageUrl)) item.images.add(imageUrl);
            }
        }
        if (!item.hasVideo() && !item.hasImages()) {
            throw new ResolveException("Resolver response did not contain media");
        }
        if (!item.hasVideo() && item.coverUrl.isEmpty()) item.coverUrl = item.images.get(0);
        return item;
    }
}
