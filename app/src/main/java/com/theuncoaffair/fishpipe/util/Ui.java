package com.theuncoaffair.fishpipe.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.databinding.DialogInputBinding;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.di.AppGraph;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Ui {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    public static void toast(Context context, String text) {
        if (context == null || text == null) return;
        Context app = context.getApplicationContext();
        MAIN.post(() -> Toast.makeText(app, text, Toast.LENGTH_SHORT).show());
    }

    public static void toast(Context context, int res) {
        if (context == null) return;
        toast(context, context.getString(res));
    }

    public static void downloadStarted(Context context) {
        if (context == null) return;
        downloadStarted(context, context.getString(R.string.download_started));
    }

    public static void downloadStarted(Context context, String text) {
        if (context == null) return;
        if (AppGraph.get(context).settings().isToastDownloadStart()) toast(context, text);
    }

    public static void downloadEnded(Context context, String text) {
        if (context == null) return;
        if (AppGraph.get(context).settings().isToastDownloadEnd()) toast(context, text);
    }

    public static void embedStarted(Context context) {
        if (context == null) return;
        Settings settings = AppGraph.get(context).settings();
        if (settings.isToastEmbedStart()) toast(context, R.string.embedding_caption);
        if (settings.isNotifyEmbedStart()) notificationSound(context);
    }

    public static void embedFinished(Context context) {
        if (context == null) return;
        Settings settings = AppGraph.get(context).settings();
        if (settings.isToastEmbedFinished()) toast(context, R.string.caption_embedded);
        if (settings.isNotifyEmbedFinished()) notificationSound(context);
    }

    public static void notificationSound(Context context) {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(context.getApplicationContext(), uri);
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {
        }
    }

    public static void alert(Context context, String title, String message) {
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public static void confirm(Context context, String title, String message, String positive,
                               DialogInterface.OnClickListener listener) {
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(positive, listener)
                .show();
    }

    public static void input(Context context, String title, String value, String hint,
                             InputTypeCallback callback) {
        input(context, title, value, hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, callback);
    }

    public static void input(Context context, String title, String value, String hint, int inputType,
                             InputTypeCallback callback) {
        DialogInputBinding binding = DialogInputBinding.inflate(LayoutInflater.from(context));
        binding.container.setHint(hint);
        binding.input.setInputType(inputType);
        binding.input.setText(value == null ? "" : value);
        binding.input.setSelection(binding.input.getText() == null ? 0 : binding.input.getText().length());
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> callback.onValue(
                        binding.input.getText() == null ? "" : binding.input.getText().toString().trim()))
                .show();
    }

    public static void singleChoice(Context context, String title, String[] items, int checked,
                                    DialogInterface.OnClickListener listener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, listener)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void hideKeyboard(Activity activity) {
        View focus = activity.getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    public static void openUrl(Context context, String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            toast(context, R.string.error_generic);
        }
    }

    public static void shareText(Context context, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)));
    }

    public static void copy(Context context, String text) {
        copy(context, text, R.string.copied);
    }

    public static void copy(Context context, String text, int labelRes) {
        if (context == null || text == null || text.trim().isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("FishPipe", text));
        toast(context, labelRes);
    }

    public static String clipboard(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null) return "";
        ClipData clip = cm.getPrimaryClip();
        if (clip.getItemCount() == 0) return "";
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        return text == null ? "" : text.toString();
    }

    public static long clipboardTimestamp(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L;
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return 0L;
        ClipDescription description = cm.getPrimaryClipDescription();
        return description == null ? 0L : description.getTimestamp();
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    public static String formatDate(long time) {
        try {
            return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(time));
        } catch (Exception e) {
            return "";
        }
    }

    public static String formatUploadTime(long time) {
        return formatUploadTime(null, time);
    }

    public static String formatUploadTime(Context context, long time) {
        if (time <= 0) return "";
        try {
            long seconds = Math.max(0L, (System.currentTimeMillis() - time) / 1000L);
            if (seconds < 60L) return text(context, R.string.time_just_now, "Just now");
            long minutes = seconds / 60L;
            if (minutes < 60L) return text(context, R.string.time_minutes_ago, "%d min ago", minutes);
            long hours = minutes / 60L;
            if (hours < 24L) return text(context, R.string.time_hours_ago, "%d hr ago", hours);
            long days = hours / 24L;
            if (days < 7L) return text(context, R.string.time_days_ago, "%d days ago", days);
            Calendar now = Calendar.getInstance();
            Calendar then = Calendar.getInstance();
            then.setTimeInMillis(time);
            String pattern = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                    ? "d-M" : "d-M-y";
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(time));
        } catch (Exception e) {
            return "";
        }
    }

    private static String text(Context context, int res, String fallback) {
        return context == null ? fallback : context.getString(res);
    }

    private static String text(Context context, int res, String fallback, long value) {
        return context == null ? String.format(Locale.getDefault(), fallback, value)
                : context.getString(res, value);
    }

    public static String safeName(String value) {
        if (value == null) return "media";
        String out = value.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (out.length() > 80) out = out.substring(0, 80);
        return out.isEmpty() ? "media" : out;
    }

    public static String extension(DownloadEntry entry) {
        if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) return "mp3";
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) return "jpg";
        return "mp4";
    }

    public static String mime(DownloadEntry entry) {
        if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) return "audio/mpeg";
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) return "image/jpeg";
        return "video/mp4";
    }

    public static void tint(ImageView view, int color) {
        view.setColorFilter(color);
    }

    public static void setVisible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static void statusBar(Activity activity, boolean light) {
        Window window = activity.getWindow();
        int background = ThemeManager.attrColor(activity, R.attr.colorSurface);
        window.setStatusBarColor(background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
        window.setNavigationBarColor(background);
    }

    public static int color(Context context, int res) {
        return context.getColor(res);
    }

    public static int withAlpha(int color, float alpha) {
        return Color.argb((int) (255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    public interface InputTypeCallback {
        void onValue(String value);
    }
}
