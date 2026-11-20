package com.theuncoaffair.fishpipe.net;

import com.theuncoaffair.fishpipe.data.MediaItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DouyinResolver {
    public MediaItem resolve(String input) throws Exception {
        String url = LinkUtils.expanded(input);
        if (url.isEmpty()) url = LinkUtils.normalize(input);
        String id = LinkUtils.extractId(url);
        if (id.isEmpty()) throw new ResolveException("Could not find the Douyin video id");

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Http.MOBILE_UA);
        headers.put("Referer", "https://www.iesdouyin.com/");
        headers.put("Cookie", "tt_webid_v2=" + LinkUtils.randomWid());

        Http.Response response = Http.get(LinkUtils.douyinApi(id), headers);
        if (response.ok()) {
            try {
                JSONObject root = new JSONObject(response.body);
                JSONArray items = root.optJSONArray("item_list");
                if (items == null) items = root.optJSONArray("aweme_list");
                if (items != null && items.length() > 0) {
                    JSONObject object = items.optJSONObject(0);
                    MediaItem item = parseObject(object, id);
                    if (item.hasContent()) {
                        item.pageUrl = "https://www.douyin.com/video/" + id;
                        return item;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // The share page contains a hydration script on some deployments.
        String page = url.contains("/video/") ? url : "https://www.douyin.com/video/" + id;
        Http.Response html = Http.get(page, headers);
        if (html.ok()) {
            List<String> scripts = JsonUtils.scriptJson(html.body, id);
            for (String script : scripts) {
                try {
                    JSONObject root = new JSONObject(script);
                    JSONObject object = JsonUtils.findVideoObject(root, id);
                    if (object == null) object = JsonUtils.findImageObject(root);
                    if (object != null) {
                        MediaItem item = parseObject(object, id);
                        if (item.hasContent()) {
                            item.pageUrl = page;
                            return item;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        throw new ResolveException("No Douyin video found");
    }

    private MediaItem parseObject(JSONObject object, String preferredId) {
        MediaItem item = new MediaItem();
        item.source = "douyin";
        if (object == null) return item;
        item.id = JsonUtils.string(object, "aweme_id", "awemeId", "item_id", "id");
        if (item.id.isEmpty()) item.id = preferredId;
        item.title = JsonUtils.string(object, "desc", "title", "text");
        item.createTime = MediaItem.seconds(object.optLong("create_time",
                object.optLong("createTime", object.optLong("ctime", 0))));
        item.author = JsonUtils.string(object, "nickname", "unique_id", "uniqueId");
        JSONObject author = object.optJSONObject("author");
        if (author != null) {
            String name = JsonUtils.string(author, "unique_id", "uniqueId", "nickname", "name");
            if (!name.isEmpty()) item.author = name;
        }

        JSONObject video = object.optJSONObject("video");
        if (video != null) {
            String play = JsonUtils.string(video, "play_addr", "playAddr", "play_addr_h264", "playAddrH264");
            String download = JsonUtils.string(video, "download_addr", "downloadAddr");
            String hd = JsonUtils.bestBitrate(video);
            item.videoUrl = noWatermark(play.isEmpty() ? download : play);
            item.videoWmUrl = download.isEmpty() || download.equals(play) ? "" : download;
            item.videoHdUrl = hd.isEmpty() || hd.equals(play) ? "" : noWatermark(hd);
            item.coverUrl = JsonUtils.string(video, "origin_cover", "originCover", "cover", "dynamic_cover", "dynamicCover");
        }

        JSONObject music = object.optJSONObject("music");
        if (music != null) {
            item.musicUrl = JsonUtils.string(music, "play_url", "playUrl", "music_url", "musicUrl");
            item.musicTitle = JsonUtils.string(music, "title", "name");
            item.musicAuthor = JsonUtils.string(music, "author", "author_name", "authorName");
            item.musicCoverUrl = JsonUtils.string(music, "cover_large", "coverLarge", "cover_medium", "coverMedium", "cover");
        }

        JSONArray images = object.optJSONArray("images");
        if (images == null) images = object.optJSONArray("image_list");
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                Object raw = images.opt(i);
                String imageUrl = "";
                if (raw instanceof JSONObject) {
                    JSONObject image = (JSONObject) raw;
                    imageUrl = JsonUtils.string(image, "url_list", "urlList", "download_url_list", "downloadUrlList", "url");
                } else if (raw instanceof String) {
                    imageUrl = JsonUtils.clean((String) raw);
                }
                if (imageUrl.startsWith("http") && !item.images.contains(imageUrl)) item.images.add(imageUrl);
            }
        }
        if (!item.hasVideo() && item.hasImages()) {
            item.coverUrl = item.coverUrl.isEmpty() ? item.images.get(0) : item.coverUrl;
        }
        return item;
    }

    private static String noWatermark(String url) {
        if (url == null) return "";
        return url.replace("/playwm/", "/play/")
                .replace("playwm", "play")
                .replace("watermark=1", "watermark=0");
    }
}
