package com.theuncoaffair.fishpipe.ui;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.theuncoaffair.fishpipe.R;

public final class DeleteDialogs {
    private DeleteDialogs() {
    }

    public static void show(Context context, boolean creator, Runnable deleteBoth,
                            Runnable deleteHistoryOnly) {
        show(context, R.string.delete_choice_title, 0,
                creator ? R.string.delete_history_and_files : R.string.delete_history_and_device,
                deleteBoth, deleteHistoryOnly);
    }

    public static void show(Context context, int titleRes, int bodyRes, int bothLabelRes,
                            Runnable deleteBoth, Runnable deleteHistoryOnly) {
        if (context == null) return;
        show(context, context.getString(titleRes), bodyRes, bothLabelRes,
                deleteBoth, deleteHistoryOnly);
    }

    public static void showSingle(Context context, CharSequence titleText, int bodyRes,
                                  int actionLabelRes, Runnable action) {
        if (context == null) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(titleText)
                .setPositiveButton(actionLabelRes, (dialog, which) -> {
                    if (action != null) action.run();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (bodyRes != 0) builder.setMessage(bodyRes);
        builder.show();
    }

    public static void show(Context context, CharSequence titleText, int bodyRes, int bothLabelRes,
                            Runnable deleteBoth, Runnable deleteHistoryOnly) {
        if (context == null) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(titleText)
                .setPositiveButton(bothLabelRes, (dialog, which) -> {
                    if (deleteBoth != null) deleteBoth.run();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (bodyRes != 0) builder.setMessage(bodyRes);
        if (deleteHistoryOnly != null) {
            builder.setNeutralButton(R.string.delete_history_only, (dialog, which) -> {
                deleteHistoryOnly.run();
            });
        }
        builder.show();
    }
}
