package com.missingpersons.app.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;

/**
 * MatchConfidenceHelper — عرض تطابقات الـ ML في CaseDetailActivity
 *
 * [مرحلة 2.2] يعرض:
 *   ✅ شريط تقدم بنسبة التطابق
 *   ✅ عدد التطابقات المحتملة من match_candidates
 *   ✅ بطاقة ملخص "تطابقات محتملة" لصاحب البلاغ فقط
 *   ✅ ألوان حسب نسبة الثقة (أخضر/أصفر/برتقالي)
 *
 * الاستخدام في CaseDetailActivity.bindData():
 *   MatchConfidenceHelper.loadAndDisplay(this, reportId, llMatchContainer);
 */
public class MatchConfidenceHelper {

    /**
     * يحمّل match_candidates لهذا البلاغ ويعرضها في الـ container المعطى
     *
     * @param context       Activity context
     * @param reportId      معرف البلاغ
     * @param reporterId    معرف صاحب البلاغ
     * @param container     LinearLayout لإضافة البطاقات فيه
     */
    public static void loadAndDisplay(Context context, String reportId,
                                       String reporterId, LinearLayout container) {
        if (container == null || reportId == null) return;

        // فقط لصاحب البلاغ والمدير
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        boolean isAdmin  = RateLimiter.isAdmin(context);
        boolean isOwner  = myUid.equals(reporterId);
        if (!isOwner && !isAdmin) return;

        // اجلب من match_candidates حيث reportId == reportId
        FirebaseDatabase.getInstance().getReference("match_candidates")
            .orderByChild("reportId").equalTo(reportId)
            .limitToFirst(5) // نعرض أفضل 5 فقط
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists() || snapshot.getChildrenCount() == 0) return;

                    // عنوان القسم
                    addSectionTitle(context, container,
                        "🔍 تطابقات محتملة (" + snapshot.getChildrenCount() + ")");

                    for (DataSnapshot child : snapshot.getChildren()) {
                        Long   scoreL   = child.child("score").getValue(Long.class);
                        String foundId  = child.child("foundId").getValue(String.class);
                        String reasons  = child.child("reasons").getValue(String.class);
                        String status   = child.child("status").getValue(String.class);

                        int score = scoreL != null ? scoreL.intValue() : 0;
                        if (score == 0) continue;

                        addMatchCard(context, container, score, foundId, reasons, status);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ════════════════════════════════════════════════════════════════════

    private static void addSectionTitle(Context ctx, LinearLayout parent, String title) {
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextSize(15f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#1565C0"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 24, 0, 8);
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private static void addMatchCard(Context ctx, LinearLayout parent,
                                      int score, String foundId,
                                      String reasons, String status) {
        // بطاقة التطابق
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(buildCardBackground(ctx, score));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 4, 0, 4);
        card.setLayoutParams(cardLp);
        card.setPadding(dpToPx(ctx, 12), dpToPx(ctx, 10),
                        dpToPx(ctx, 12), dpToPx(ctx, 10));

        // عنوان: نسبة التطابق
        TextView tvScore = new TextView(ctx);
        tvScore.setText(getConfidenceEmoji(score) + " تطابق " + score + "%"
            + (isHighConfidence(score) ? " — عالي الثقة" : ""));
        tvScore.setTextSize(14f);
        tvScore.setTypeface(null, android.graphics.Typeface.BOLD);
        tvScore.setTextColor(getConfidenceColor(score));
        card.addView(tvScore);

        // شريط التقدم
        ProgressBar pb = new ProgressBar(ctx, null,
            android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ctx, 8));
        pbLp.setMargins(0, 6, 0, 6);
        pb.setLayoutParams(pbLp);
        pb.setMax(100);
        pb.setProgress(score);
        setProgressBarColor(pb, score);
        card.addView(pb);

        // أسباب التطابق
        if (reasons != null && !reasons.isEmpty()) {
            TextView tvReasons = new TextView(ctx);
            tvReasons.setText("📋 " + formatReasons(reasons));
            tvReasons.setTextSize(12f);
            tvReasons.setTextColor(Color.parseColor("#555555"));
            card.addView(tvReasons);
        }

        // حالة المطابقة
        if (status != null) {
            TextView tvStatus = new TextView(ctx);
            tvStatus.setText(formatStatus(status));
            tvStatus.setTextSize(11f);
            tvStatus.setTextColor(Color.parseColor("#777777"));
            tvStatus.setPadding(0, 4, 0, 0);
            card.addView(tvStatus);
        }

        parent.addView(card);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private static android.graphics.drawable.GradientDrawable buildCardBackground(
            Context ctx, int score) {
        android.graphics.drawable.GradientDrawable gd =
            new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(ctx, 8));
        gd.setColor(Color.parseColor(score >= 75 ? "#E8F5E9" : "#FFF8E1"));
        gd.setStroke(dpToPx(ctx, 1),
            Color.parseColor(score >= 75 ? "#4CAF50" : "#FFC107"));
        return gd;
    }

    private static String getConfidenceEmoji(int score) {
        if (score >= 80) return "🟢";
        if (score >= 60) return "🟡";
        return "🟠";
    }

    private static int getConfidenceColor(int score) {
        if (score >= 80) return Color.parseColor("#2E7D32");
        if (score >= 60) return Color.parseColor("#F57F17");
        return Color.parseColor("#E65100");
    }

    private static boolean isHighConfidence(int score) {
        return score >= 75;
    }

    private static void setProgressBarColor(ProgressBar pb, int score) {
        String hex = score >= 75 ? "#4CAF50" : score >= 55 ? "#FFC107" : "#FF9800";
        pb.getProgressDrawable().setColorFilter(
            Color.parseColor(hex),
            android.graphics.PorterDuff.Mode.SRC_IN);
    }

    private static String formatReasons(String reasons) {
        return reasons
            .replace("same_governorate", "نفس المحافظة")
            .replace("age_match", "تقارب العمر")
            .replace("name_match", "تشابه الاسم")
            .replace(",", " • ")
            .replaceAll("face_sim_(\\d+)", "تشابه وجه $1%");
    }

    private static String formatStatus(String status) {
        switch (status) {
            case "high_confidence": return "⚡ ثقة عالية — يحتاج مراجعة إدارية";
            case "review_needed":   return "🔎 قيد المراجعة";
            case "confirmed":       return "✅ تأكيد تطابق";
            case "rejected":        return "❌ تم الرفض";
            default:                return status;
        }
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
