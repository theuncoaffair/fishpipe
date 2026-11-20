package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public final class HashtagTextView extends AppCompatTextView {
    private static final float MAX_WIDTH_FRACTION = 0.75f;

    public HashtagTextView(Context context) {
        this(context, null);
    }

    public HashtagTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HashtagTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int size = MeasureSpec.getSize(widthMeasureSpec);
        if (mode != MeasureSpec.UNSPECIFIED && size > 0) {
            int limit = (int) (size * MAX_WIDTH_FRACTION);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
