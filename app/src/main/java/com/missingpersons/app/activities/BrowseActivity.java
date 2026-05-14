package com.missingpersons.app.activities;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.missingpersons.app.R;
import com.missingpersons.app.models.ReportEntity;
import com.missingpersons.app.utils.AbuseReportHelper;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.LanguageHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.nativead.MediaView;
import java.util.Set;
import java.util.HashSet;

import com.missingpersons.app.utils.AdsManager;

import com.missingpersons.app.data.repository.ReportRepository;

/**
 * BrowseActivity — تصفح الحالات
 * 
 * ✅ Native Ads داخل RecyclerView كل 20 عنصر
 * ✅ فلتر المحافظات في BottomSheet
 * ✅ أيقونة خريطة للعرض على الخريطة
 * ✅ Offline-first مع Room
 */
public class BrowseActivity extends AppCompatActivity {

    private static final String[] FIREBASE_NODES = {"reports", "found_persons", "sightings"};
    private static final long     DEBOUNCE_MS    = 300L;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    private RecyclerView            rvBrowse;
    private LinearProgressIndicator progressSync;
    private View                    layoutEmptyState;
    private TextView                tvEmpty;
    private TextView                tvCount;
    private View                    llOfflineBanner;
    private ChipGroup               cgType;

    private BrowseViewModel viewModel;
    private BrowseAdapter   adapter;
    private boolean isGrid = false;

    // [إصلاح] مؤشر الفلتر النشط
    private TextView tvFilterIndicator;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        // Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            View appBar = findViewById(R.id.app_bar);
            if (appBar != null) appBar.setPadding(0, top, 0, 0);
            View rv = findViewById(R.id.rv_browse);
            if (rv != null) rv.setPadding(
                rv.getPaddingLeft(), rv.getPaddingTop(),
                rv.getPaddingRight(), bot);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(BrowseViewModel.class);

