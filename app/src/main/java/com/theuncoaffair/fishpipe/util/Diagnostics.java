package com.theuncoaffair.fishpipe.util;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.theuncoaffair.fishpipe.BuildConfig;
import com.theuncoaffair.fishpipe.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import timber.log.Timber;

public final class Diagnostics {
    private static final String FILE_NAME = "fishpipe-diagnostics.log";
    private static final String OLD_FILE_NAME = "fishpipe-diagnostics.old.log";
    private static final String PREFS = "diagnostics";
    private static final String KEY_PENDING_CRASH = "pending_crash";
    private static final long MAX_BYTES = 512L * 1024L;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File file;
    private static Context appContext;
    private static boolean installed;

    private Diagnostics() {
    }

    public static void install(@NonNull Context context) {
        if (installed) return;
        installed = true;
        appContext = context.getApplicationContext();
        file = new File(appContext.getFilesDir(), FILE_NAME);
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrash(thread, throwable);
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    public static void plant() {
        Timber.plant(new Tree());
    }

    public static boolean hasPendingCrash(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_PENDING_CRASH, false) && file(context).isFile();
    }

    public static void markCrashReported(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_PENDING_CRASH, false).apply();
    }

    public static void share(@NonNull Context context) {
        append(context, "INFO", "Diagnostics", "Manual diagnostics share", null);
        appendExitHistory(context);
        File target = file(context);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_STREAM, LocalFileProvider.uriFor(target));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, context.getString(R.string.share_log));
        try {
            context.startActivity(chooser);
        } catch (Exception e) {
            Log.w("Diagnostics", "Unable to open share sheet", e);
        }
    }

    public static void append(@NonNull Context context, @NonNull String priority,
                              @Nullable String tag, @Nullable String message,
                              @Nullable Throwable throwable) {
        File target = file(context);
        StringBuilder text = new StringBuilder(256);
        synchronized (TIME) {
            text.append(TIME.format(new Date())).append(' ');
        }
        text.append(priority).append('/');
        text.append(tag == null || tag.isEmpty() ? "FishPipe" : tag);
        if (message != null && !message.isEmpty()) text.append(": ").append(message);
        text.append('\n');
        if (throwable != null) {
            StringWriter stack = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stack));
            text.append(stack);
            if (!stack.toString().endsWith("\n")) text.append('\n');
        }
        write(target, text.toString());
    }

    private static void appendExitHistory(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Api30.append(context);
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private static final class Api30 {
        private static void append(Context context) {
            try {
                ActivityManager manager = context.getSystemService(ActivityManager.class);
                if (manager == null) return;
                for (ApplicationExitInfo info : manager.getHistoricalProcessExitReasons(
                        context.getPackageName(), 0, 4)) {
                    int reason = info.getReason();
                    if (reason != ApplicationExitInfo.REASON_CRASH
                            && reason != ApplicationExitInfo.REASON_CRASH_NATIVE
                            && reason != ApplicationExitInfo.REASON_ANR
                            && reason != ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                            && reason != ApplicationExitInfo.REASON_SIGNALED) {
                        continue;
                    }
                    StringBuilder text = new StringBuilder(512);
                    text.append("\n===== PREVIOUS EXIT =====\n");
                    text.append("When: ").append(TIME.format(new Date(info.getTimestamp())))
                            .append('\n');
                    text.append("Reason: ").append(reasonLabel(reason)).append('\n');
                    text.append("Description: ").append(info.getDescription()).append('\n');
                    InputStream trace = info.getTraceInputStream();
                    if (trace != null) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(trace, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                text.append(line).append('\n');
                            }
                        }
                    }
                    write(file(context), text.toString());
                }
            } catch (Exception e) {
                Log.w("Diagnostics", "Unable to read previous exit info", e);
            }
        }

        private static String reasonLabel(int reason) {
            switch (reason) {
                case ApplicationExitInfo.REASON_CRASH:
                    return "CRASH";
                case ApplicationExitInfo.REASON_CRASH_NATIVE:
                    return "CRASH_NATIVE";
                case ApplicationExitInfo.REASON_ANR:
                    return "ANR";
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                    return "INITIALIZATION_FAILURE";
                case ApplicationExitInfo.REASON_SIGNALED:
                    return "SIGNALED";
                default:
                    return "REASON_" + reason;
            }
        }
    }

    private static void writeCrash(Thread thread, Throwable throwable) {
        Context context = appContext;
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_PENDING_CRASH, true).commit();
        StringBuilder header = new StringBuilder(512);
        header.append("\n===== FATAL CRASH =====\n");
        header.append("Thread: ").append(thread.getName()).append('\n');
        header.append("App: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        header.append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        header.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        header.append("Locale: ").append(Locale.getDefault()).append('\n');
        StringWriter stack = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stack));
        header.append(stack).append('\n');
        write(file(context), header.toString());
    }

    private static void write(File target, String text) {
        synchronized (LOCK) {
            try {
                if (target.length() + text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                    File old = new File(target.getParentFile(), OLD_FILE_NAME);
                    if (old.exists()) old.delete();
                    target.renameTo(old);
                }
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(target, true), StandardCharsets.UTF_8)) {
                    writer.write(text);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static File file(Context context) {
        if (file != null) return file;
        file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        return file;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String priorityLabel(int priority) {
        switch (priority) {
            case Log.VERBOSE:
                return "VERBOSE";
            case Log.DEBUG:
                return "DEBUG";
            case Log.INFO:
                return "INFO";
            case Log.WARN:
                return "WARN";
            case Log.ERROR:
                return "ERROR";
            default:
                return "LOG";
        }
    }

    private static final class Tree extends Timber.Tree {
        @Override
        protected boolean isLoggable(@Nullable String tag, int priority) {
            return priority >= Log.INFO;
        }

        @Override
        protected void log(int priority, String tag, @NonNull String message,
                           @Nullable Throwable throwable) {
            if (appContext == null) return;
            append(appContext, priorityLabel(priority), tag, message, throwable);
        }
    }
}
