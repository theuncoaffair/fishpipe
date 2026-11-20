package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.view.MotionEvent;

public final class EdgeSwipeDetector {
    public interface Listener {
        void onSwipe(int direction);
    }

    private final int edgePx;
    private final int thresholdPx;
    private final int widthPx;
    private final Listener listener;
    private float downX;
    private float downY;
    private boolean tracking;
    private boolean swallowing;

    public EdgeSwipeDetector(Context context, Listener listener) {
        float density = context.getResources().getDisplayMetrics().density;
        edgePx = Math.round(40f * density);
        thresholdPx = Math.round(48f * density);
        widthPx = context.getResources().getDisplayMetrics().widthPixels;
        this.listener = listener;
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swallowing = false;
                downX = event.getRawX();
                downY = event.getRawY();
                tracking = downX <= edgePx || downX >= widthPx - edgePx;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (swallowing) return true;
                if (!tracking) return false;
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (Math.abs(dx) < thresholdPx || Math.abs(dx) <= Math.abs(dy)) return false;
                if (downX <= edgePx && dx > 0) {
                    tracking = false;
                    swallowing = true;
                    listener.onSwipe(-1);
                    return true;
                }
                if (downX >= widthPx - edgePx && dx < 0) {
                    tracking = false;
                    swallowing = true;
                    listener.onSwipe(1);
                    return true;
                }
                tracking = false;
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean consumed = swallowing;
                tracking = false;
                swallowing = false;
                return consumed;
            default:
                return false;
        }
    }
}