        bindViews();
        setupBackButton();
        setupSearch();
        setupTypeChips();
        setupFilterButton();
        setupToggleView();
        setupRecycler();
        setupResetFiltersButton();
        setupMapButton();  // أيقونة الخريطة
        observeViewModel();
        setupNetworkCallback();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerNetworkCallback();
        boolean online = isOnline();
        if (online) {
            ensureAuthAndSync();
        } else {
            showOfflineBanner(true);
            adapter.setSkeletonMode(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // [إصلاح خطأ-01] forceSync عند كل عودة للشاشة
        // يضمن أن Room محدَّث بأحدث البيانات من Firebase
        if (isOnline() && FirebaseAuth.getInstance().getCurrentUser() != null) {
            android.util.Log.d("BrowseActivity", "onResume → forceSync()");
            viewModel.forceSync();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterNetworkCallback();
        debounceHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) adapter.destroyAds();
    }

    private void bindViews() {
        rvBrowse         = findViewById(R.id.rv_browse);
        progressSync     = findViewById(R.id.progress_sync);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvEmpty          = findViewById(R.id.tv_empty);
        tvCount          = findViewById(R.id.tv_count);
        llOfflineBanner  = findViewById(R.id.ll_offline_banner);
        cgType           = findViewById(R.id.cg_type);
        // [إصلاح] مؤشر الفلتر النشط
        tvFilterIndicator = findViewById(R.id.tv_active_filter);
    }

    private void setupBackButton() {
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void setupMapButton() {
        View btnMap = findViewById(R.id.btn_map_view);
        if (btnMap != null)
            btnMap.setOnClickListener(v ->
                startActivity(new Intent(this, MapActivity.class)));
    }

    private void setupResetFiltersButton() {
        View btnReset = findViewById(R.id.btn_reset_filters);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                viewModel.resetFilters();
                if (cgType != null) cgType.check(R.id.chip_all);
                TextInputEditText et = findViewById(R.id.et_search);
                if (et != null) et.setText("");
                // [إصلاح] إخفاء مؤشر الفلتر عند الإعادة
                updateFilterIndicator(null, null);
            });
        }
    }

    private void setupSearch() {
        TextInputEditText etSearch = findViewById(R.id.et_search);
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                debounceHandler.removeCallbacksAndMessages(null);
                String q = s.toString().trim().toLowerCase();
                debounceHandler.postDelayed(() -> viewModel.setSearch(q), DEBOUNCE_MS);
            }
        });
    }

    private void setupTypeChips() {
        if (cgType == null) return;
        cgType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String type = "all";
            if      (id == R.id.chip_missing)   type = "missing";
            else if (id == R.id.chip_found)      type = "found";
            else if (id == R.id.chip_sighting)   type = "sighting";
            else if (id == R.id.chip_emergency)  type = "emergency";
            viewModel.setType(type);
            runLayoutAnimation();
        });
    }

    private void setupFilterButton() {
        View btnFilter = findViewById(R.id.btn_filter);
        if (btnFilter == null) return;
        btnFilter.setOnClickListener(v -> {
            // [إصلاح] تمرير الفلتر الحالي للـ fragment ليعرضه
            String curGov  = viewModel.govFilter.getValue();
            int    curSort = viewModel.sortMode.getValue() != null ? viewModel.sortMode.getValue() : 0;
            String curSortStr = curSort == BrowseViewModel.SORT_SMART ? "smart"
                              : curSort == BrowseViewModel.SORT_NEAREST ? "nearest" : "newest";
            FilterBottomSheetFragment sheet = FilterBottomSheetFragment.newInstance(
                curGov != null && !curGov.equals("all") ? curGov : "الكل", curSortStr);

            // [إصلاح] ربط الـ listener — كان مفقوداً تماماً، ولهذا الفلترة لا تعمل
            sheet.setOnFiltersAppliedListener(new FilterBottomSheetFragment.OnFiltersAppliedListener() {
                @Override
                public void onFiltersApplied(String governorate, String sortOrder) {
                    // تطبيق فلتر المحافظة
                    viewModel.setGov(governorate != null ? governorate : "all");

                    // تحويل sortOrder String → int constant
                    if ("newest".equals(sortOrder)) {
                        viewModel.setSort(BrowseViewModel.SORT_NEWEST);
                    } else if ("nearest".equals(sortOrder)) {
                        viewModel.setSort(BrowseViewModel.SORT_NEAREST);
                    } else {
                        viewModel.setSort(BrowseViewModel.SORT_SMART);
                    }

                    // تحديث مؤشر الفلتر في الـ header
                    updateFilterIndicator(governorate, sortOrder);
                    runLayoutAnimation();
                }

                @Override
                public void onFiltersReset() {
                    viewModel.resetFilters();
                    updateFilterIndicator(null, null);
                    if (cgType != null) cgType.check(R.id.chip_all);
                    TextInputEditText et = findViewById(R.id.et_search);
                    if (et != null) et.setText("");
                    runLayoutAnimation();
                }
            });

            sheet.show(getSupportFragmentManager(), "filter_sheet");
        });
    }

    /**
     * [إصلاح] يعرض مؤشراً في الـ header يوضح الفلتر النشط
     * مثال: "📍 القاهرة | ترتيب: الأحدث"
     */
    private void updateFilterIndicator(String governorate, String sortOrder) {
        if (tvFilterIndicator == null) return;

        boolean hasGov  = (governorate != null && !governorate.isEmpty());
        boolean hasSort = (sortOrder != null && !"smart".equals(sortOrder));

        if (!hasGov && !hasSort) {
            tvFilterIndicator.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (hasGov)  sb.append("📍 ").append(governorate);
        if (hasGov && hasSort) sb.append("  ·  ");
        if (hasSort) {
            sb.append("newest".equals(sortOrder) ? "🕒 الأحدث" : "📏 الأقرب");
        }

        tvFilterIndicator.setText(sb.toString());
        tvFilterIndicator.setVisibility(View.VISIBLE);
    }

    private void setupToggleView() {
        View btnToggle = findViewById(R.id.btn_toggle_view);
        if (btnToggle == null) return;
        btnToggle.setOnClickListener(v -> {
            isGrid = !isGrid;
            rvBrowse.setLayoutManager(isGrid
                ? new GridLayoutManager(this, 2)
                : new LinearLayoutManager(this));
            runLayoutAnimation();
        });
    }

    private void setupRecycler() {
        adapter = new BrowseAdapter(
            r -> startActivity(
                new Intent(this, CaseDetailActivity.class)
                    .putExtra("reportId", r.reportId)),
            r -> AbuseReportHelper.showReportDialog(
                this, AbuseReportHelper.ReportTarget.REPORT,
                r.reportId, "بلاغ: " + r.personName),
            r -> com.missingpersons.app.utils.ShareHelper.shareReport(
                this,
                r.personName != null ? r.personName : "",
                r.manualAddress != null ? r.manualAddress : (r.governorate != null ? r.governorate : ""),
                r.personAge != null ? r.personAge : "",
                r.personGender != null ? r.personGender : "",
                r.timestamp,
                r.reportId,
                null)
        );
        adapter.setContext(this);

        rvBrowse.setLayoutManager(new LinearLayoutManager(this));
        rvBrowse.setHasFixedSize(false);

        DefaultItemAnimator anim = new DefaultItemAnimator();
        anim.setAddDuration(200);
        anim.setChangeDuration(200);
        anim.setRemoveDuration(150);
        rvBrowse.setItemAnimator(anim);
        rvBrowse.setAdapter(adapter);

        rvBrowse.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || adapter.isShowingSkeleton()) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                int last = -1;
                if (lm instanceof GridLayoutManager)
                    last = ((GridLayoutManager) lm).findLastCompletelyVisibleItemPosition();
                else if (lm instanceof LinearLayoutManager)
                    last = ((LinearLayoutManager) lm).findLastCompletelyVisibleItemPosition();
                if (last >= adapter.getItemCount() - 3)
                    viewModel.loadMore();
            }
        });
    }

    private void observeViewModel() {
        viewModel.reports.observe(this, rawList -> {
            List<ReportEntity> sorted = viewModel.sortedList(rawList);
            boolean syncing = Boolean.TRUE.equals(viewModel.isSyncing.getValue());

            if (sorted.isEmpty() && syncing) return;

            adapter.setSkeletonMode(false);
            adapter.updateData(sorted);

            int count = sorted.size();
            if (tvCount != null)
                tvCount.setText(count > 0 ? "الحالات: " + count : "");
            if (layoutEmptyState != null) {
                layoutEmptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                if (count == 0) updateContextualEmptyState();
            }
        });

        viewModel.isSyncing.observe(this, syncing -> {
            if (progressSync != null)
                progressSync.setVisibility(Boolean.TRUE.equals(syncing)
                    ? View.VISIBLE : View.GONE);

            if (!Boolean.TRUE.equals(syncing)) {
                adapter.setSkeletonMode(false);
                List<ReportEntity> cur = viewModel.reports.getValue();
                if (cur != null) {
                    List<ReportEntity> sorted = viewModel.sortedList(cur);
                    adapter.updateData(sorted);
                    int count = sorted.size();
                    if (tvCount != null)
                        tvCount.setText(count > 0 ? "الحالات: " + count : "");
                    if (layoutEmptyState != null) {
                        layoutEmptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                        if (count == 0) updateContextualEmptyState();
                    }
                }
            }
        });

        viewModel.isOffline.observe(this, offline ->
            showOfflineBanner(Boolean.TRUE.equals(offline)));

        viewModel.isLoadingMore.observe(this, loading -> {
            if (progressSync != null)
                progressSync.setVisibility(
                    (Boolean.TRUE.equals(loading) || Boolean.TRUE.equals(viewModel.isSyncing.getValue()))
                    ? View.VISIBLE : View.GONE);
        });
    }

    private void ensureAuthAndSync() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            viewModel.syncInitial();
            return;
        }
        adapter.setSkeletonMode(true);
        auth.signInAnonymously()
            .addOnSuccessListener(r -> viewModel.syncInitial())
            .addOnFailureListener(e -> {
                adapter.setSkeletonMode(false);
                viewModel.isSyncing.postValue(false);
            });
    }





    private void setupNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    showOfflineBanner(false);
                    viewModel.isOffline.postValue(false);
                    ensureAuthAndSync();
                });
            }
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    showOfflineBanner(true);
                    viewModel.isOffline.postValue(true);
                });
            }
        };
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null)
                cm.registerNetworkCallback(
                    new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    networkCallback);
        } catch (Exception ignored) {}
    }

    private void unregisterNetworkCallback() {
        try {
            ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {}
    }

    private boolean isOnline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
            getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkCapabilities nc =
            cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            || nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            || nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private boolean isCurrentlyOffline = false;

    private void showOfflineBanner(boolean show) {
        isCurrentlyOffline = show;
        if (llOfflineBanner != null)
            llOfflineBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        // [Phase 4.3] Update empty state message when connectivity changes
        updateContextualEmptyState();
    }

    // [Phase 4.3] Contextual empty state — رسائل مختلفة حسب السياق
    private void updateContextualEmptyState() {
        if (tvEmpty == null) return;
        boolean hasFilters = !viewModel.getType().equals("all")
                          || !viewModel.getGov().equals("all")
                          || !viewModel.getStatus().equals("all")
                          || (viewModel.reports.getValue() != null
                              && viewModel.reports.getValue().isEmpty());
        if (isCurrentlyOffline) {
            tvEmpty.setText("📵 أنت غير متصل بالإنترنت\nتعرض آخر البيانات المحفوظة");
        } else if (hasFilters) {
            tvEmpty.setText("🔍 لا توجد نتائج للفلاتر المحددة\nجرّب تغيير الفلاتر أو إعادة ضبطها");
        } else {
            tvEmpty.setText("لا توجد بلاغات بعد\nكن أول من يساهم!");
        }
    }

    private void runLayoutAnimation() {
        LayoutAnimationController anim =
            AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down);
        rvBrowse.setLayoutAnimation(anim);
        rvBrowse.scheduleLayoutAnimation();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // ════════════════════════════════════════════════════════
    //  ADAPTER مع Native Ads
    // ════════════════════════════════════════════════════════

    static class BrowseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_SKELETON  = 0;
        private static final int TYPE_ITEM      = 1;
        private static final int TYPE_AD        = 2;
        private static final int AD_INTERVAL    = 20;
        private static final int SKELETON_COUNT = 6;
        private static final int PAGE_SIZE      = 20;

        private final List<NativeAd> loadedAds = new ArrayList<>();
        private Context ctx;

        interface ClickListener { void onClick(ReportEntity r); }
        interface AbuseListener { void onAbuse(ReportEntity r); }
        interface ShareListener { void onShare(ReportEntity r); }

        private final ClickListener onItemClick;
        private final AbuseListener onAbuse;
        private final ShareListener onShare;

        private final List<ReportEntity> fullList    = new ArrayList<>();
        private final List<ReportEntity> displayList = new ArrayList<>();
        private boolean showSkeleton  = true;
        private int     displayedCount = 0;

        BrowseAdapter(ClickListener cl, AbuseListener al, ShareListener sl) {
            this.onItemClick = cl;
            this.onAbuse     = al;
            this.onShare     = sl;
        }

        void setContext(Context c) {
            this.ctx = c;
            preloadAds();
        }

        void destroyAds() {
            for (NativeAd ad : loadedAds) {
                if (ad != null) ad.destroy();
            }
            loadedAds.clear();
        }

        private void preloadAds() {
            if (ctx == null) return;
            try {
                com.google.android.gms.ads.AdLoader loader = new com.google.android.gms.ads.AdLoader.Builder(ctx,
                    "ca-app-pub-3940256099942544/2247696110") // Test ad unit
                    .forNativeAd(ad -> {
                        if (ad != null) loadedAds.add(ad);
                    })
                    .build();
                loader.loadAds(new com.google.android.gms.ads.AdRequest.Builder().build(), 3);
            } catch (Exception ignored) {}
        }

        void setSkeletonMode(boolean on) {
            if (showSkeleton == on) return;
            showSkeleton = on;
            notifyDataSetChanged();
        }

        boolean isShowingSkeleton() { return showSkeleton; }

        void updateData(List<ReportEntity> newFull) {
            if (newFull == null) newFull = new ArrayList<>();

            fullList.clear();
            fullList.addAll(newFull);
            displayedCount = Math.min(PAGE_SIZE, fullList.size());

            List<ReportEntity> page = new ArrayList<>(
                fullList.subList(0, displayedCount));

            if (showSkeleton) {
                showSkeleton = false;
                displayList.clear();
                displayList.addAll(page);
                notifyDataSetChanged();
            } else {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(
                    new BrowseDiffCallback(new ArrayList<>(displayList), page));
                displayList.clear();
                displayList.addAll(page);
                result.dispatchUpdatesTo(this);
            }
        }

        void loadNextPage() {
            int from = displayedCount;
            int to   = Math.min(displayedCount + PAGE_SIZE, fullList.size());
            if (to <= from) return;
            List<ReportEntity> added = new ArrayList<>(fullList.subList(from, to));
            displayList.addAll(added);
            displayedCount = to;
            notifyItemRangeInserted(from, added.size());
        }

        @Override public int getItemViewType(int pos) {
            if (showSkeleton) return TYPE_SKELETON;
            if (!loadedAds.isEmpty() && pos > 0 && pos % AD_INTERVAL == 0)
                return TYPE_AD;
            return TYPE_ITEM;
        }

        @Override public int getItemCount() {
            if (showSkeleton) return SKELETON_COUNT;
            int items = displayList.size();
            if (loadedAds.isEmpty()) return items;
            int adSlots = items / AD_INTERVAL;
            return items + adSlots;
        }

        private int realIndex(int selfPos) {
            if (loadedAds.isEmpty()) return selfPos;
            int adCount = selfPos / AD_INTERVAL;
            return selfPos - adCount;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SKELETON)
                return new SkeletonVH(inf.inflate(R.layout.item_case_skeleton, parent, false));
            if (viewType == TYPE_AD)
                return new AdVH(inf.inflate(R.layout.item_ad_card, parent, false));
            return new ItemVH(inf.inflate(R.layout.item_case_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            if (holder instanceof SkeletonVH) return;
            if (holder instanceof AdVH) {
                if (loadedAds.isEmpty()) return;
                int adIdx = (pos / AD_INTERVAL) % loadedAds.size();
                if (adIdx < loadedAds.size())
                    ((AdVH) holder).bind(loadedAds.get(adIdx));
                return;
            }
            int realPos = realIndex(pos);
            if (realPos < displayList.size())
                bindItem((ItemVH) holder, displayList.get(realPos));
        }

        private void bindItem(ItemVH h, ReportEntity r) {
            Context context = h.itemView.getContext();

            h.tvName.setText(!empty(r.personName) ? r.personName : "مجهول");

            String addr = !empty(r.manualAddress) ? r.manualAddress
                        : (!empty(r.governorate)   ? r.governorate : "");
            h.tvAddr.setText(addr.isEmpty() ? "" : "📍 " + addr);

            h.tvAge.setText("العمر: " + (!empty(r.personAge) ? r.personAge : "؟"));
            h.tvGender.setText(!empty(r.personGender) ? r.personGender : "؟");

            if (r.timestamp > 0 && h.tvTime != null)
                h.tvTime.setText(new SimpleDateFormat("dd/MM/yy", new Locale("ar"))
                    .format(new Date(r.timestamp)));

            bindTypeBadge(context, h.tvTypeBadge, r.reportType);
            bindStatusChip(context, h.chipStatus, r.status, r.reportType);

            if (!empty(r.imageUrl))
                CoilImageLoader.loadRounded(context, r.imageUrl,
                    h.ivPhoto, R.drawable.ic_face_placeholder, 0f);
            else
                h.ivPhoto.setImageResource(R.drawable.ic_face_placeholder);

            h.card.setOnClickListener(v -> onItemClick.onClick(r));
            if (h.btnReportAbuse != null)
                h.btnReportAbuse.setOnClickListener(v -> onAbuse.onAbuse(r));
            if (h.btnShare != null)
                h.btnShare.setOnClickListener(v -> { if (onShare != null) onShare.onShare(r); });
        }

        private void bindTypeBadge(Context ctx, TextView badge, String type) {
            if (badge == null) return;
            if (type == null) type = "missing";
            switch (type) {
                case "found":
                    badge.setText("✋ معثور عليه");
                    badge.setBackgroundColor(ContextCompat.getColor(ctx, R.color.color_found));
                    break;
                case "sighting":
                    badge.setText("👁 شُفت شخصاً");
                    badge.setBackgroundColor(ContextCompat.getColor(ctx, R.color.color_warning));
                    break;
                case "emergency":
                    badge.setText("🚨 طوارئ");
                    badge.setBackgroundColor(ContextCompat.getColor(ctx, R.color.md_error));
                    break;
                default:
                    badge.setText("📋 مفقود");
                    badge.setBackgroundColor(ContextCompat.getColor(ctx, R.color.md_primary));
                    break;
            }
        }

        private void bindStatusChip(Context ctx, Chip chip, String status, String type) {
            if (chip == null) return;
            if ("resolved".equals(status)) {
                chip.setText("✅ تم الحل");
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.color_found));
            } else if ("found".equals(type)) {
                chip.setText("🔎 بحاجة تواصل");
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.md_primary));
            } else if ("approved".equals(status)) {
                chip.setText("🔍 نشط");
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.md_primary));
            } else {
                chip.setText("⏳ قيد المراجعة");
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.color_warning));
            }
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setChipStrokeWidth(0f);
        }

        private boolean empty(String s) { return s == null || s.trim().isEmpty(); }

        static class ItemVH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            ImageView ivPhoto;
            ImageView btnShare;
            TextView  tvName, tvAddr, tvTime, tvTypeBadge;
            Chip      tvAge, tvGender, chipStatus;
            View      btnReportAbuse;

            ItemVH(@NonNull View v) {
                super(v);
                card           = (com.google.android.material.card.MaterialCardView) v;
                ivPhoto        = v.findViewById(R.id.iv_case_photo);
                tvName         = v.findViewById(R.id.tv_case_name);
                tvAddr         = v.findViewById(R.id.tv_case_addr);
                tvAge          = v.findViewById(R.id.tv_case_age);
                tvGender       = v.findViewById(R.id.tv_case_gender);
                tvTime         = v.findViewById(R.id.tv_case_time);
                tvTypeBadge    = v.findViewById(R.id.tv_case_type_badge);
                chipStatus     = v.findViewById(R.id.chip_case_status);
                btnReportAbuse = v.findViewById(R.id.btn_report_abuse);
                btnShare       = v.findViewById(R.id.btn_share_case);
            }
        }

        static class AdVH extends RecyclerView.ViewHolder {
            NativeAdView adView;
            ImageView adIcon;
            TextView  adHeadline, adBody, adCta;
            MediaView adMedia;

            AdVH(@NonNull View v) {
                super(v);
                adView    = (NativeAdView) v;
                adIcon    = v.findViewById(R.id.ad_icon);
                adHeadline= v.findViewById(R.id.ad_headline);
                adBody    = v.findViewById(R.id.ad_body);
                adCta     = v.findViewById(R.id.ad_call_to_action);
                adMedia   = v.findViewById(R.id.ad_media);

                adView.setHeadlineView(adHeadline);
                adView.setBodyView(adBody);
                adView.setCallToActionView(adCta);
                adView.setIconView(adIcon);
                adView.setMediaView(adMedia);
            }

            void bind(NativeAd ad) {
                if (adHeadline != null && ad.getHeadline() != null)
                    adHeadline.setText(ad.getHeadline());
                if (adBody != null && ad.getBody() != null)
                    adBody.setText(ad.getBody());
                if (adCta != null && ad.getCallToAction() != null)
                    adCta.setText(ad.getCallToAction());
                if (adIcon != null && ad.getIcon() != null)
                    adIcon.setImageDrawable(ad.getIcon().getDrawable());
                if (adMedia != null && ad.getMediaContent() != null) {
                    adMedia.setMediaContent(ad.getMediaContent());
                    adMedia.setVisibility(View.VISIBLE);
                }
                adView.setNativeAd(ad);
            }
        }

        static class SkeletonVH extends RecyclerView.ViewHolder {
            SkeletonVH(@NonNull View v) { super(v); }
        }
    }
}