package com.theuncoaffair.fishpipe.download;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.theuncoaffair.fishpipe.FloatingDownloadActivity;
import com.theuncoaffair.fishpipe.MainActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;
import com.theuncoaffair.fishpipe.databinding.DialogFloatingSettingsBinding;
import com.theuncoaffair.fishpipe.databinding.ViewDismissTargetBinding;
import com.theuncoaffair.fishpipe.databinding.ViewFloatingButtonBinding;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.util.LocaleHelper;
import com.theuncoaffair.fishpipe.util.Notifications;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class ClipboardWatchService extends Service {
    private static final String ACTION_START = "com.theuncoaffair.fishpipe.action.START_WATCH";
    private static final String ACTION_STOP = "com.theuncoaffair.fishpipe.action.STOP_WATCH";
    private static final String ACTION_RESOLVE = "com.theuncoaffair.fishpipe.action.RESOLVE_FLOATING";
    private static final String EXTRA_RESOLVE_LINK = "resolve_link";
    private static final long POLL_MS = 1500L;
    private static final int NOTIFICATION_ID = Notifications.CLIPBOARD_WATCH_ID;

    private static boolean appForeground;
    private static boolean overlaySuspended;
    private static boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ExecutorService resolverPool = Executors.newSingleThreadExecutor();

    @Inject Settings settings;
    @Inject ResolveRepository resolver;
    private Context localized;
    private boolean resolving;
    private WindowManager windowManager;
    private ImageButton floating;
    private WindowManager.LayoutParams floatingParams;
    private ImageView dismissTarget;
    private ImageView dismissHighlight;
    private TextView dismissLabel;
    private FrameLayout dismissContainer;
    private int dismissSize = 0;
    private final int[] floatingLocation = new int[2];
    private final int[] dismissLocation = new int[2];
    private AlertDialog settingsDialog;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private boolean dragging;
    private boolean longPressed;
    private int touchSlop;
    private final Runnable longPress = () -> {
        if (dragging || floating == null) return;
        longPressed = true;
        showFloatingSettings();
    };

    private final Runnable resetSuspension = () -> {
        if (!appForeground && overlaySuspended) {
            overlaySuspended = false;
            updateOverlay();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!settings.isStayInApp()) {
                shutdown();
                return;
            }
            updateOverlay();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public static void sync(Context context) {
        if (context == null) return;
        Settings settings = AppGraph.get(context).settings();
        if (settings.isStayInApp()) start(context);
        else stop(context);
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, ClipboardWatchService.class).setAction(ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void resolveFloating(Context context, String link) {
        if (context == null || link == null || link.trim().isEmpty()) return;
        Intent intent = new Intent(context, ClipboardWatchService.class)
                .setAction(ACTION_RESOLVE)
                .putExtra(EXTRA_RESOLVE_LINK, link);
        try {
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context) {
        if (!running) return;
        Intent intent = new Intent(context, ClipboardWatchService.class).setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void setAppForeground(boolean foreground) {
        appForeground = foreground;
    }

    public static void setOverlaySuspended(boolean suspended) {
        overlaySuspended = suspended;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        localized = LocaleHelper.wrap(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        Notifications.ensureChannel(this);
        if (!startForegroundCompat()) {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        localized = LocaleHelper.wrap(this);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (!settings.isStayInApp()) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (!startForegroundCompat()) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESOLVE.equals(intent.getAction())) {
            beginFloatingResolve(intent.getStringExtra(EXTRA_RESOLVE_LINK));
            handler.removeCallbacks(tick);
            handler.post(tick);
            return START_STICKY;
        }
        updateOverlay();
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        localized = LocaleHelper.wrap(this);
        startForegroundCompat();
        updateOverlay();
        clampFloatingToScreen();
    }

    @Override
    public void onDestroy() {
        running = false;
        overlaySuspended = false;
        handler.removeCallbacksAndMessages(null);
        resolving = false;
        resolverPool.shutdownNow();
        dismissSettings();
        hideOverlay();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean startForegroundCompat() {
        Notification notification = Notifications.clipboardWatch(localized, false,
                settings.isStayInApp());
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void shutdown() {
        handler.removeCallbacksAndMessages(null);
        dismissSettings();
        hideOverlay();
        stopForeground(true);
        stopSelf();
    }

    private void updateOverlay() {
        boolean show = settings.isStayInApp()
                && !appForeground
                && !overlaySuspended
                && !resolving
                && android.provider.Settings.canDrawOverlays(this);
        if (show) {
            showOverlay();
            if (floating != null) {
                floating.setContentDescription(localized.getString(R.string.floating_download));
            }
        } else {
            hideOverlay();
        }
    }

    @SuppressLint({"ClickableViewAccessibility", "RtlHardcoded"})
    private void showOverlay() {
        if (floating != null || windowManager == null) return;
        Point screen = screenSize();
        int size = overlaySize();
        ImageButton button = ViewFloatingButtonBinding.inflate(LayoutInflater.from(this)).floatingButton;
        button.setColorFilter(Color.WHITE);
        button.setContentDescription(localized.getString(R.string.floating_download));
        button.setBackground(floatingBackground(size));
        button.setClipToOutline(true);
        button.setPadding(size / 4, size / 4, size / 4, size / 4);
        button.setAlpha(settings.getFloatOpacity());
        button.setOnClickListener(v -> openOptions());
        button.setOnTouchListener(this::onTouch);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size, size, overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        placeInitial(params, screen, size);
        try {
            windowManager.addView(button, params);
        } catch (Exception e) {
            return;
        }
        floating = button;
        floatingParams = params;
    }

    private void hideOverlay() {
        hideDismissTarget();
        if (floating == null) {
            dismissSettings();
            return;
        }
        if (windowManager == null) return;
        floating.removeCallbacks(longPress);
        saveFloatingPosition();
        dismissSettings();
        try {
            windowManager.removeView(floating);
        } catch (Exception ignored) {
        }
        floating = null;
        floatingParams = null;
    }

    private GradientDrawable floatingBackground(int size) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(getColor(R.color.primary));
        shape.setStroke(Math.max(1, size / 42), 0x33FFFFFF);
        return shape;
    }

    private void placeInitial(WindowManager.LayoutParams params, Point screen, int size) {
        int maxX = Math.max(0, screen.x - size);
        int maxY = Math.max(0, screen.y - size);
        float x = settings.getFloatX();
        float y = settings.getFloatY();
        if (x < 0f) {
            params.x = Math.max(0, maxX - dp(16));
        } else {
            params.x = clamp(Math.round(x * maxX), 0, maxX);
        }
        if (y < 0f) {
            params.y = Math.round(maxY * 0.55f);
        } else {
            params.y = clamp(Math.round(y * maxY), 0, maxY);
        }
    }

    private boolean onTouch(View view, MotionEvent event) {
        if (floatingParams == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hideDismissTarget();
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = floatingParams.x;
                downY = floatingParams.y;
                dragging = false;
                longPressed = false;
                view.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                    view.removeCallbacks(longPress);
                }
                if (dragging) {
                    moveFloating(dx, dy);
                    if (reachedDismissCentre()) {
                        showDismissTarget();
                        updateDismissTarget();
                    } else {
                        hideDismissTarget();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                view.removeCallbacks(longPress);
                boolean drop = dragging && isOverDismiss();
                hideDismissTarget();
                if (drop) {
                    hideFloatingButton();
                } else if (dragging) {
                    saveFloatingPosition();
                } else if (!longPressed) {
                    view.performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                view.removeCallbacks(longPress);
                hideDismissTarget();
                if (dragging) saveFloatingPosition();
                return true;
            default:
                return false;
        }
    }

    private void showDismissTarget() {
        if (dismissContainer != null || windowManager == null) return;
        Point screen = screenSize();
        int size = dp(64);
        int rootWidth = dp(120);
        int labelGap = dp(6);
        int highlightSize = Math.round(size * 1.55f);
        int highlightInset = Math.max(0, (highlightSize - size) / 2);
        int rootHeight = highlightSize + labelGap + dp(20);
        dismissSize = size;

        ViewDismissTargetBinding binding = ViewDismissTargetBinding.inflate(
                LayoutInflater.from(this));
        FrameLayout root = binding.getRoot();
        ImageView highlight = binding.dismissHighlight;
        ImageView target = binding.dismissTarget;
        TextView label = binding.dismissLabel;
        target.setColorFilter(Color.WHITE);
        target.setContentDescription(localized.getString(R.string.dismiss));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                rootWidth, rootHeight, overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(0, screen.x / 2 - rootWidth / 2);
        params.y = Math.max(0, screen.y / 2 - highlightSize / 2);
        try {
            windowManager.addView(root, params);
        } catch (Exception e) {
            return;
        }
        dismissContainer = root;
        dismissTarget = target;
        dismissHighlight = highlight;
        dismissLabel = label;
    }

    private void hideDismissTarget() {
        if (dismissContainer == null) return;
        FrameLayout container = dismissContainer;
        dismissContainer = null;
        dismissTarget = null;
        dismissHighlight = null;
        dismissLabel = null;
        if (windowManager == null) return;
        try {
            windowManager.removeView(container);
        } catch (Exception ignored) {
        }
    }

    private float dismissDistance() {
        if (floating == null || dismissTarget == null) return Float.MAX_VALUE;
        floating.getLocationOnScreen(floatingLocation);
        dismissTarget.getLocationOnScreen(dismissLocation);
        int floatingWidth = floating.getWidth() > 0 ? floating.getWidth() : dismissSize;
        int floatingHeight = floating.getHeight() > 0 ? floating.getHeight() : dismissSize;
        int targetWidth = dismissTarget.getWidth() > 0 ? dismissTarget.getWidth() : dismissSize;
        int targetHeight = dismissTarget.getHeight() > 0 ? dismissTarget.getHeight() : dismissSize;
        float cx = floatingLocation[0] + floatingWidth / 2f;
        float cy = floatingLocation[1] + floatingHeight / 2f;
        float tx = dismissLocation[0] + targetWidth / 2f;
        float ty = dismissLocation[1] + targetHeight / 2f;
        return (float) Math.hypot(cx - tx, cy - ty);
    }

    private boolean isOverDismiss() {
        if (dismissContainer == null || floating == null || dismissTarget == null) return false;
        floating.getLocationOnScreen(floatingLocation);
        dismissTarget.getLocationOnScreen(dismissLocation);
        int floatingWidth = floating.getWidth() > 0 ? floating.getWidth() : dismissSize;
        int floatingHeight = floating.getHeight() > 0 ? floating.getHeight() : dismissSize;
        int targetWidth = dismissTarget.getWidth() > 0 ? dismissTarget.getWidth() : dismissSize;
        int targetHeight = dismissTarget.getHeight() > 0 ? dismissTarget.getHeight() : dismissSize;
        float cx = floatingLocation[0] + floatingWidth / 2f;
        float cy = floatingLocation[1] + floatingHeight / 2f;
        float tx = dismissLocation[0] + targetWidth / 2f;
        float ty = dismissLocation[1] + targetHeight / 2f;
        return Math.abs(cx - tx) <= targetWidth / 2f
                && Math.abs(cy - ty) <= targetHeight / 2f;
    }

    private boolean reachedDismissCentre() {
        if (floatingParams == null) return false;
        Point screen = screenSize();
        float cx = floatingParams.x + floatingParams.width / 2f;
        float cy = floatingParams.y + floatingParams.height / 2f;
        float dx = cx - screen.x / 2f;
        float dy = cy - screen.y / 2f;
        return Math.hypot(dx, dy) <= screen.y * 0.30f;
    }

    private void updateDismissTarget() {
        if (dismissContainer == null || dismissHighlight == null) return;
        float distance = dismissDistance();
        float proximity = clamp(1f - distance / (dismissSize * 3f), 0f, 1f);
        boolean over = isOverDismiss();
        dismissHighlight.setAlpha(0.15f + proximity * 0.7f);
        float scale = 0.85f + proximity * 0.45f;
        dismissHighlight.setScaleX(scale);
        dismissHighlight.setScaleY(scale);
        if (dismissTarget != null) {
            dismissTarget.setAlpha(over ? 1f : 0.85f);
            dismissTarget.setScaleX(over ? 1.12f : 1f);
            dismissTarget.setScaleY(over ? 1.12f : 1f);
        }
        if (dismissLabel != null) {
            dismissLabel.setAlpha(over ? 1f : 0.65f);
        }
    }

    private void moveFloating(float dx, float dy) {
        if (floatingParams == null) return;
        Point screen = screenSize();
        int maxX = Math.max(0, screen.x - floatingParams.width);
        int maxY = Math.max(0, screen.y - floatingParams.height);
        floatingParams.x = clamp(Math.round(downX + dx), 0, maxX);
        floatingParams.y = clamp(Math.round(downY + dy), 0, maxY);
        try {
            windowManager.updateViewLayout(floating, floatingParams);
        } catch (Exception ignored) {
        }
    }

    private void saveFloatingPosition() {
        if (floatingParams == null) return;
        Point screen = screenSize();
        int maxX = Math.max(1, screen.x - floatingParams.width);
        int maxY = Math.max(1, screen.y - floatingParams.height);
        settings.setFloatPosition(
                clamp(floatingParams.x / (float) maxX, 0f, 1f),
                clamp(floatingParams.y / (float) maxY, 0f, 1f));
    }

    private void openOptions() {
        if (resolving) return;
        overlaySuspended = true;
        updateOverlay();
        handler.removeCallbacks(resetSuspension);
        handler.postDelayed(resetSuspension, 6000L);
        Intent intent = new Intent(this, FloatingDownloadActivity.class);
        intent.putExtra(FloatingDownloadActivity.EXTRA_CAPTURE_ONLY, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            handler.removeCallbacks(resetSuspension);
            overlaySuspended = false;
            updateOverlay();
            Toast.makeText(localized, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private void beginFloatingResolve(String link) {
        if (!running || resolving || link == null || link.trim().isEmpty()) return;
        resolving = true;
        overlaySuspended = true;
        handler.removeCallbacks(resetSuspension);
        updateOverlay();
        resolverPool.execute(() -> {
            MediaItem item = null;
            try {
                item = resolver.resolve(link);
            } catch (Exception ignored) {
            }
            MediaItem resolved = item;
            handler.post(() -> showResolvedOptions(resolved, link));
        });
    }

    private void showResolvedOptions(MediaItem item, String link) {
        if (!running) return;
        resolving = false;
        if (item == null) {
            overlaySuspended = false;
            updateOverlay();
            Toast.makeText(localized, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!MediaItem.notEmpty(item.pageUrl)) item.pageUrl = link;
        Intent intent = new Intent(this, FloatingDownloadActivity.class);
        intent.putExtra(FloatingDownloadActivity.EXTRA_ITEM, item);
        intent.putExtra(FloatingDownloadActivity.EXTRA_COPIED_LINK, link);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        overlaySuspended = true;
        updateOverlay();
        handler.removeCallbacks(resetSuspension);
        handler.postDelayed(resetSuspension, 6000L);
        try {
            startActivity(intent);
        } catch (Exception e) {
            handler.removeCallbacks(resetSuspension);
            overlaySuspended = false;
            updateOverlay();
            Toast.makeText(localized, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideFloatingButton() {
        settings.setStayInApp(false);
        overlaySuspended = false;
        hideOverlay();
        Toast.makeText(localized, R.string.floating_removed, Toast.LENGTH_LONG).show();
        if (settings.isAutoDetect()) updateOverlay();
        else shutdown();
    }

    private void openFishPipe() {
        dismissSettings();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(localized, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private void showFloatingSettings() {
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        Context themed = new android.view.ContextThemeWrapper(
                localized, ThemeManager.themeRes(localized));
        DialogFloatingSettingsBinding binding = DialogFloatingSettingsBinding.inflate(
                android.view.LayoutInflater.from(themed));

        int sizePercent = Math.round(clamp(settings.getFloatScale(), 0.6f, 1f) * 100f);
        binding.floatingSizeBar.setProgress(sizePercent - 60);
        binding.floatingSizeLabel.setText(
                localized.getString(R.string.floating_size_value, sizePercent));

        int opacityPercent = Math.round(clamp(settings.getFloatOpacity(), 0.2f, 1f) * 100f);
        binding.floatingOpacityBar.setProgress(opacityPercent - 20);
        binding.floatingOpacityLabel.setText(
                localized.getString(R.string.floating_opacity_value, opacityPercent));

        binding.floatingRemove.setOnClickListener(v -> hideFloatingButton());
        binding.floatingOpen.setOnClickListener(v -> openFishPipe());

        binding.floatingSizeBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = progress + 60;
                binding.floatingSizeLabel.setText(
                        localized.getString(R.string.floating_size_value, percent));
                settings.setFloatScale(percent / 100f);
                applyFloatingAppearance();
            }
        });
        binding.floatingOpacityBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = progress + 20;
                binding.floatingOpacityLabel.setText(
                        localized.getString(R.string.floating_opacity_value, percent));
                settings.setFloatOpacity(percent / 100f);
                applyFloatingAppearance();
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(themed)
                .setTitle(R.string.floating_settings_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.floating_reset, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            TextView reset = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            reset.setOnClickListener(v -> {
                binding.floatingSizeBar.setProgress(40);
                binding.floatingOpacityBar.setProgress(80);
                settings.setFloatScale(1f);
                settings.setFloatOpacity(1f);
                applyFloatingAppearance();
            });
        });
        dialog.setOnDismissListener(ignored -> {
            if (settingsDialog == dialog) settingsDialog = null;
            saveFloatingPosition();
        });
        settingsDialog = dialog;
        android.view.Window window = dialog.getWindow();
        if (window != null) window.setType(overlayWindowType());
        try {
            dialog.show();
        } catch (Exception e) {
            settingsDialog = null;
        }
    }

    private void clampFloatingToScreen() {
        if (floating == null || floatingParams == null || windowManager == null) return;
        Point screen = screenSize();
        int maxX = Math.max(0, screen.x - floatingParams.width);
        int maxY = Math.max(0, screen.y - floatingParams.height);
        int x = clamp(floatingParams.x, 0, maxX);
        int y = clamp(floatingParams.y, 0, maxY);
        if (x == floatingParams.x && y == floatingParams.y) return;
        floatingParams.x = x;
        floatingParams.y = y;
        try {
            windowManager.updateViewLayout(floating, floatingParams);
        } catch (Exception ignored) {
        }
    }

    private void applyFloatingAppearance() {
        if (floating == null || floatingParams == null || windowManager == null) return;
        int newSize = overlaySize();
        int oldSize = floatingParams.width;
        if (oldSize <= 0) oldSize = newSize;
        int centerX = floatingParams.x + oldSize / 2;
        int centerY = floatingParams.y + oldSize / 2;
        floatingParams.width = newSize;
        floatingParams.height = newSize;
        Point screen = screenSize();
        int maxX = Math.max(0, screen.x - newSize);
        int maxY = Math.max(0, screen.y - newSize);
        floatingParams.x = clamp(centerX - newSize / 2, 0, maxX);
        floatingParams.y = clamp(centerY - newSize / 2, 0, maxY);
        floating.setAlpha(settings.getFloatOpacity());
        floating.setBackground(floatingBackground(newSize));
        floating.setPadding(newSize / 4, newSize / 4, newSize / 4, newSize / 4);
        try {
            windowManager.updateViewLayout(floating, floatingParams);
        } catch (Exception ignored) {
        }
    }

    private void dismissSettings() {
        if (settingsDialog == null) return;
        AlertDialog dialog = settingsDialog;
        settingsDialog = null;
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    private int overlaySize() {
        float scale = clamp(settings.getFloatScale(), 0.6f, 1f);
        return Math.max(dp(30), Math.round(dp(52) * scale));
    }

    private Point screenSize() {
        Point size = new Point();
        try {
            windowManager.getDefaultDisplay().getRealSize(size);
        } catch (Exception ignored) {
        }
        if (size.x <= 0 || size.y <= 0) {
            size.x = getResources().getDisplayMetrics().widthPixels;
            size.y = getResources().getDisplayMetrics().heightPixels;
        }
        return size;
    }

    private int overlayWindowType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
