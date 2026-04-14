package com.missingpersons.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import java.util.*;

/**
 * RatingManager — نظام مزدوج:
 *
 *  1. تقييم المستخدمين داخل التطبيق (1-5 نجوم في Firebase)
 *  2. طلب تقييم التطبيق على Google Play عبر Intent مباشر
 *
 * ملاحظة: Play In-App Review API تتطلب dependency إضافية
 * غير متاحة في البيئة الحالية. نستخدم Intent لـ Play Store
 * كبديل مباشر وموثوق.
 *
 * Firebase path: ratings/{targetUid}/{raterUid}
 */
public class RatingManager {

    private static final String TAG          = "RatingManager";
    private static final String RATINGS_PATH = "ratings";
    private static final String USERS_PATH   = "users";

    // ══════════════════════════════════════════════
    //  GOOGLE PLAY STORE RATING (Intent)
    // ══════════════════════════════════════════════

    /**
     * يفتح صفحة التطبيق على Google Play لتقييمه.
     * يستخدم Intent مباشر — لا يحتاج Play Core dependency.
     *
     * @param activity Activity نشطة
     */
    public static void requestPlayStoreReview(@NonNull Activity activity) {
        String packageName = activity.getPackageName();
        try {
            // حاول فتح تطبيق Play Store مباشرة
            Uri marketUri = Uri.parse("market://details?id=" + packageName);
            Intent intent = new Intent(Intent.ACTION_VIEW, marketUri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            activity.startActivity(intent);
            Log.d(TAG, "Opened Play Store app for rating");
        } catch (android.content.ActivityNotFoundException e) {
            // Play Store غير مثبت — افتح المتصفح
            try {
                Uri webUri = Uri.parse(
                    "https://play.google.com/store/apps/details?id=" + packageName);
                activity.startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                Log.d(TAG, "Opened Play Store web for rating");
            } catch (Exception ex) {
                Log.e(TAG, "Could not open Play Store: " + ex.getMessage());
                Toast.makeText(activity,
                    "تعذّر فتح Google Play", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ══════════════════════════════════════════════
    //  SHOW USER RATING DIALOG
    // ══════════════════════════════════════════════

    public static void showRatingDialog(Context ctx, String targetUid, String targetName) {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null || me.isAnonymous()) {
            Toast.makeText(ctx, "🔒 سجّل دخولك لتقييم المستخدمين",
                Toast.LENGTH_SHORT).show();
            return;
        }
        if (me.getUid().equals(targetUid)) {
            Toast.makeText(ctx, "لا يمكنك تقييم نفسك", Toast.LENGTH_SHORT).show();
            return;
        }

        String myUid = me.getUid();
        FirebaseDatabase.getInstance().getReference(RATINGS_PATH)
            .child(targetUid).child(myUid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    float existing = 0f;
                    if (snap.exists()) {
                        Object v = snap.child("stars").getValue();
                        if (v instanceof Long)   existing = ((Long) v).floatValue();
                        if (v instanceof Double) existing = ((Double) v).floatValue();
                    }
                    openDialog(ctx, targetUid, targetName, myUid, existing);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private static void openDialog(Context ctx, String targetUid, String targetName,
                                    String myUid, float existing) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(ctx);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        TextView tvMsg = new TextView(ctx);
        tvMsg.setText("قيّم تجربتك مع " + targetName);
        tvMsg.setTextSize(14f);
        tvMsg.setGravity(android.view.Gravity.CENTER);
        tvMsg.setPadding(0, 0, 0, 20);

        RatingBar ratingBar = new RatingBar(ctx);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(existing > 0 ? existing : 3f);
        android.widget.LinearLayout.LayoutParams lp =
            new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        ratingBar.setLayoutParams(lp);

        layout.addView(tvMsg);
        layout.addView(ratingBar);

        String title = existing > 0 ? "⭐ تعديل تقييمك" : "⭐ تقييم المستخدم";
        new AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("تأكيد", (d, w) -> {
                float stars = ratingBar.getRating();
                if (stars < 1) stars = 1;
                submitRating(ctx, targetUid, myUid, stars);
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    // ══════════════════════════════════════════════
    //  SUBMIT
    // ══════════════════════════════════════════════

    private static void submitRating(Context ctx, String targetUid,
                                      String myUid, float stars) {
        Map<String, Object> data = new HashMap<>();
        data.put("stars",     (int) stars);
        data.put("raterUid",  myUid);
        data.put("timestamp", System.currentTimeMillis());

        FirebaseDatabase.getInstance()
            .getReference(RATINGS_PATH).child(targetUid).child(myUid)
            .setValue(data)
            .addOnSuccessListener(v -> {
                Toast.makeText(ctx,
                    "✅ تم إرسال تقييمك (" + (int) stars + " ⭐)",
                    Toast.LENGTH_SHORT).show();
                recalcAverage(targetUid);
            });
    }

    // ══════════════════════════════════════════════
    //  RECALCULATE AVERAGE
    // ══════════════════════════════════════════════

    private static void recalcAverage(String targetUid) {
        FirebaseDatabase.getInstance().getReference(RATINGS_PATH)
            .child(targetUid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    if (!snap.exists()) return;
                    double total = 0;
                    int count = 0;
                    for (DataSnapshot r : snap.getChildren()) {
                        Object v = r.child("stars").getValue();
                        if (v instanceof Long)   { total += (Long) v;   count++; }
                        else if (v instanceof Double) { total += (Double) v; count++; }
                    }
                    if (count == 0) return;
                    double avg = Math.round((total / count) * 10.0) / 10.0;

                    Map<String, Object> update = new HashMap<>();
                    update.put("ratingAvg",   avg);
                    update.put("ratingCount", count);
                    FirebaseDatabase.getInstance()
                        .getReference(USERS_PATH).child(targetUid)
                        .updateChildren(update);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ══════════════════════════════════════════════
    //  LOAD & DISPLAY
    // ══════════════════════════════════════════════

    public static void loadUserRating(String uid, RatingCallback cb) {
        FirebaseDatabase.getInstance().getReference(USERS_PATH).child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    Object avg   = snap.child("ratingAvg").getValue();
                    Object count = snap.child("ratingCount").getValue();
                    double a = avg instanceof Double ? (Double) avg
                             : avg instanceof Long   ? ((Long) avg).doubleValue() : 0.0;
                    int    c = count instanceof Long ? ((Long) count).intValue() : 0;
                    cb.onLoaded(a, c);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    cb.onLoaded(0, 0);
                }
            });
    }

    public interface RatingCallback {
        void onLoaded(double avg, int count);
    }
}
