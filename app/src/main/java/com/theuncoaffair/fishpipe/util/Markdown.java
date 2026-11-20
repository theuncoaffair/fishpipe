package com.theuncoaffair.fishpipe.util;

import android.os.Build;
import android.text.Html;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Markdown {
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    private Markdown() {
    }

    public static CharSequence render(String markdown) {
        String html = toHtml(markdown);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        }
        return Html.fromHtml(html);
    }

    private static String toHtml(String markdown) {
        StringBuilder out = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        boolean inList = false;
        boolean inParagraph = false;
        for (String raw : markdown.split("\\r?\\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) {
                closeParagraph(out, paragraph, inParagraph);
                inParagraph = false;
                if (inList) {
                    out.append("</ul>");
                    inList = false;
                }
                continue;
            }
            if (line.startsWith("# ")) {
                closeBlocks(out, paragraph, inParagraph, inList);
                inParagraph = false;
                inList = false;
                out.append("<h1>").append(inline(line.substring(2))).append("</h1>");
            } else if (line.startsWith("## ")) {
                closeBlocks(out, paragraph, inParagraph, inList);
                inParagraph = false;
                inList = false;
                out.append("<h2>").append(inline(line.substring(3))).append("</h2>");
            } else if (line.startsWith("### ")) {
                closeBlocks(out, paragraph, inParagraph, inList);
                inParagraph = false;
                inList = false;
                out.append("<h3>").append(inline(line.substring(4))).append("</h3>");
            } else if (line.startsWith("- ")) {
                closeParagraph(out, paragraph, inParagraph);
                inParagraph = false;
                if (!inList) {
                    out.append("<ul>");
                    inList = true;
                }
                out.append("<li>&#8194;&#8194;")
                        .append(inline(line.substring(2))).append("</li>");
            } else {
                if (inList) {
                    out.append("</ul>");
                    inList = false;
                }
                if (inParagraph) paragraph.append(' ');
                paragraph.append(inline(line));
                inParagraph = true;
            }
        }
        closeBlocks(out, paragraph, inParagraph, inList);
        return out.toString();
    }

    private static void closeBlocks(StringBuilder out, StringBuilder paragraph,
                                    boolean inParagraph, boolean inList) {
        closeParagraph(out, paragraph, inParagraph);
        if (inList) out.append("</ul>");
    }

    private static void closeParagraph(StringBuilder out, StringBuilder paragraph, boolean inParagraph) {
        if (!inParagraph) return;
        String text = paragraph.toString().trim();
        paragraph.setLength(0);
        if (text.isEmpty()) return;
        out.append("<p>").append(text).append("</p>");
    }

    private static String inline(String text) {
        String value = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        value = replace(LINK, value, "<a href=\"$2\">$1</a>");
        value = replace(BOLD, value, "<b>$1</b>");
        value = replace(ITALIC, value, "<i>$1</i>");
        value = replace(CODE, value, "<tt>$1</tt>");
        return value;
    }

    private static String replace(Pattern pattern, String value, String replacement) {
        Matcher matcher = pattern.matcher(value);
        return matcher.replaceAll(replacement);
    }
}
