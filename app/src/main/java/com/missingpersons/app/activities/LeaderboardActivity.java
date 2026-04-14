package com.missingpersons.app.activities;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;
import com.missingpersons.app.utils.PointsManager;
import java.util.List;

/**
 * LeaderboardActivity — لوحة الأوائل بتصميم Podium
 * الثلاثة الأوائل في الأعلى (2-1-3) + قائمة للباقين
 */
public class LeaderboardActivity extends AppCompatActivity {

    private LinearLayout            llTop3, layoutLeaderboard;
    private com.google.android.material.card.MaterialCardView cardLeaderboardList;
    private CircularProgressIndicator progressLeaderboard;
    private TextView                tvMyRank;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);
        // ── Edge-to-Edge: يمنع تداخل المحتوى مع Navigation Bar ──
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int navBot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                         v.getPaddingRight(), navBot);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbar_leaderboard);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("لوحة الأوائل");
        }

        llTop3              = findViewById(R.id.ll_top3);
        layoutLeaderboard   = findViewById(R.id.layout_leaderboard);
        cardLeaderboardList = findViewById(R.id.card_leaderboard_list);
        progressLeaderboard = findViewById(R.id.progress_leaderboard);
        tvMyRank            = findViewById(R.id.tv_my_rank);

        loadLeaderboard();
        loadMyPoints();
    }

    // ══════════════════════════════════════════════════════════════
    //  تحميل البيانات
    // ══════════════════════════════════════════════════════════════

    private void loadLeaderboard() {
        if (progressLeaderboard != null)
            if (progressLeaderboard != null) progressLeaderboard.setVisibility(View.VISIBLE);

        PointsManager.getLeaderboard(entries -> runOnUiThread(() -> {
            if (progressLeaderboard != null)
                if (progressLeaderboard != null) progressLeaderboard.setVisibility(View.GONE);

            if (entries.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("لا توجد بيانات بعد — كن أول المساهمين!");
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, 60, 0, 0);
                empty.setTextColor(Color.WHITE);
                if (layoutLeaderboard != null) layoutLeaderboard.addView(empty);
                return;
            }

            buildPodium(entries);
            buildList(entries);
        }));
    }

    // ══════════════════════════════════════════════════════════════
    //  Podium — الثلاثة الأوائل
    //  الترتيب بالعرض: 2 (يسار) — 1 (وسط أطول) — 3 (يمين)
    // ══════════════════════════════════════════════════════════════

    private void buildPodium(List<PointsManager.LeaderboardEntry> entries) {
        if (llTop3 == null) return;
        if (llTop3 != null) llTop3.removeAllViews();

        // إعداد الثلاثة
        PointsManager.LeaderboardEntry e1 = entries.size() > 0 ? entries.get(0) : null;
        PointsManager.LeaderboardEntry e2 = entries.size() > 1 ? entries.get(1) : null;
        PointsManager.LeaderboardEntry e3 = entries.size() > 2 ? entries.get(2) : null;

        // ترتيب العرض: 2 — 1 — 3
        if (e2 != null && llTop3 != null) llTop3.addView(buildPodiumItem(e2, 2, 200, false));
        if (e1 != null && llTop3 != null) llTop3.addView(buildPodiumItem(e1, 1, 230, true));
        if (e3 != null && llTop3 != null) llTop3.addView(buildPodiumItem(e3, 3, 180, false));
    }

    private View buildPodiumItem(PointsManager.LeaderboardEntry entry,
                                  int rank, int avatarSize, boolean isFirst) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(colLp);

        // ── صورة بروفايل دائرية (Avatar مولود) ──
        ImageView ivAvatar = new ImageView(this);
        int sz = dpToPx(avatarSize / 4);  // ~50-58dp
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(sz, sz);
        ivLp.bottomMargin = dpToPx(6);
        ivAvatar.setLayoutParams(ivLp);
        ivAvatar.setImageBitmap(generateAvatar(entry.displayName, rank));
        col.addView(ivAvatar);

        // ── شارة الترتيب فوق الصورة ──
        if (rank <= 3) {
            TextView tvMedal = new TextView(this);
            tvMedal.setText(rank == 1 ? "🥇" : rank == 2 ? "🥈" : "🥉");
            tvMedal.setTextSize(isFirst ? 22f : 18f);
            tvMedal.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams medLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            medLp.bottomMargin = dpToPx(2);
            tvMedal.setLayoutParams(medLp);
            col.addView(tvMedal);
        }

        // ── الاسم ──
        TextView tvName = new TextView(this);
        String name = entry.displayName;
        if (name.length() > 8) name = name.substring(0, 6) + "…";
        tvName.setText(name);
        tvName.setTextSize(isFirst ? 14f : 12f);
        tvName.setTextColor(Color.WHITE);
        tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
        tvName.setGravity(Gravity.CENTER);
        col.addView(tvName);

        // ── "Me" badge (يُفحص لاحقاً) ──
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null &&
                auth.getCurrentUser().getUid().equals(entry.uid)) {
            TextView tvMe = new TextView(this);
            tvMe.setText("Me");
            tvMe.setTextSize(10f);
            tvMe.setTextColor(Color.WHITE);
            tvMe.setBackgroundColor(0xFFFF5722);
            tvMe.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            tvMe.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams meLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            meLp.topMargin = dpToPx(2);
            tvMe.setLayoutParams(meLp);
            col.addView(tvMe);
        }

        // ── النقاط ──
        TextView tvPoints = new TextView(this);
        tvPoints.setText(formatPoints(entry.points));
        tvPoints.setTextSize(isFirst ? 13f : 11f);
        tvPoints.setTextColor(0xFFFFE082);
        tvPoints.setGravity(Gravity.CENTER);
        tvPoints.setTypeface(tvPoints.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        ptLp.bottomMargin = dpToPx(8);
        tvPoints.setLayoutParams(ptLp);
        col.addView(tvPoints);

        // ── قاعدة المنصة ──
        int barHeight = rank == 1 ? dpToPx(80) : rank == 2 ? dpToPx(60) : dpToPx(44);
        int barBg = rank == 1 ? R.drawable.bg_podium_gold
                  : rank == 2 ? R.drawable.bg_podium_silver : R.drawable.bg_podium_bronze;
        View bar = new View(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, barHeight);
        bar.setLayoutParams(barLp);
        bar.setBackgroundResource(barBg);
        col.addView(bar);

        return col;
    }

    // ══════════════════════════════════════════════════════════════
    //  القائمة — من المركز 4 فما فوق
    // ══════════════════════════════════════════════════════════════

    private void buildList(List<PointsManager.LeaderboardEntry> entries) {
        if (layoutLeaderboard == null) return;
        layoutLeaderboard.removeAllViews();

        String myUid = "";
        com.google.firebase.auth.FirebaseUser me =
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (me != null) myUid = me.getUid();

        // [إصلاح خطأ-07] إضافة المراكز من 4 فما فوق
        for (int i = 3; i < entries.size(); i++) {
            layoutLeaderboard.addView(buildListRow(i + 1, entries.get(i), myUid));
        }

        // إظهار بطاقة القائمة دائماً (حتى لو فارغة نسبياً)
        if (cardLeaderboardList != null) {
            cardLeaderboardList.setVisibility(View.VISIBLE);
        }

        if (layoutLeaderboard.getChildCount() == 0) {
            // أقل من 4 مشاركين — أظهر رسالة تشجيعية بدل صندوق فارغ
            TextView tv = new TextView(this);
            tv.setText("أنت من أوائل المساهمين 🎉\nكن أول من يصل للمركز الرابع!");
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24));
            tv.setTextColor(0xFF888888);
            layoutLeaderboard.addView(tv);
        }
    }

    private View buildListRow(int rank, PointsManager.LeaderboardEntry entry, String myUid) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dpToPx(8));
        card.setLayoutParams(cardLp);
        card.setRadius(dpToPx(16));
        card.setCardElevation(dpToPx(1));
        boolean isMe = entry.uid.equals(myUid);
        card.setCardBackgroundColor(isMe ? 0xFFFFF3E0 : Color.WHITE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        row.setGravity(Gravity.CENTER_VERTICAL);

        // رقم الترتيب
        TextView tvRank = new TextView(this);
        tvRank.setText(String.valueOf(rank));
        tvRank.setTextSize(16f);
        tvRank.setTextColor(0xFF90A4AE);
        tvRank.setMinWidth(dpToPx(36));
        tvRank.setGravity(Gravity.CENTER);
        row.addView(tvRank);

        // avatar صغير
        ImageView iv = new ImageView(this);
        int sz = dpToPx(44);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(sz, sz);
        ivLp.setMarginStart(dpToPx(8));
        ivLp.setMarginEnd(dpToPx(12));
        iv.setLayoutParams(ivLp);
        iv.setImageBitmap(generateAvatar(entry.displayName, 0));
        row.addView(iv);

        // الاسم + الرتبة
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(infoLp);

        TextView tvName = new TextView(this);
        tvName.setText(entry.displayName + (isMe ? " (أنا)" : ""));
        tvName.setTextSize(14f);
        tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
        tvName.setTextColor(0xFF212121);
        info.addView(tvName);

        TextView tvRankLabel = new TextView(this);
        tvRankLabel.setText(PointsManager.getRank(entry.points));
        tvRankLabel.setTextSize(11f);
        tvRankLabel.setTextColor(0xFF9E9E9E);
        info.addView(tvRankLabel);
        row.addView(info);

        // النقاط
        TextView tvPoints = new TextView(this);
        tvPoints.setText(formatPoints(entry.points));
        tvPoints.setTextSize(14f);
        tvPoints.setTypeface(tvPoints.getTypeface(), Typeface.BOLD);
        tvPoints.setTextColor(0xFF1A5276);
        tvPoints.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(tvPoints);

        card.addView(row);
        return card;
    }

    // ══════════════════════════════════════════════════════════════
    //  نقاطي
    // ══════════════════════════════════════════════════════════════

    private void loadMyPoints() {
        PointsManager.getMyPoints((totalPoints, rank) -> runOnUiThread(() -> {
            if (tvMyRank != null) {
                if (tvMyRank != null) tvMyRank.setVisibility(View.VISIBLE);
                if (tvMyRank != null) tvMyRank.setText("نقاطي: " + formatPoints(totalPoints) + "  •  " + rank);
            }
        }));
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    /** يولّد صورة avatar دائرية بالحرف الأول واللون */
    private Bitmap generateAvatar(String name, int rank) {
        int size = 120;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // لون الخلفية
        int[] colors = {0xFF1A5276, 0xFFC0392B, 0xFF1D8348,
                        0xFF7D3C98, 0xFF117A65, 0xFFCA6F1E};
        int bg = colors[Math.abs(name.hashCode()) % colors.length];
        if (rank == 1) bg = 0xFFB7950B;  // ذهبي
        if (rank == 2) bg = 0xFF797D7F;  // فضي
        if (rank == 3) bg = 0xFF935116;  // برونزي

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(bg);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // الحرف الأول
        paint.setColor(Color.WHITE);
        paint.setTextSize(52f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        String letter = name.isEmpty() ? "؟" : String.valueOf(name.charAt(0)).toUpperCase();
        float baseline = size / 2f - ((paint.descent() + paint.ascent()) / 2f);
        canvas.drawText(letter, size / 2f, baseline, paint);

        return bmp;
    }

    private String formatPoints(int pts) {
        if (pts >= 1_000_000) return String.format("%.1fM", pts / 1_000_000f);
        if (pts >= 1_000)     return String.format("%,d", pts);
        return String.valueOf(pts);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
