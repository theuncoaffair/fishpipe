package com.theuncoaffair.fishpipe.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.PackageInfoCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.databinding.DialogUpdateBinding;
import com.theuncoaffair.fishpipe.net.Http;
import com.theuncoaffair.fishpipe.util.LocalFileProvider;
import com.theuncoaffair.fishpipe.util.Markdown;
import com.theuncoaffair.fishpipe.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdatePrompt {
    private static final AtomicBoolean CHECKED = new AtomicBoolean();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdatePrompt() {
    }

    public static void check(Activity activity) {
        check(activity, false);
    }

    public static void checkNow(Activity activity, boolean manual) {
        check(activity, manual);
    }

    private static void check(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing()) return;
        String manifestUrl = activity.getString(R.string.update_manifest_url).trim();
        if (manifestUrl.isEmpty()) {
            if (manual) Ui.toast(activity, R.string.update_not_configured);
            return;
        }
        if (!manual && !CHECKED.compareAndSet(false, true)) return;
        Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            try {
                UpdateInfo info = UpdateInfo.load(manifestUrl);
                boolean available = info.versionCode > currentVersionCode(app)
                        && !info.apkUrl.isEmpty();
                MAIN.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (available) show(activity, info);
                    else if (manual) Ui.toast(activity, R.string.update_up_to_date);
                });
            } catch (Exception ignored) {
                if (!manual) CHECKED.set(false);
                if (manual) {
                    MAIN.post(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            Ui.toast(activity, R.string.update_check_failed);
                        }
                    });
                }
            }
        });
    }

    private static void show(Activity activity, UpdateInfo info) {
        DialogUpdateBinding binding = DialogUpdateBinding.inflate(
                LayoutInflater.from(activity));
        binding.updateVersion.setText(activity.getString(
                R.string.update_dialog_version, info.versionName));
        binding.updateWhatsNewBody.setMovementMethod(LinkMovementMethod.getInstance());
        binding.updateBugFixesBody.setMovementMethod(LinkMovementMethod.getInstance());
        binding.updateWhatsNewBody.setText(Markdown.render(
                info.whatsNew.isEmpty() ? activity.getString(R.string.update_no_notes) : info.whatsNew));
        binding.updateBugFixesBody.setText(Markdown.render(
                info.bugFixes.isEmpty() ? activity.getString(R.string.update_no_notes) : info.bugFixes));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(binding.getRoot())
                .setCancelable(false)
                .create();

        File[] readyFile = {null};
        binding.updateAction.setOnClickListener(v -> {
            if (readyFile[0] != null) {
                install(activity, binding, readyFile[0]);
            } else {
                download(activity, binding, info, readyFile);
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels
                    - dp(activity, 32), dp(activity, 560));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
    }

    private static void download(Activity activity, DialogUpdateBinding binding,
                                 UpdateInfo info, File[] readyFile) {
        binding.updateAction.setEnabled(false);
        binding.updateProgress.setVisibility(android.view.View.VISIBLE);
        binding.updateProgress.setIndeterminate(false);
        binding.updateProgress.setProgressCompat(0, false);
        binding.updateStatus.setVisibility(android.view.View.GONE);
        binding.updateAction.setText(R.string.update_now);

        Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            File file = null;
            try {
                if (normalizeHash(info.sha256).isEmpty()) throw new IntegrityException();
                File dir = new File(app.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create update dir");
                file = new File(dir, "fishpipe-" + info.versionCode + ".apk");
                if (!file.isFile() || !sha256(file).equalsIgnoreCase(normalizeHash(info.sha256))) {
                    if (file.exists() && !file.delete()) throw new IOException("Cannot replace update");
                    downloadFile(info.apkUrl, file, percent -> MAIN.post(() -> {
                        if (!activity.isFinishing()) {
                            binding.updateProgress.setProgressCompat(percent, true);
                            binding.updateStatus.setVisibility(android.view.View.VISIBLE);
                            binding.updateStatus.setText(activity.getString(
                                    R.string.update_downloading, percent));
                        }
                    }));
                }
                if (!sha256(file).equalsIgnoreCase(normalizeHash(info.sha256))) {
                    throw new IntegrityException();
                }
                if (!sameSigner(app, file)) throw new SignatureException();
                File done = file;
                MAIN.post(() -> {
                    if (activity.isFinishing()) return;
                    readyFile[0] = done;
                    binding.updateProgress.setVisibility(android.view.View.GONE);
                    binding.updateStatus.setVisibility(android.view.View.GONE);
                    binding.updateAction.setEnabled(true);
                    binding.updateAction.setText(R.string.update_install);
                    install(activity, binding, done);
                });
            } catch (Exception error) {
                if (file != null) file.delete();
                MAIN.post(() -> {
                    if (activity.isFinishing()) return;
                    binding.updateProgress.setVisibility(android.view.View.GONE);
                    binding.updateStatus.setVisibility(android.view.View.VISIBLE);
                    binding.updateStatus.setText(error instanceof IntegrityException
                            ? R.string.update_integrity_failed
                            : error instanceof SignatureException
                            ? R.string.update_signature_failed
                            : R.string.update_download_failed);
                    binding.updateAction.setEnabled(true);
                    binding.updateAction.setText(R.string.update_retry);
                });
            }
        });
    }

    private static void install(Activity activity, DialogUpdateBinding binding, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            binding.updateStatus.setVisibility(android.view.View.VISIBLE);
            binding.updateStatus.setText(R.string.update_install_permission);
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(settings);
            } catch (Exception ignored) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                } catch (Exception ignoredAgain) {
                    binding.updateStatus.setText(R.string.update_download_failed);
                }
            }
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(LocalFileProvider.uriFor(file),
                "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (Exception ignored) {
            binding.updateStatus.setVisibility(android.view.View.VISIBLE);
            binding.updateStatus.setText(R.string.update_download_failed);
        }
    }

    private static void downloadFile(String url, File file, Progress progress) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", Http.MOBILE_UA);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            long total = conn.getContentLength();
            in = conn.getInputStream();
            out = new FileOutputStream(file);
            byte[] buffer = new byte[65536];
            long read = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                read += count;
                if (total > 0) progress.update((int) Math.min(100, read * 100 / total));
            }
        } finally {
            if (out != null) out.close();
            if (in != null) in.close();
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean sameSigner(Context context, File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo current = pm.getPackageInfo(context.getPackageName(), flags);
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) return false;
        if (PackageInfoCompat.getLongVersionCode(archive)
                <= PackageInfoCompat.getLongVersionCode(current)) return false;
        Set<String> installed = signerHashes(current);
        Set<String> update = signerHashes(archive);
        return !installed.isEmpty() && installed.equals(update);
    }

    private static Set<String> signerHashes(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            SigningInfo signing = info.signingInfo;
            signatures = signing.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> hashes = new HashSet<>();
        if (signatures == null) return hashes;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            hashes.add(hex(digest.digest(signature.toByteArray())));
        }
        return hashes;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new java.io.FileInputStream(file);
        try {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            return hex(digest.digest());
        } finally {
            in.close();
        }
    }

    private static String normalizeHash(String value) {
        String hash = value.trim();
        int colon = hash.indexOf(':');
        if (colon >= 0) hash = hash.substring(colon + 1).trim();
        return hash;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >> 4) & 0xF, 16));
            out.append(Character.forDigit(value & 0xF, 16));
        }
        return out.toString();
    }

    private static long currentVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return PackageInfoCompat.getLongVersionCode(info);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private interface Progress {
        void update(int percent);
    }

    private static final class IntegrityException extends Exception {
    }

    private static final class SignatureException extends Exception {
    }
}
