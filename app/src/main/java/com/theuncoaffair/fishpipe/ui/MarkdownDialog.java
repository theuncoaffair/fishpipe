package com.theuncoaffair.fishpipe.ui;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.databinding.DialogMarkdownBinding;
import com.theuncoaffair.fishpipe.util.Markdown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class MarkdownDialog {
    private MarkdownDialog() {
    }

    public static void showCloseOnly(Context context, int titleRes, String[] assets) {
        show(context, titleRes, assets, false, true, null, null, true);
    }

    public static void show(Context context, int titleRes, String[] assets,
                            boolean allowStartupChoice, boolean startupEnabled,
                            Runnable onDontShowAgain, Runnable onShowAgain) {
        show(context, titleRes, assets, allowStartupChoice, startupEnabled,
                onDontShowAgain, onShowAgain, false);
    }

    private static void show(Context context, int titleRes, String[] assets,
                             boolean allowStartupChoice, boolean startupEnabled,
                             Runnable onDontShowAgain, Runnable onShowAgain,
                             boolean closeOnly) {
        DialogMarkdownBinding binding = DialogMarkdownBinding.inflate(
                LayoutInflater.from(context));
        binding.markdownVersion.setText(context.getString(
                R.string.version, versionName(context)));
        binding.markdownBody.setMovementMethod(LinkMovementMethod.getInstance());
        binding.markdownBody.setText(read(context, assets));
        binding.markdownScroll.setMaxHeightFraction(0.64f);
        binding.markdownDontShow.setVisibility(
                !closeOnly && allowStartupChoice ? View.VISIBLE : View.GONE);
        binding.markdownDontShow.setChecked(!startupEnabled);
        if (closeOnly) {
            binding.markdownOk.setVisibility(View.GONE);
            binding.markdownCancel.setText(R.string.close);
            binding.markdownFooter.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .create();
        binding.markdownOk.setOnClickListener(v -> {
            if (allowStartupChoice) {
                boolean dontShowAgain = binding.markdownDontShow.isChecked();
                if (dontShowAgain && onDontShowAgain != null) onDontShowAgain.run();
                if (!dontShowAgain && onShowAgain != null) onShowAgain.run();
            }
            dialog.dismiss();
        });
        binding.markdownCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels
                    - dp(context, 32), dp(context, 560));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
    }

    private static String versionName(Context context) {
        try {
            String value = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static CharSequence read(Context context, String[] assets) {
        StringBuilder text = new StringBuilder();
        for (String asset : assets) {
            if (asset == null || asset.isEmpty()) continue;
            if (text.length() > 0) text.append('\n');
            try {
                text.append(readAsset(context, asset));
            } catch (IOException ignored) {
                // Missing content stays empty instead of showing a broken dialog.
            }
        }
        return Markdown.render(text.toString());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String readAsset(Context context, String path) throws IOException {
        InputStream in = context.getAssets().open(path);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
