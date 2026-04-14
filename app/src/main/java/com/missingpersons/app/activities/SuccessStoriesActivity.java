package com.missingpersons.app.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.CoilImageLoader;
import com.missingpersons.app.utils.LanguageHelper;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SuccessStoriesActivity — قصص النجاح
 *
 * إصلاحات المرحلة 1:
 *  - يقرأ من 3 nodes: reports + found_persons + sightings
 *  - لا يعتمد على btn_load_more — التحميل تلقائي عند الوصول لآخر القائمة (infinite scroll)
 *
 * المرحلة 2 — Pagination تلقائي:
 *  - PAGE_SIZE = 15
 *  - addOnScrollListener يكتشف الوصول لنهاية القائمة ويطلب الصفحة التالية
 *  - مؤشر تحميل في أسفل القائمة (footer ViewHolder)
 */
public class SuccessStoriesActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 15;

    // ─── Views ────────────────────────────────────────────────────
    private RecyclerView recyclerView;
    private ProgressBar  progressBar;
    private TextView     tvEmpty, tvCount;

    // ─── Adapter & Data ───────────────────────────────────────────
    private StoriesAdapter adapter;
    private final List<HashMap<String, Object>> stories = new ArrayList<>();

    // ─── Pagination ───────────────────────────────────────────────
    /** آخر resolvedAt مقروء لكل node */
    private final Map<String, Long> lastTimestamps = new HashMap<>();
    /** هل وصلنا لنهاية كل الـ nodes؟ */
    private final Map<String, Boolean> nodeExhausted = new HashMap<>();
    private boolean isLoading   = false;
    private boolean allExhausted = false;

    private static final String[] NODES = {"reports", "found_persons", "sightings"};

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_success_stories);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("✅ قصص النجاح");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.rv_stories);
        progressBar  = findViewById(R.id.progress_stories);
        tvEmpty      = findViewById(R.id.tv_stories_empty);
        tvCount      = findViewById(R.id.tv_stories_count);

        // ── إعداد الـ RecyclerView ─────────────────────────────────
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new StoriesAdapter(stories);
        recyclerView.setAdapter(adapter);

        // ── Infinite Scroll — تحميل تلقائي عند الوصول لآخر العناصر ──
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (allExhausted || isLoading) return;
                int totalItems     = layoutManager.getItemCount();
                int lastVisible    = layoutManager.findLastVisibleItemPosition();
                // ابدأ التحميل عندما يتبقى 3 عناصر أو أقل قبل النهاية
                if (totalItems > 0 && lastVisible >= totalItems - 3) {
                    loadNextPage();
                }
            }
        });

        // تهيئة الـ maps
        for (String node : NODES) {
            lastTimestamps.put(node, Long.MAX_VALUE); // نبدأ من الأحدث (ترتيب تنازلي)
            nodeExhausted.put(node, false);
        }

        // تحميل الصفحة الأولى
        loadNextPage();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGINATION — تحميل صفحة باستخدام resolvedAt كـ cursor
    // ══════════════════════════════════════════════════════════════

    private void loadNextPage() {
        if (isLoading || allExhausted) return;
        isLoading = true;
        showFooterLoading(true);

        int[] remaining = {NODES.length};
        List<HashMap<String, Object>> batch = new ArrayList<>();

        for (String node : NODES) {
            if (Boolean.TRUE.equals(nodeExhausted.get(node))) {
                remaining[0]--;
                if (remaining[0] == 0) mergeBatch(batch);
                continue;
            }

            long endBefore = lastTimestamps.getOrDefault(node, Long.MAX_VALUE);

            // استخدم endAt على resolvedAt للـ cursor-based pagination
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference(node);
            Query q;
            if (endBefore == Long.MAX_VALUE) {
                // الصفحة الأولى — أحدث PAGE_SIZE
                q = ref.orderByChild("resolvedAt").limitToLast(PAGE_SIZE);
            } else {
                // الصفحات التالية — قبل آخر cursor
                q = ref.orderByChild("resolvedAt").endAt(endBefore - 1).limitToLast(PAGE_SIZE);
            }

            final String currentNode = node;
            q.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    int fetchedCount = 0;
                    for (DataSnapshot c : snap.getChildren()) {
                        String status = c.child("status").getValue(String.class);
                        if (!"resolved".equals(status)) continue;
                        fetchedCount++;
                        HashMap<String, Object> m = new HashMap<>();
                        for (DataSnapshot f : c.getChildren()) m.put(f.getKey(), f.getValue());
                        m.put("reportId", c.getKey());
                        m.put("_sourceNode", currentNode);
                        if (!m.containsKey("reportType")) {
                            if ("found_persons".equals(currentNode))  m.put("reportType", "found");
                            else if ("sightings".equals(currentNode)) m.put("reportType", "sighting");
                            else                                       m.put("reportType", "missing");
                        }
                        batch.add(m);
                    }

                    // إذا جاءت أقل من PAGE_SIZE — الـ node انتهى
                    if (fetchedCount < PAGE_SIZE) {
                        nodeExhausted.put(currentNode, true);
                    }
                    // حدّث cursor
                    if (fetchedCount > 0) {
                        // أصغر resolvedAt في هذه الدفعة يصبح الـ cursor
                        long minTs = Long.MAX_VALUE;
                        for (HashMap<String, Object> item : batch) {
                            if (currentNode.equals(item.get("_sourceNode"))) {
                                Object ts = item.get("resolvedAt");
                                if (ts instanceof Long && (Long) ts < minTs) minTs = (Long) ts;
                            }
                        }
                        if (minTs < Long.MAX_VALUE) lastTimestamps.put(currentNode, minTs);
                    }

                    remaining[0]--;
                    if (remaining[0] == 0) mergeBatch(batch);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    remaining[0]--;
                    if (remaining[0] == 0) mergeBatch(batch);
                }
            });
        }
    }

    private void mergeBatch(List<HashMap<String, Object>> batch) {
        // ترتيب من الأحدث (resolvedAt تنازلي)
        batch.sort((a, b) -> {
            long ta = a.get("resolvedAt") instanceof Long ? (Long) a.get("resolvedAt") : 0L;
            long tb = b.get("resolvedAt") instanceof Long ? (Long) b.get("resolvedAt") : 0L;
            return Long.compare(tb, ta);
        });

        // تحقق من نهاية كل الـ nodes
        boolean exhausted = true;
        for (String node : NODES) {
            if (!Boolean.TRUE.equals(nodeExhausted.get(node))) {
                exhausted = false;
                break;
            }
        }
        allExhausted = exhausted;

        runOnUiThread(() -> {
            isLoading = false;
            showFooterLoading(false);
            if (progressBar != null) progressBar.setVisibility(View.GONE);

            if (batch.isEmpty() && stories.isEmpty()) {
                if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                return;
            }

            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            int oldSize = stories.size();
            stories.addAll(batch);
            adapter.notifyItemRangeInserted(oldSize, batch.size());

            if (tvCount != null) {
                tvCount.setText("🎉 " + stories.size() + " حالة تم إيجادها بنجاح"
                    + (allExhausted ? "" : "+"));
                tvCount.setVisibility(View.VISIBLE);
            }
        });
    }

    /** إظهار / إخفاء مؤشر التحميل في footer القائمة */
    private void showFooterLoading(boolean show) {
        adapter.setFooterLoading(show);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  ADAPTER — يدعم footer مؤشر التحميل
    // ══════════════════════════════════════════════════════════════

    static class StoriesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_ITEM   = 0;
        private static final int TYPE_FOOTER = 1;

        private final List<HashMap<String, Object>> items;
        private boolean isFooterLoading = false;

        StoriesAdapter(List<HashMap<String, Object>> items) {
            this.items = items;
        }

        void setFooterLoading(boolean loading) {
            boolean changed = this.isFooterLoading != loading;
            this.isFooterLoading = loading;
            if (changed) notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return position < items.size() ? TYPE_ITEM : TYPE_FOOTER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_FOOTER) {
                ProgressBar pb = new ProgressBar(parent.getContext());
                pb.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, 120));
                pb.setPadding(0, 24, 0, 24);
                return new FooterVH(pb);
            }
            return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_success_story, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof FooterVH) {
                ((FooterVH) holder).pb.setVisibility(
                    isFooterLoading ? View.VISIBLE : View.GONE);
                return;
            }

            VH h = (VH) holder;
            HashMap<String, Object> r = items.get(position);

            String name   = str(r, "personName", "مجهول");
            String addr   = str(r, "manualAddress", "");
            Object tsObj  = r.get("resolvedAt");
            long   ts     = tsObj instanceof Long ? (Long) tsObj : 0L;

            h.tvName.setText("✅ " + name);
            if (!addr.isEmpty() && h.tvAddr != null) h.tvAddr.setText("📍 " + addr);

            if (ts > 0 && h.tvDate != null) {
                String date = new SimpleDateFormat("dd MMMM yyyy",
                    new Locale("ar")).format(new Date(ts));
                h.tvDate.setText("📅 " + date);
            }

            // كم يوماً كان مفقوداً؟
            Object incTsObj = r.get("incidentDate");
            long incTs = incTsObj instanceof Long ? (Long) incTsObj : 0L;
            if (incTs > 0 && ts > 0 && h.tvDaysMissing != null) {
                long days = (ts - incTs) / 86400000L;
                h.tvDaysMissing.setText("مكث مفقوداً " + days + " يوم");
                h.tvDaysMissing.setVisibility(View.VISIBLE);
            }

            // زر التفاصيل + click على الكرت
            String reportId = str(r, "reportId", "");
            if (!reportId.isEmpty()) {
                android.content.Context ctx = h.itemView.getContext();
                android.view.View.OnClickListener openDetail = v ->
                    ctx.startActivity(new Intent(ctx, CaseDetailActivity.class)
                        .putExtra("reportId", reportId));
                h.itemView.setOnClickListener(openDetail);
                if (h.btnDetails != null) h.btnDetails.setOnClickListener(openDetail);
            }

            // الصورة
            Object urlsObj = r.get("imageUrls");
            String imgUrl  = null;
            if (urlsObj instanceof List && !((List<?>) urlsObj).isEmpty())
                imgUrl = ((List<?>) urlsObj).get(0).toString();
            if (imgUrl == null || imgUrl.isEmpty())
                imgUrl = str(r, "imageUrl", "");

            if (!imgUrl.isEmpty() && h.ivPhoto != null) {
                CoilImageLoader.loadRounded(h.ivPhoto.getContext(), imgUrl,
                    h.ivPhoto, R.drawable.ic_person, 12f);
            } else if (h.ivPhoto != null) {
                h.ivPhoto.setImageResource(R.drawable.ic_person);
            }
        }

        private String str(HashMap<String, Object> m, String k, String def) {
            Object v = m.get(k);
            return v instanceof String ? (String) v : def;
        }

        @Override
        public int getItemCount() {
            // +1 للـ footer (مؤشر التحميل)
            return items.size() + 1;
        }

        // ── ViewHolders ──────────────────────────────────────────
        static class VH extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            TextView  tvName, tvAddr, tvDate, tvDaysMissing, btnDetails;

            VH(@NonNull View v) {
                super(v);
                ivPhoto       = v.findViewById(R.id.iv_story_photo);
                tvName        = v.findViewById(R.id.tv_story_name);
                tvAddr        = v.findViewById(R.id.tv_story_addr);
                tvDate        = v.findViewById(R.id.tv_story_date);
                tvDaysMissing = v.findViewById(R.id.tv_story_days_missing);
                btnDetails    = v.findViewById(R.id.btn_story_details);
            }
        }

        static class FooterVH extends RecyclerView.ViewHolder {
            ProgressBar pb;
            FooterVH(@NonNull ProgressBar v) {
                super(v);
                pb = v;
            }
        }
    }
}
