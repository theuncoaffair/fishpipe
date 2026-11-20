package com.theuncoaffair.fishpipe.net;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class Http {
    public static final String DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int MAX_BODY = 8 * 1024 * 1024;

    private Http() {
    }

    public static Response get(String url, Map<String, String> headers) throws Exception {
        return request(url, "GET", null, headers);
    }

    public static Response get(String url, Map<String, String> headers,
                               int connectTimeout, int readTimeout) throws Exception {
        return request(url, "GET", null, headers, connectTimeout, readTimeout);
    }

    public static Response postForm(String url, Map<String, String> form, Map<String, String> headers) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        Map<String, String> h = new LinkedHashMap<>();
        if (headers != null) h.putAll(headers);
        if (!h.containsKey("Content-Type")) h.put("Content-Type", "application/x-www-form-urlencoded");
        return request(url, "POST", body.toString().getBytes(StandardCharsets.UTF_8), h);
    }

    public static Response request(String url, String method, byte[] body,
                                   Map<String, String> headers) throws Exception {
        return request(url, method, body, headers, 20000, 25000);
    }

    public static Response request(String url, String method, byte[] body,
                                   Map<String, String> headers,
                                   int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("User-Agent", DESKTOP_UA);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        conn.setRequestProperty(e.getKey(), e.getValue());
                    }
                }
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(body.length);
                OutputStream out = conn.getOutputStream();
                out.write(body);
                out.close();
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null && "gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                in = new GZIPInputStream(in);
            }
            String text = read(in);
            return new Response(code, text, conn.getURL() == null ? url : conn.getURL().toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static long contentLength(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", MOBILE_UA);
            conn.setRequestProperty("Referer", "https://www.tiktok.com/");
            conn.setRequestProperty("Range", "bytes=0-0");
            int code = conn.getResponseCode();
            String range = conn.getHeaderField("Content-Range");
            if (range != null) {
                int slash = range.lastIndexOf('/');
                if (slash >= 0 && slash + 1 < range.length()) {
                    try {
                        long total = Long.parseLong(range.substring(slash + 1).trim());
                        if (total > 0) return total;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (code != 206) {
                String header = conn.getHeaderField("Content-Length");
                if (header != null) {
                    try {
                        long length = Long.parseLong(header.trim());
                        if (length > 0) return length;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
        return -1;
    }

    public static String follow(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", MOBILE_UA);
            conn.setRequestProperty("Accept", "text/html,*/*");
            // Reading the headers is enough to obtain the final URL.
            conn.getResponseCode();
            return conn.getURL() == null ? url : conn.getURL().toString();
        } catch (Exception ignored) {
            return url;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > MAX_BODY) break;
            out.write(buffer, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static final class Response {
        public final int code;
        public final String body;
        public final String finalUrl;

        Response(int code, String body, String finalUrl) {
            this.code = code;
            this.body = body == null ? "" : body;
            this.finalUrl = finalUrl == null ? "" : finalUrl;
        }

        public boolean ok() {
            return code >= 200 && code < 400 && !body.isEmpty();
        }
    }
}
