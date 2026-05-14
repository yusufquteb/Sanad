package com.missingpersons.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.HumanReviewQueueManager;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.RoleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * EnhancedAdminDashboardActivity — شاشة قائمة المراجعة البشرية.
 *
 * تعرض المطابقات التي تحتاج قرار بشري (REVIEW_REQUIRED) لمسؤولي النظام.
 * المسؤول يمكنه قبول أو رفض كل عنصر.
 */
public class EnhancedAdminDashboardActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────
    private MaterialToolbar             toolbar;
    private LinearProgressIndicator     progressReview;
    private SwipeRefreshLayout          swipeRefresh;
    private RecyclerView                rvQueue;
    private View                        layoutEmpty;
    private TextView                    tvPendingCount;

    // ── Data ───────────────────────────────────────────────────
    private ReviewQueueAdapter                          adapter;
    private final List<HumanReviewQueueManager.ReviewItem> items = new ArrayList<>();

    // ── Auth ───────────────────────────────────────────────────
    private String currentUid;

    // ══════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Auth guard ────────────────────────────────────────
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();

        setContentView(R.layout.activity_enhanced_admin_dashboard);
        bindViews();
        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();

        // ── Role guard ────────────────────────────────────────
        showLoading(true);
        RoleManager.get().load(new RoleManager.LoadCallback() {
            @Override
            public void onLoaded(boolean isAdminOrManager) {
                if (!isAdminOrManager) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        finish();
                    });
                    return;
                }
                runOnUiThread(() -> loadQueue());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showLoading(false);
                    finish();
                });
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    //  View setup
    // ══════════════════════════════════════════════════════════

    private void bindViews() {
        toolbar        = findViewById(R.id.toolbar_enhanced_admin);
        progressReview = findViewById(R.id.progress_review);
        swipeRefresh   = findViewById(R.id.swipe_refresh_review);
        rvQueue        = findViewById(R.id.rv_review_queue);
        layoutEmpty    = findViewById(R.id.layout_empty_review);
        tvPendingCount = findViewById(R.id.tv_pending_count);

        View btnEmptyRefresh = findViewById(R.id.btn_empty_refresh);
        if (btnEmptyRefresh != null) {
            btnEmptyRefresh.setOnClickListener(v -> loadQueue());
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new ReviewQueueAdapter(this, items, new ReviewQueueAdapter.ActionListener() {
            @Override
            public void onApprove(HumanReviewQueueManager.ReviewItem item, int position) {
                handleApprove(item, position);
            }

            @Override
            public void onReject(HumanReviewQueueManager.ReviewItem item, int position) {
                showRejectConfirmDialog(item, position);
            }
        });
        rvQueue.setLayoutManager(new LinearLayoutManager(this));
        rvQueue.setAdapter(adapter);
        rvQueue.setHasFixedSize(false);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(
                R.color.md_primary,
                R.color.success_color,
                R.color.status_matched);
        swipeRefresh.setOnRefreshListener(this::loadQueue);
    }

    // ══════════════════════════════════════════════════════════
    //  Data loading
    // ══════════════════════════════════════════════════════════

    private void loadQueue() {
        showLoading(true);

        HumanReviewQueueManager.loadPendingItems(new HumanReviewQueueManager.QueueLoadCallback() {
            @Override
            public void onLoaded(List<HumanReviewQueueManager.ReviewItem> loaded) {
                runOnUiThread(() -> {
                    showLoading(false);
                    items.clear();
                    items.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    updateStatsCard();
                });
            }

            @Override
            public void onError(String reason) {
                runOnUiThread(() -> {
                    showLoading(false);
                    showSnackbar("خطأ في تحميل القائمة: " + reason, true);
                    updateEmptyState();
                });
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    //  Approve
    // ══════════════════════════════════════════════════════════

    private void handleApprove(HumanReviewQueueManager.ReviewItem item, int position) {
        // Optimistic removal
        removeItem(position);

        HumanReviewQueueManager.approve(item, currentUid,
                new HumanReviewQueueManager.ActionCallback() {
                    @Override
                    public void onSuccess(String itemId) {
                        runOnUiThread(() ->
                            showSnackbar("تم قبول التطابق بنجاح", false));
                    }

                    @Override
                    public void onError(String reason) {
                        runOnUiThread(() -> {
                            // Re-insert on failure
                            items.add(position < items.size() ? position : items.size(), item);
                            adapter.notifyItemInserted(position < items.size()
                                    ? position : items.size() - 1);
                            updateEmptyState();
                            updateStatsCard();
                            showSnackbar("فشل القبول: " + reason, true);
                        });
                    }
                });
    }

    // ══════════════════════════════════════════════════════════
    //  Reject
    // ══════════════════════════════════════════════════════════

    private void showRejectConfirmDialog(HumanReviewQueueManager.ReviewItem item, int position) {
        String name = (item.personName != null && !item.personName.isEmpty())
                ? item.personName : "غير معروف";
        new AlertDialog.Builder(this)
                .setTitle("تأكيد الرفض")
                .setMessage("هل تريد رفض تطابق الشخص "" + name + ""؟\n"
                        + "نسبة التشابه: " + item.getPercent() + "%")
                .setPositiveButton("رفض", (d, w) -> handleReject(item, position))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void handleReject(HumanReviewQueueManager.ReviewItem item, int position) {
        // Optimistic removal
        removeItem(position);

        HumanReviewQueueManager.reject(item, currentUid,
                new HumanReviewQueueManager.ActionCallback() {
                    @Override
                    public void onSuccess(String itemId) {
                        runOnUiThread(() ->
                            showSnackbar("تم رفض التطابق", false));
                    }

                    @Override
                    public void onError(String reason) {
                        runOnUiThread(() -> {
                            // Re-insert on failure
                            items.add(position < items.size() ? position : items.size(), item);
                            adapter.notifyItemInserted(position < items.size()
                                    ? position : items.size() - 1);
                            updateEmptyState();
                            updateStatsCard();
                            showSnackbar("فشل الرفض: " + reason, true);
                        });
                    }
                });
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            adapter.notifyItemRemoved(position);
            updateEmptyState();
            updateStatsCard();
        }
    }

    private void updateEmptyState() {
        boolean empty = items.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvQueue.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateStatsCard() {
        if (tvPendingCount != null) {
            tvPendingCount.setText(String.valueOf(items.size()));
        }
    }

    private void showLoading(boolean loading) {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(loading && items.isEmpty());
        if (progressReview != null)
            progressReview.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showSnackbar(String message, boolean isError) {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar sb = Snackbar.make(root, message, Snackbar.LENGTH_LONG);
        if (isError) {
            sb.setBackgroundTint(getResources().getColor(R.color.md_error, null));
            sb.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            sb.setBackgroundTint(getResources().getColor(R.color.success_color, null));
            sb.setTextColor(getResources().getColor(R.color.white, null));
        }
        sb.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
