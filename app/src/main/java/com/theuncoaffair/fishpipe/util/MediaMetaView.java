package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.databinding.ViewMediaMetaBinding;

public final class MediaMetaView extends ScrollView {
    private final int maxHeight;
    private LinearLayout creatorRow;
    private TextView creator;
    private TextView dot;
    private TextView date;
    private TextView caption;
    private TextView hashtags;
    private boolean expanded;
    private boolean hasContent;
    private boolean collapsible;

    public MediaMetaView(Context context) {
        this(context, null);
    }

    public MediaMetaView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaMetaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        maxHeight = context.getResources().getDisplayMetrics().heightPixels * 3 / 4;
        setFillViewport(false);
        setVerticalScrollBarEnabled(true);
        setScrollbarFadingEnabled(true);
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        ViewMediaMetaBinding binding = ViewMediaMetaBinding.inflate(LayoutInflater.from(context), this, true);
        creatorRow = binding.metaCreatorRow;
        creator = binding.metaCreator;
        dot = binding.metaDot;
        date = binding.metaDate;
        caption = binding.metaCaption;
        hashtags = binding.metaHashtags;
        creatorRow.setOnClickListener(v -> {
            if (collapsible) toggleExpanded();
        });
        caption.setOnClickListener(v -> {
            if (collapsible) toggleExpanded();
        });
        setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean bind(String author, long uploadedAt, String captionText, String hashtagsText) {
        String creatorText = clean(author);
        String dateText = Ui.formatUploadTime(getContext(), uploadedAt);
        String captionValue = clean(captionText);
        String hashtagsValue = clean(hashtagsText);

        creator.setText(creatorText);
        creator.setVisibility(creatorText.isEmpty() ? GONE : VISIBLE);
        date.setText(dateText);
        date.setVisibility(dateText.isEmpty() ? GONE : VISIBLE);
        dot.setVisibility(!creatorText.isEmpty() && !dateText.isEmpty() ? VISIBLE : GONE);
        creatorRow.setVisibility(creatorText.isEmpty() && dateText.isEmpty() ? GONE : VISIBLE);

        caption.setText(captionValue);
        caption.setVisibility(captionValue.isEmpty() ? GONE : VISIBLE);
        hashtags.setText(hashtagsValue);
        hashtags.setVisibility(hashtagsValue.isEmpty() ? GONE : VISIBLE);

        hashtags.setMaxLines(Integer.MAX_VALUE);
        hashtags.setEllipsize(null);
        collapsible = false;
        expanded = false;
        caption.setMaxLines(Integer.MAX_VALUE);
        caption.setEllipsize(null);
        caption.post(() -> {
            if (caption.getLayout() == null) return;
            collapsible = caption.getLayout().getLineCount() > 2;
            if (collapsible) {
                setExpanded(false);
            } else {
                caption.setMaxLines(Integer.MAX_VALUE);
                caption.setEllipsize(null);
                requestLayout();
            }
        });

        hasContent = !creatorText.isEmpty() || !dateText.isEmpty()
                || !captionValue.isEmpty() || !hashtagsValue.isEmpty();
        setVisibility(hasContent ? VISIBLE : GONE);
        return hasContent;
    }

    private void toggleExpanded() {
        if (!hasContent) return;
        setExpanded(!expanded);
    }

    private void setExpanded(boolean value) {
        expanded = value;
        if (!collapsible) {
            caption.setMaxLines(Integer.MAX_VALUE);
            caption.setEllipsize(null);
        } else {
            caption.setMaxLines(value ? Integer.MAX_VALUE : 2);
            caption.setEllipsize(value ? null : TextUtils.TruncateAt.END);
        }
        if (!value) scrollTo(0, 0);
        requestLayout();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
