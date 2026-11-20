package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

public final class HalfScreenScrollView extends ScrollView {
    private float maxHeightFraction = 0.5f;

    public HalfScreenScrollView(Context context) {
        this(context, null);
    }

    public HalfScreenScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HalfScreenScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMaxHeightFraction(float fraction) {
        float clamped = Math.max(0.2f, Math.min(1f, fraction));
        if (clamped == maxHeightFraction) return;
        maxHeightFraction = clamped;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels
                * maxHeightFraction);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
