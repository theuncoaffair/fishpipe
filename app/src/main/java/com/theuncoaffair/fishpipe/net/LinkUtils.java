package com.theuncoaffair.fishpipe.net;

import java.net.URI;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkUtils {
    private static final Pattern URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("(?:/video/|/v/|item_ids=|modal_id=|/share/video/|/story/)(\\d{6,})");
    private static final Pattern ANY_ID = Pattern.compile("(\\d{15,})");

    private LinkUtils() {
    }

    public static String findUrl(String text) {
        if (text == null) return "";
        Matcher m = URL.matcher(text);
        if (m.find()) {
            String value = m.group();
            while (value.endsWith(")") || value.endsWith("]") || value.endsWith(",") || value.endsWith(".")) {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }
        String compact = text.trim();
        if (compact.contains("tiktok.com") || compact.contains("douyin.com")) {
            return "https://" + compact.replaceFirst("^/+", "");
        }
        return "";
    }

    public static String normalize(String url) {
        String value = findUrl(url);
        if (value.isEmpty()) return "";
        if (value.startsWith("http://")) value = "https://" + value.substring(7);
        return value;
    }

    public static String host(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isTikTok(String url) {
        String host = host(url);
        return host.equals("tiktok.com") || host.endsWith(".tiktok.com");
    }

    public static boolean isDouyin(String url) {
        String host = host(url);
        return host.equals("douyin.com") || host.endsWith(".douyin.com") || host.endsWith("iesdouyin.com");
    }

    public static boolean isSupported(String url) {
        return isTikTok(url) || isDouyin(url);
    }

    public static boolean isStory(String url) {
        if (url == null) return false;
        String value = url.toLowerCase(Locale.US);
        return value.contains("/story/") || value.contains("/stories/");
    }

    public static boolean isShort(String url) {
        String host = host(url);
        if (host.startsWith("vt.") || host.startsWith("vm.") || host.startsWith("v.")) return true;
        return url.contains("/t/");
    }

    public static String expanded(String url) {
        String value = normalize(url);
        if (value.isEmpty()) return "";
        if (!isShort(value)) return value;
        String followed = Http.follow(value);
        return followed == null || followed.isEmpty() ? value : followed;
    }

    public static String extractId(String url) {
        if (url == null) return "";
        Matcher m = ID.matcher(url);
        if (m.find()) return m.group(1);
        Matcher any = ANY_ID.matcher(url);
        if (any.find()) return any.group(1);
        return "";
    }

    public static String randomWid() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("69");
        for (int i = 0; i < 16; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    public static String tiktokPage(String url, String id) {
        if (url != null && isTikTok(url) && (url.contains("/video/") || isStory(url))) return url;
        if (id != null && !id.isEmpty()) return "https://m.tiktok.com/v/" + id + ".html";
        return url == null ? "" : url;
    }

    public static String douyinApi(String id) {
        return "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=" + id;
    }
}
