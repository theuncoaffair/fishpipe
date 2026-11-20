package com.theuncoaffair.fishpipe;

import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import com.theuncoaffair.fishpipe.analytics.InsightsRepository;
import com.theuncoaffair.fishpipe.analytics.InsightsResult;
import com.theuncoaffair.fishpipe.analytics.WatchlistDb;
import com.theuncoaffair.fishpipe.databinding.FragmentInsightsBinding;
import com.theuncoaffair.fishpipe.databinding.ItemInsightMetricBinding;
import com.theuncoaffair.fishpipe.databinding.ItemInsightStatBinding;
import com.theuncoaffair.fishpipe.databinding.ItemInsightStatRowBinding;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.ui.insights.InsightFormat;
import com.theuncoaffair.fishpipe.ui.insights.InsightsUiState;
import com.theuncoaffair.fishpipe.ui.insights.SampleAdapter;
import com.theuncoaffair.fishpipe.ui.insights.TagAdapter;
import com.theuncoaffair.fishpipe.ui.insights.WatchlistAdapter;
import com.theuncoaffair.fishpipe.ui.insights.InsightsViewModel;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.MenuDividers;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class InsightsFragment extends Fragment {
    private static final String ARG_QUERY = "insights_query";
    private static final String ARG_MODE = "insights_mode";

    private FragmentInsightsBinding binding;
    private InsightsViewModel viewModel;
    private String pendingQuery = "";
    private final Map<View, View[]> collapsibles = new LinkedHashMap<>();

    private ScrollView scroll;
    private EditText input;
    private TextInputLayout inputLayout;
    private boolean inputActive;
    private View progress;
    private View errorCard;
    private TextView errorText;
    private View results;
    private View profileCard;
    private ImageView profileAvatar;
    private TextView profileName;
    private TextView profileHandle;
    private TextView profileSignature;
    private TextView profileBioLink;
    private TextView profileVerified;
    private LinearLayout profileStats;
    private View profileOpen;
    private Button profileWatch;
    private View videoCard;
    private ImageView videoCover;
    private TextView videoTitle;
    private TextView videoAuthor;
    private TextView videoHashtags;
    private TextView videoMeta;
    private LinearLayout videoStats;
    private LinearLayout videoRates;
    private View videoOpen;
    private View creatorCard;
    private TextView creatorCaption;
    private LinearLayout creatorStats;
    private LinearLayout creatorRates;
    private View soundCard;
    private TextView soundTitle;
    private TextView soundAuthor;
    private LinearLayout soundStats;
    private View soundOpen;
    private View hashtagCard;
    private RecyclerView hashtagContainer;
    private View samplesCard;
    private TextView samplesCaption;
    private RecyclerView samplesContainer;
    private View watchlistCard;
    private View watchlistBody;
    private TextView watchlistTitle;
    private ImageView watchlistChevron;
    private RecyclerView watchlistContainer;
    private TextView watchlistEmpty;
    private ImageButton watchlistMore;
    private TagAdapter hashtagAdapter;
    private SampleAdapter samplesAdapter;
    private WatchlistAdapter watchlistAdapter;
    private boolean watchlistExpanded;
    private View watchSelectionBar;
    private TextView watchSelectionCount;
    private final Set<String> watchSelected = new HashSet<>();
    private boolean watchSelecting;
    private TextView warningText;
    private WatchlistDb watchDb;

    private InsightsResult current;
    private int mode = InsightsRepository.MODE_AUTO;

    @NonNull
    public static InsightsFragment newInstance(@Nullable String query, int mode) {
        InsightsFragment fragment = new InsightsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        args.putInt(ARG_MODE, mode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInsightsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        watchDb = new WatchlistDb(requireContext());
        viewModel = new ViewModelProvider(this).get(InsightsViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::renderState);
        bind();
        setMode(InsightsRepository.MODE_AUTO);
        renderWatchlist();
        Bundle args = getArguments();
        String query = args == null ? pendingQuery : args.getString(ARG_QUERY);
        int requested = args == null ? mode : args.getInt(ARG_MODE, mode);
        setMode(requested);
        if (query != null && !query.trim().isEmpty()) {
            binding.insightsInput.setText(query);
            binding.insightsInput.setSelection(binding.insightsInput.getText().length());
            analyze();
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    public void analyzeQuery(@Nullable String query, int mode) {
        setMode(mode);
        if (binding == null) {
            pendingQuery = query == null ? "" : query;
            return;
        }
        if (query != null && !query.trim().isEmpty()) {
            binding.insightsInput.setText(query);
            binding.insightsInput.setSelection(binding.insightsInput.getText().length());
            analyze();
        }
    }

    private void bind() {
        scroll = binding.insightsScroll;
        input = binding.insightsInput;
        inputLayout = binding.insightsInputLayout;
        progress = binding.insightsProgress;
        input.setOnFocusChangeListener((v, hasFocus) -> {
            inputActive = hasFocus;
            updateInputAction();
        });
        inputLayout.setEndIconOnClickListener(v -> {
            if (input.length() == 0) {
                pasteClipboard();
            } else if (inputActive) {
                input.setText("");
            } else {
                Ui.copy(requireContext(), input.getText().toString());
                Ui.toast(requireContext(), R.string.copied);
            }
            updateInputAction();
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateInputAction();
            }
        });
        updateInputAction();
        errorCard = binding.insightsError;
        errorText = binding.insightsErrorText;
        results = binding.insightsResults;
        profileCard = binding.profileCard;
        profileAvatar = binding.profileAvatar;
        profileName = binding.profileName;
        profileHandle = binding.profileHandle;
        profileSignature = binding.profileSignature;
        profileBioLink = binding.profileBioLink;
        profileVerified = binding.profileVerified;
        profileStats = binding.profileStats;
        profileOpen = binding.btnProfileOpen;
        profileWatch = binding.btnProfileWatch;
        videoCard = binding.videoCard;
        videoCover = binding.videoCover;
        videoTitle = binding.videoTitle;
        videoAuthor = binding.videoAuthor;
        videoHashtags = binding.videoHashtags;
        videoMeta = binding.videoMeta;
        videoStats = binding.videoStats;
        videoRates = binding.videoRates;
        videoOpen = binding.btnVideoOpen;
        creatorCard = binding.creatorCard;
        creatorCaption = binding.creatorCaption;
        creatorStats = binding.creatorStats;
        creatorRates = binding.creatorRates;
        soundCard = binding.soundCard;
        soundTitle = binding.soundTitle;
        soundAuthor = binding.soundAuthor;
        soundStats = binding.soundStats;
        soundOpen = binding.btnSoundOpen;
        hashtagCard = binding.hashtagCard;
        hashtagContainer = binding.hashtagContainer;
        hashtagContainer.setLayoutManager(new LinearLayoutManager(requireContext()));
        hashtagAdapter = new TagAdapter(tag -> Ui.openUrl(requireContext(),
                "https://www.tiktok.com/tag/" + Uri.encode(tag.tag)));
        hashtagContainer.setAdapter(hashtagAdapter);
        samplesCard = binding.samplesCard;
        samplesCaption = binding.samplesCaption;
        samplesContainer = binding.samplesContainer;
        samplesContainer.setLayoutManager(new LinearLayoutManager(requireContext()));
        samplesAdapter = new SampleAdapter(sample -> Ui.openUrl(requireContext(), sample.pageUrl));
        samplesContainer.setAdapter(samplesAdapter);
        watchlistCard = binding.watchlistCard;
        watchlistBody = binding.watchlistBody;
        watchlistTitle = binding.watchlistTitle;
        watchlistChevron = binding.watchlistChevron;
        watchlistContainer = binding.watchlistContainer;
        watchlistContainer.setLayoutManager(new LinearLayoutManager(requireContext()));
        watchlistAdapter = new WatchlistAdapter(new WatchlistAdapter.Listener() {
            @Override
            public void onClick(WatchlistDb.Entry entry) {
                if (watchSelecting) toggleWatch(entry.uniqueId);
            }

            @Override
            public void onLongClick(WatchlistDb.Entry entry) {
                if (!watchSelecting) startWatchSelection(entry.uniqueId);
                else toggleWatch(entry.uniqueId);
            }

            @Override
            public void onMore(View anchor, WatchlistDb.Entry entry) {
                showWatchMenu(anchor, entry);
            }
        });
        watchlistContainer.setAdapter(watchlistAdapter);
        watchlistEmpty = binding.watchlistEmpty;
        watchlistMore = binding.watchlistMore;
        binding.watchlistHeader.setOnClickListener(
                v -> setWatchlistExpanded(!watchlistExpanded));
        setWatchlistExpanded(false);
        watchSelectionBar = binding.watchSelectionBar;
        watchSelectionCount = binding.watchSelectionCount;
        watchlistMore.setOnClickListener(this::showWatchlistMenu);
        binding.btnWatchCancel.setOnClickListener(v -> setWatchSelecting(false));
        binding.btnWatchDelete.setOnClickListener(v -> confirmRemoveSelectedWatch());
        warningText = binding.insightsWarning;

        binding.btnInsightsAnalyze.setOnClickListener(v -> analyze());
        binding.insightsRetry.setOnClickListener(v -> analyze());
        profileOpen.setOnClickListener(v -> {
            if (current != null && current.creator != null) {
                Ui.openUrl(requireContext(), current.creator.pageUrl);
            }
        });
        videoOpen.setOnClickListener(v -> {
            if (current != null && current.video != null) {
                Ui.openUrl(requireContext(), current.video.pageUrl);
            }
        });
        soundOpen.setOnClickListener(v -> {
            if (current != null && current.sound != null) {
                Ui.openUrl(requireContext(), current.sound.pageUrl);
            }
        });
        profileWatch.setOnClickListener(v -> saveWatch());
        makeCollapsible(profileCard, profileStats, binding.profileActions);
        makeCollapsible(videoCard, videoTitle, videoAuthor, videoMeta, videoStats,
                videoRates, videoOpen);
        makeCollapsible(creatorCard, creatorCaption, creatorStats, creatorRates);
        makeCollapsible(soundCard, soundTitle, soundAuthor, soundStats, soundOpen);
        makeCollapsible(hashtagCard, hashtagContainer);
        makeCollapsible(samplesCard, samplesCaption, samplesContainer);
    }

    public boolean hasCollapsibles() {
        return collapsibleCount() > 0;
    }

    public boolean allCollapsed() {
        int total = collapsibleCount();
        return total > 0 && collapsedCount() >= total;
    }

    private int collapsibleCount() {
        int total = 0;
        for (View header : collapsibles.keySet()) {
            if (header.isShown()) total++;
        }
        if (watchlistCard != null && watchlistCard.isShown()) total++;
        return total;
    }

    private int collapsedCount() {
        int count = 0;
        for (Map.Entry<View, View[]> entry : collapsibles.entrySet()) {
            if (entry.getKey().isShown() && isCollapsed(entry.getValue())) count++;
        }
        if (watchlistCard != null && watchlistCard.isShown() && !watchlistExpanded) count++;
        return count;
    }

    private boolean isCollapsed(View... body) {
        for (View view : body) {
            if (view != null && view.getVisibility() == View.VISIBLE) return false;
        }
        return true;
    }

    private void makeCollapsible(View card, View... body) {
        if (card == null || body.length == 0) return;
        if (!(card instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) card;
        if (group.getChildCount() == 0) return;
        View header = group.getChildAt(0);
        collapsibles.put(header, body);
        header.setClickable(true);
        header.setFocusable(true);
        if (header instanceof TextView) {
            TextView title = (TextView) header;
            title.setCompoundDrawablePadding(Ui.dp(requireContext(), 6));
            title.setCompoundDrawableTintList(ColorStateList.valueOf(
                    ThemeManager.attrColor(requireContext(), R.attr.colorOutline)));
        }
        updateCollapseIcon(header, false);
        header.setOnClickListener(v -> toggleCollapsed(header, body));
    }

    private void toggleCollapsed(View header, View... body) {
        boolean collapse = false;
        for (View view : body) {
            if (view != null && view.getVisibility() == View.VISIBLE) {
                collapse = true;
                break;
            }
        }
        for (View view : body) {
            if (view == null) continue;
            if (collapse) {
                view.setTag(R.id.insights_results, view.getVisibility());
                view.setVisibility(View.GONE);
            } else {
                Object previous = view.getTag(R.id.insights_results);
                view.setVisibility(previous instanceof Integer ? (Integer) previous : View.VISIBLE);
            }
        }
        updateCollapseIcon(header, collapse);
        invalidateMenu();
    }

    private void updateCollapseIcon(View header, boolean collapsed) {
        int icon = collapsed ? R.drawable.ic_expand_more : R.drawable.ic_expand_less;
        if (header instanceof TextView) {
            ((TextView) header).setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0);
            return;
        }
        ImageView chevron = collapseChevron(header);
        if (chevron != null) chevron.setImageResource(icon);
    }

    @Nullable
    private ImageView collapseChevron(View header) {
        if (!(header instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) header;
        View chevron = group.findViewById(R.id.profile_collapse);
        if (chevron == null) chevron = group.findViewById(R.id.video_collapse);
        return chevron instanceof ImageView ? (ImageView) chevron : null;
    }

    private void invalidateMenu() {
        if (isAdded()) requireActivity().invalidateOptionsMenu();
    }

    public void collapseAll() {
        for (Map.Entry<View, View[]> entry : collapsibles.entrySet()) {
            collapseBody(entry.getValue());
            updateCollapseIcon(entry.getKey(), true);
        }
        setWatchlistExpanded(false);
        invalidateMenu();
    }

    public void expandAll() {
        for (Map.Entry<View, View[]> entry : collapsibles.entrySet()) {
            expandBody(entry.getValue());
            updateCollapseIcon(entry.getKey(), false);
        }
        setWatchlistExpanded(true);
        invalidateMenu();
    }

    private void expandBody(View... body) {
        for (View view : body) {
            if (view == null) continue;
            Object previous = view.getTag(R.id.insights_results);
            view.setVisibility(previous instanceof Integer ? (Integer) previous : View.VISIBLE);
        }
    }

    private void collapseBody(View... body) {
        for (View view : body) {
            if (view == null || view.getVisibility() != View.VISIBLE) continue;
            view.setTag(R.id.insights_results, view.getVisibility());
            view.setVisibility(View.GONE);
        }
    }

    private void pasteClipboard() {
        String text = Ui.clipboard(requireContext());
        if (text == null || text.trim().isEmpty()) {
            Ui.toast(requireContext(), R.string.clipboard_empty);
            return;
        }
        input.setText(text.trim());
        input.setSelection(input.length());
        input.requestFocus();
    }

    private void updateInputAction() {
        if (inputLayout == null) return;
        if (input.length() == 0) {
            inputLayout.setEndIconDrawable(R.drawable.ic_paste);
            inputLayout.setEndIconContentDescription(getString(R.string.paste_clipboard));
        } else if (inputActive) {
            inputLayout.setEndIconDrawable(R.drawable.ic_delete);
            inputLayout.setEndIconContentDescription(getString(R.string.delete));
        } else {
            inputLayout.setEndIconDrawable(R.drawable.ic_copy);
            inputLayout.setEndIconContentDescription(getString(R.string.copy_link));
        }
    }

    private void setMode(int value) {
        if (value < InsightsRepository.MODE_AUTO || value > InsightsRepository.MODE_HASHTAG) {
            value = InsightsRepository.MODE_AUTO;
        }
        mode = value;
    }

    private void analyze() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) {
            Ui.toast(requireContext(), R.string.insights_input_missing);
            return;
        }
        String normalized = LinkUtils.normalize(query);
        boolean numeric = query.matches("\\d{15,}");
        boolean looksLikeLink = !LinkUtils.findUrl(query).isEmpty() || query.matches(
                "(?i)^(?:https?://)?(?:www\\.)?[\\w-]+(?:\\.[\\w-]+)+(?:/.*)?$");
        if (!numeric && looksLikeLink && !LinkUtils.isSupported(normalized)) {
            Ui.toast(requireContext(), R.string.invalid_link);
            return;
        }
        Ui.hideKeyboard(requireActivity());
        input.clearFocus();
        viewModel.analyze(query, mode);
    }

    private void renderState(InsightsUiState state) {
        if (state.isLoading()) {
            setBusy(true);
            return;
        }
        setBusy(false);
        if (state.getError() != null) {
            errorCard.setVisibility(View.VISIBLE);
            results.setVisibility(View.GONE);
            String message = state.getError();
            errorText.setText(message == null || message.trim().isEmpty()
                    ? getString(R.string.insights_error) : message);
            invalidateMenu();
            return;
        }
        if (state.getResult() != null) {
            render(state.getResult());
        }
    }

    private void setBusy(boolean busy) {
        Button analyze = binding.btnInsightsAnalyze;
        analyze.setEnabled(!busy);
        analyze.setText(busy ? R.string.insights_analyzing : R.string.insights_analyze);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) errorCard.setVisibility(View.GONE);
    }

    private void render(InsightsResult result) {
        current = result;
        errorCard.setVisibility(View.GONE);
        results.setVisibility(View.VISIBLE);
        renderProfile(result.creator);
        renderVideo(result);
        renderCreator(result);
        if (result.mode != InsightsRepository.MODE_HASHTAG) renderSound(result.sound);
        else soundCard.setVisibility(View.GONE);
        renderHashtags(result.hashtags);
        renderSamples(result.samples, result.sampleVideos);
        renderWarning(result.warning);
        renderWatchlist();
        invalidateMenu();
        scroll.post(() -> scroll.smoothScrollTo(0, results.getTop()));
    }

    private void bindExpandable(final TextView text, final int collapsedLines) {
        if (text == null) return;
        text.setMaxLines(collapsedLines);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setClickable(false);
        text.setFocusable(false);
        text.setOnClickListener(null);
        final boolean[] expanded = {false};
        text.post(() -> {
            if (text.getWidth() <= 0) return;
            text.setMaxLines(Integer.MAX_VALUE);
            text.setEllipsize(null);
            text.measure(View.MeasureSpec.makeMeasureSpec(text.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int lines = text.getLayout() == null ? 0 : text.getLayout().getLineCount();
            boolean more = lines > collapsedLines;
            text.setMaxLines(more ? collapsedLines : Integer.MAX_VALUE);
            text.setEllipsize(more ? TextUtils.TruncateAt.END : null);
            text.setClickable(more);
            text.setFocusable(more);
            if (!more) return;
            text.setOnClickListener(v -> {
                expanded[0] = !expanded[0];
                text.setMaxLines(expanded[0] ? Integer.MAX_VALUE : collapsedLines);
                text.setEllipsize(expanded[0] ? null : TextUtils.TruncateAt.END);
            });
        });
    }

    private void renderProfile(InsightsResult.Profile profile) {
        if (profile == null) {
            profileCard.setVisibility(View.GONE);
            return;
        }
        profileCard.setVisibility(View.VISIBLE);
        String name = profile.nickname;
        if (name == null || name.trim().isEmpty()) name = profile.uniqueId;
        if (name == null || name.trim().isEmpty()) name = getString(R.string.insights_creator_section);
        profileName.setText(name);
        String handle = profile.uniqueId;
        if (handle == null || handle.trim().isEmpty()) handle = "";
        else if (!handle.startsWith("@")) handle = "@" + handle;
        profileHandle.setText(handle);
        profileSignature.setText(profile.signature == null ? "" : profile.signature);
        profileSignature.setVisibility(TextUtils.isEmpty(profileSignature.getText()) ? View.GONE : View.VISIBLE);
        bindExpandable(profileSignature, 2);
        String bioLink = profile.bioLink == null ? "" : profile.bioLink.trim();
        profileBioLink.setText(bioLink);
        profileBioLink.setVisibility(TextUtils.isEmpty(bioLink) ? View.GONE : View.VISIBLE);
        profileBioLink.setOnClickListener(v -> {
            String target = profileBioLink.getText().toString();
            if (!target.isEmpty()) Ui.openUrl(requireContext(), target);
        });
        profileVerified.setVisibility(profile.verified ? View.VISIBLE : View.GONE);
        ImageLoader.load(profileAvatar, profile.avatar, R.drawable.ic_explore);
        profileStats.removeAllViews();
        addStatRows(profileStats, new String[]{
                getString(R.string.insights_followers),
                getString(R.string.insights_following),
                getString(R.string.insights_hearts),
                getString(R.string.insights_videos)
        }, new String[]{
                InsightFormat.compact(profile.followers),
                InsightFormat.compact(profile.following),
                InsightFormat.compact(profile.hearts),
                InsightFormat.compact(profile.videos)
        }, 2);
        profileOpen.setVisibility(TextUtils.isEmpty(profile.pageUrl) ? View.GONE : View.VISIBLE);
        profileWatch.setVisibility(TextUtils.isEmpty(profile.uniqueId) ? View.GONE : View.VISIBLE);
        updateWatchButton();
    }

    private void renderVideo(InsightsResult result) {
        MediaItem video = result.video;
        if (video == null) {
            videoCard.setVisibility(View.GONE);
            return;
        }
        videoCard.setVisibility(View.VISIBLE);
        StringBuilder byline = new StringBuilder(video.author == null || video.author.trim().isEmpty()
                ? getString(R.string.unknown_author)
                : getString(R.string.by_author, video.author));
        if (video.createTime > 0) {
            byline.append("  •  ").append(Ui.formatUploadTime(requireContext(), video.createTime * 1000L));
        }
        videoAuthor.setText(byline);
        String caption = video.captionFromTitle();
        videoTitle.setText(caption);
        bindExpandable(videoTitle, 2);
        String hashtags = video.hashtagsFromTitle();
        videoHashtags.setText(hashtags);
        videoHashtags.setVisibility(TextUtils.isEmpty(hashtags) ? View.GONE : View.VISIBLE);
        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(video.region)) {
            meta.append(getString(R.string.insights_region, video.region));
        }
        if (video.durationSeconds > 0) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(formatDuration(video.durationSeconds));
        }
        videoMeta.setText(meta);
        videoMeta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);
        videoMeta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);
        ImageLoader.load(videoCover, video.coverUrl, R.drawable.ic_video);

        videoStats.removeAllViews();
        addStatRows(videoStats, new String[]{
                getString(R.string.insights_views),
                getString(R.string.insights_likes),
                getString(R.string.insights_comments),
                getString(R.string.insights_shares),
                getString(R.string.insights_saves),
                getString(R.string.insights_engagement)
        }, new String[]{
                InsightFormat.compact(result.views),
                InsightFormat.compact(result.likes),
                InsightFormat.compact(result.comments),
                InsightFormat.compact(result.shares),
                InsightFormat.compact(result.saves),
                percent(result.engagementRate)
        }, 3);

        videoRates.removeAllViews();
        addMetric(videoRates, getString(R.string.insights_like_rate), percent(result.likeRate), 0);
        addMetric(videoRates, getString(R.string.insights_save_rate), percent(result.saveRate), 0);
        addMetric(videoRates, getString(R.string.insights_share_rate), percent(result.shareRate), 0);
        addMetric(videoRates, getString(R.string.insights_comment_rate), percent(result.commentRate), 0);
        if (result.viewRate >= 0) {
            addMetric(videoRates, getString(R.string.insights_view_rate), percent(result.viewRate), 0);
        }
        if (!Double.isNaN(result.compareAvgViews)) {
            int color = result.compareAvgViews > 0 ? requireContext().getColor(R.color.success)
                    : result.compareAvgViews < 0 ? requireContext().getColor(R.color.error) : 0;
            addMetric(videoRates, getString(R.string.insights_compare_avg),
                    signedPercent(result.compareAvgViews), color);
        }
        videoOpen.setVisibility(TextUtils.isEmpty(video.pageUrl) ? View.GONE : View.VISIBLE);
    }

    private void renderCreator(InsightsResult result) {
        if (result.creator == null) {
            creatorCard.setVisibility(View.GONE);
            return;
        }
        creatorCard.setVisibility(View.VISIBLE);
        if (result.sampleVideos <= 0) {
            creatorCaption.setText(R.string.insights_no_samples);
            creatorStats.setVisibility(View.GONE);
            creatorRates.setVisibility(View.GONE);
            return;
        }
        creatorCaption.setText(getString(R.string.insights_sample_count, result.sampleVideos));
        creatorStats.setVisibility(View.VISIBLE);
        creatorStats.removeAllViews();
        addStatRows(creatorStats, new String[]{
                getString(R.string.insights_avg_views),
                getString(R.string.insights_avg_likes),
                getString(R.string.insights_avg_comments),
                getString(R.string.insights_avg_shares),
                getString(R.string.insights_avg_engagement),
                getString(R.string.insights_posts_week)
        }, new String[]{
                InsightFormat.compact(result.avgViews),
                InsightFormat.compact(result.avgLikes),
                InsightFormat.compact(result.avgComments),
                InsightFormat.compact(result.avgShares),
                percent(result.avgEngagement),
                String.format(Locale.US, "%.1f", result.postsPerWeek)
        }, 3);
        creatorRates.removeAllViews();
        creatorRates.setVisibility(View.GONE);
    }

    private void renderSound(InsightsResult.SoundStat sound) {
        if (sound == null) {
            soundCard.setVisibility(View.GONE);
            return;
        }
        soundCard.setVisibility(View.VISIBLE);
        String title = sound.title;
        if (title == null || title.trim().isEmpty()) title = getString(R.string.insights_sound_section);
        soundTitle.setText(title);
        soundAuthor.setText(sound.author == null || sound.author.trim().isEmpty()
                ? "" : getString(R.string.by_author, sound.author));
        soundAuthor.setVisibility(TextUtils.isEmpty(soundAuthor.getText()) ? View.GONE : View.VISIBLE);
        soundStats.removeAllViews();
        addStatRows(soundStats, new String[]{
                getString(R.string.insights_sample_uses),
                getString(R.string.insights_sample_views),
                getString(R.string.insights_avg_engagement)
        }, new String[]{
                InsightFormat.compact(sound.sampleUses),
                InsightFormat.compact(sound.totalViews),
                percent(sound.avgEngagement)
        }, 3);
        soundOpen.setVisibility(TextUtils.isEmpty(sound.pageUrl) ? View.GONE : View.VISIBLE);
    }

    private void renderHashtags(List<InsightsResult.TagStat> tags) {
        if (tags == null || tags.isEmpty()) {
            hashtagAdapter.submitList(java.util.Collections.emptyList());
            hashtagCard.setVisibility(View.GONE);
            return;
        }
        hashtagCard.setVisibility(View.VISIBLE);
        int count = Math.min(tags.size(), 8);
        hashtagAdapter.submitList(new java.util.ArrayList<>(tags.subList(0, count)));
    }

    private void renderSamples(List<InsightsResult.SampleVideo> samples, int total) {
        if (samples == null || samples.isEmpty()) {
            samplesAdapter.submitList(java.util.Collections.emptyList());
            samplesCard.setVisibility(View.GONE);
            return;
        }
        samplesCard.setVisibility(View.VISIBLE);
        samplesCaption.setText(getString(R.string.insights_sample_count, total));
        samplesAdapter.submitList(samples);
    }

    private void renderWarning(String warning) {
        if (warning == null || warning.trim().isEmpty()) {
            warningText.setVisibility(View.GONE);
            return;
        }
        warningText.setVisibility(View.VISIBLE);
        warningText.setText(getString(R.string.insights_limited, warning));
    }

    private void setWatchlistExpanded(boolean expanded) {
        watchlistExpanded = expanded;
        watchlistBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        watchlistChevron.setRotation(expanded ? 180f : 0f);
        invalidateMenu();
    }

    private void renderWatchlist() {
        List<WatchlistDb.Entry> entries = watchDb.list();
        if (watchSelecting) {
            Set<String> live = new HashSet<>();
            for (WatchlistDb.Entry entry : entries) live.add(entry.uniqueId);
            watchSelected.retainAll(live);
            if (watchSelected.isEmpty()) watchSelecting = false;
        }
        if (entries.isEmpty()) {
            watchSelecting = false;
            watchSelected.clear();
            watchlistCard.setVisibility(View.VISIBLE);
            watchlistTitle.setText(R.string.insights_watchlist_section);
            setWatchlistExpanded(true);
            watchlistEmpty.setVisibility(View.VISIBLE);
            watchlistAdapter.submitList(java.util.Collections.emptyList());
            updateWatchSelection();
            return;
        }
        watchlistCard.setVisibility(View.VISIBLE);
        watchlistTitle.setText(getString(R.string.insights_watchlist_section_count, entries.size()));
        setWatchlistExpanded(watchlistExpanded);
        watchlistEmpty.setVisibility(View.GONE);
        watchlistAdapter.setSelection(watchSelected, watchSelecting);
        watchlistAdapter.submitList(entries);
        updateWatchSelection();
    }

    private void showWatchlistMenu(View anchor) {
        List<WatchlistDb.Entry> entries = watchDb.list();
        if (entries.isEmpty()) return;
        boolean all = watchSelected.size() >= entries.size();
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 10, 0, all ? R.string.deselect_all : R.string.select_all);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 10) {
                if (all) {
                    setWatchSelecting(false);
                } else {
                    watchSelecting = true;
                    watchSelected.clear();
                    for (WatchlistDb.Entry entry : entries) watchSelected.add(entry.uniqueId);
                    renderWatchlist();
                }
            }
            return true;
        });
        popup.show();
    }

    private void setWatchSelecting(boolean value) {
        watchSelecting = value;
        if (!value) watchSelected.clear();
        renderWatchlist();
    }

    private void startWatchSelection(String uniqueId) {
        watchSelecting = true;
        watchSelected.clear();
        watchSelected.add(uniqueId);
        renderWatchlist();
    }

    private void toggleWatch(String uniqueId) {
        if (watchSelected.contains(uniqueId)) watchSelected.remove(uniqueId);
        else watchSelected.add(uniqueId);
        if (watchSelected.isEmpty()) watchSelecting = false;
        renderWatchlist();
    }

    private void updateWatchSelection() {
        watchSelectionBar.setVisibility(watchSelecting ? View.VISIBLE : View.GONE);
        watchSelectionCount.setText(getString(R.string.selected_count, watchSelected.size()));
    }

    private void confirmRemoveSelectedWatch() {
        if (watchSelected.isEmpty()) {
            Ui.toast(requireContext(), R.string.no_items_selected);
            return;
        }
        Ui.confirm(requireContext(), getString(R.string.delete_choice_title),
                getString(R.string.insights_remove_watch_selected_body),
                getString(R.string.delete_selected), (dialog, which) -> {
                    int count = 0;
                    for (String uniqueId : new java.util.ArrayList<>(watchSelected)) {
                        watchDb.delete(uniqueId);
                        count++;
                    }
                    watchSelecting = false;
                    watchSelected.clear();
                    updateWatchButton();
                    renderWatchlist();
                    Ui.toast(requireContext(), getString(R.string.deleted_count, count));
                });
    }

    private String creatorUrl(WatchlistDb.Entry entry) {
        return "https://www.tiktok.com/@" + Uri.encode(entry.uniqueId);
    }

    private void analyzeWatchEntry(WatchlistDb.Entry entry) {
        input.setText(creatorUrl(entry));
        input.setSelection(input.getText().length());
        setMode(InsightsRepository.MODE_CREATOR);
        analyze();
    }

    private void showWatchMenu(View anchor, WatchlistDb.Entry entry) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.insights_open_profile);
        popup.getMenu().add(0, 2, 1, R.string.insights_analyze);
        popup.getMenu().add(1, 3, 0, R.string.delete);
        MenuDividers.enable(popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                Ui.openUrl(requireContext(), creatorUrl(entry));
            } else if (item.getItemId() == 2) {
                analyzeWatchEntry(entry);
            } else if (item.getItemId() == 3) {
                confirmRemoveWatch(entry);
            }
            return true;
        });
        popup.show();
    }

    private void confirmRemoveWatch(WatchlistDb.Entry entry) {
        Ui.confirm(requireContext(), getString(R.string.insights_remove_watch_title),
                getString(R.string.insights_remove_watch_body),
                getString(R.string.delete), (dialog, which) -> {
                    watchDb.delete(entry.uniqueId);
                    updateWatchButton();
                    renderWatchlist();
                });
    }

    private void saveWatch() {
        if (current == null || current.creator == null) return;
        if (watchDb.save(current.creator)) {
            Ui.toast(requireContext(), R.string.insights_snapshot_saved);
            updateWatchButton();
            renderWatchlist();
        } else {
            Ui.toast(requireContext(), R.string.error_generic);
        }
    }

    private void updateWatchButton() {
        boolean tracked = false;
        if (current != null && current.creator != null) {
            for (WatchlistDb.Entry entry : watchDb.list()) {
                if (entry.uniqueId.equalsIgnoreCase(current.creator.uniqueId)) {
                    tracked = true;
                    break;
                }
            }
        }
        profileWatch.setText(tracked ? R.string.insights_watch_update : R.string.insights_watch);
    }

    private void addStatRows(LinearLayout container, String[] labels, String[] values, int columns) {
        if (columns < 1) columns = 1;
        int index = 0;
        boolean first = true;
        while (index < labels.length) {
            ItemInsightStatRowBinding row = ItemInsightStatRowBinding.inflate(
                    getLayoutInflater(), container, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (!first) params.topMargin = Ui.dp(requireContext(), 10);
            row.getRoot().setLayoutParams(params);
            for (int column = 0; column < columns && index < labels.length; column++, index++) {
                ItemInsightStatBinding cell = ItemInsightStatBinding.inflate(
                        getLayoutInflater(), row.getRoot(), false);
                cell.statValue.setText(values[index]);
                cell.statLabel.setText(labels[index]);
                row.getRoot().addView(cell.getRoot());
            }
            container.addView(row.getRoot());
            first = false;
        }
    }

    private void addMetric(LinearLayout container, String label, String value, int color) {
        ItemInsightMetricBinding line = ItemInsightMetricBinding.inflate(
                getLayoutInflater(), container, false);
        line.metricLabel.setText(label);
        line.metricValue.setText(value);
        line.metricValue.setTextColor(color == 0
                ? ThemeManager.attrColor(requireContext(), R.attr.colorOnSurface)
                : color);
        container.addView(line.getRoot());
    }


    private String percent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) return "—";
        return String.format(Locale.US, "%.1f%%", value);
    }

    private String signedPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "—";
        return String.format(Locale.US, "%+.1f%%", value);
    }


    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long rest = seconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, rest);
    }
}
