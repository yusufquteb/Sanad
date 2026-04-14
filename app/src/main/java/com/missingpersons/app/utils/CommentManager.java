package com.missingpersons.app.utils;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CommentManager — نظام التعليقات لكل حالة
 *
 * ✅ تعليق واحد لكل مستخدم على كل حالة
 * ✅ إبلاغ عن تعليق غير مناسب
 * ✅ الأدمن يحذف التعليقات المُبلَّغ عنها
 * ✅ Firebase path: comments/{reportId}/{userId}
 */
public class CommentManager {

    private static final int MAX_COMMENT_LENGTH = 300;
    private static final String COMMENTS_PATH   = "comments";
    private static final String REPORTS_PATH    = "comment_reports";

    // ══════════════════════════════════════════════
    //  WRITE — إضافة أو تعديل تعليق
    // ══════════════════════════════════════════════

    public static void showAddCommentDialog(Context ctx, String reportId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Toast.makeText(ctx, "🔒 سجّل دخولك لإضافة تعليق", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = user.getUid();

        // هل عنده تعليق مسبق؟
        FirebaseDatabase.getInstance().getReference(COMMENTS_PATH)
            .child(reportId).child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    String existing = snap.child("text").getValue(String.class);
                    openCommentDialog(ctx, reportId, uid, user, existing);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    openCommentDialog(ctx, reportId, uid, user, null);
                }
            });
    }

    private static void openCommentDialog(Context ctx, String reportId,
                                           String uid, FirebaseUser user, String existing) {
        View dialogView = LayoutInflater.from(ctx).inflate(
            android.R.layout.select_dialog_item, null);

        EditText et = new EditText(ctx);
        et.setHint("اكتب تعليقك هنا... (حد أقصى " + MAX_COMMENT_LENGTH + " حرف)");
        et.setMaxLines(5);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setPadding(40, 20, 40, 20);
        if (existing != null) { et.setText(existing); et.setSelection(existing.length()); }

        String title = existing != null ? "✏️ تعديل تعليقك" : "💬 إضافة تعليق";

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("نشر", (d, w) -> {
                String text = et.getText().toString().trim();
                if (text.isEmpty()) {
                    Toast.makeText(ctx, "التعليق فارغ", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (text.length() > MAX_COMMENT_LENGTH) {
                    Toast.makeText(ctx, "التعليق طويل جداً", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveComment(ctx, reportId, uid, user, text);
            })
            .setNegativeButton("إلغاء", null);
        if (existing != null)
            builder.setNeutralButton("🗑 حذف تعليقي", (dd, ww) ->
                deleteMyComment(ctx, reportId, uid));
        builder.show();
    }

    private static void saveComment(Context ctx, String reportId,
                                     String uid, FirebaseUser user, String text) {
        Map<String, Object> comment = new HashMap<>();
        comment.put("text",      text);
        comment.put("userName",  user.getDisplayName() != null
            ? user.getDisplayName() : "مستخدم");
        comment.put("photoUrl",  user.getPhotoUrl() != null
            ? user.getPhotoUrl().toString() : "");
        comment.put("uid",       uid);
        comment.put("timestamp", System.currentTimeMillis());
        comment.put("reportId",  reportId);

        FirebaseDatabase.getInstance().getReference(COMMENTS_PATH)
            .child(reportId).child(uid)
            .setValue(comment)
            .addOnSuccessListener(v ->
                Toast.makeText(ctx, "✅ تم نشر تعليقك", Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e ->
                Toast.makeText(ctx, "❌ فشل النشر", Toast.LENGTH_SHORT).show());
    }

    private static void deleteMyComment(Context ctx, String reportId, String uid) {
        FirebaseDatabase.getInstance().getReference(COMMENTS_PATH)
            .child(reportId).child(uid).removeValue()
            .addOnSuccessListener(v ->
                Toast.makeText(ctx, "تم حذف تعليقك", Toast.LENGTH_SHORT).show());
    }

    // ══════════════════════════════════════════════
    //  REPORT — إبلاغ عن تعليق
    // ══════════════════════════════════════════════

    public static void reportComment(Context ctx, String reportId,
                                      String commentUid, String commentText) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        new AlertDialog.Builder(ctx)
            .setTitle("⚠️ إبلاغ عن تعليق")
            .setItems(new String[]{
                "محتوى مسيء أو مزعج",
                "معلومات مضللة",
                "خصوصية / ابتزاز",
                "محتوى آخر"
            }, (d, which) -> {
                String[] reasons = {"محتوى مسيء", "معلومات مضللة", "خصوصية", "آخر"};
                Map<String, Object> report = new HashMap<>();
                report.put("reportedUid",    commentUid);
                report.put("reporterUid",    user.getUid());
                report.put("reason",         reasons[which]);
                report.put("commentText",    commentText);
                report.put("caseId",         reportId);
                report.put("timestamp",      System.currentTimeMillis());
                report.put("status",         "pending");

                FirebaseDatabase.getInstance().getReference(REPORTS_PATH)
                    .push().setValue(report)
                    .addOnSuccessListener(v ->
                        Toast.makeText(ctx, "✅ تم إرسال البلاغ للمشرفين", Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    // ══════════════════════════════════════════════
    //  LOAD — تحميل التعليقات مع Adapter
    // ══════════════════════════════════════════════

    public static void loadComments(Context ctx, String reportId,
                                     RecyclerView rv, TextView tvCount) {
        List<Map<String, Object>> comments = new ArrayList<>();
        CommentAdapter adapter = new CommentAdapter(ctx, comments, reportId);
        rv.setLayoutManager(new LinearLayoutManager(ctx));
        rv.setAdapter(adapter);

        FirebaseDatabase.getInstance().getReference(COMMENTS_PATH)
            .child(reportId)
            .orderByChild("timestamp")
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    comments.clear();
                    for (DataSnapshot c : snap.getChildren()) {
                        Map<String, Object> m = new HashMap<>();
                        for (DataSnapshot f : c.getChildren())
                            m.put(f.getKey(), f.getValue());
                        comments.add(0, m); // أحدث أولاً
                    }
                    adapter.notifyDataSetChanged();
                    if (tvCount != null)
                        tvCount.setText("💬 " + comments.size() + " تعليق");
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ══════════════════════════════════════════════
    //  ADAPTER
    // ══════════════════════════════════════════════

    static class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {

        private final Context ctx;
        private final List<Map<String, Object>> items;
        private final String reportId;
        private final String myUid;

        CommentAdapter(Context ctx, List<Map<String, Object>> items, String reportId) {
            this.ctx      = ctx;
            this.items    = items;
            this.reportId = reportId;
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            this.myUid    = u != null ? u.getUid() : "";
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_comment, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> c = items.get(pos);
            String name      = safeStr(c, "userName");
            String text      = safeStr(c, "text");
            String photoUrl  = safeStr(c, "photoUrl");
            String uid       = safeStr(c, "uid");
            Long   ts        = c.get("timestamp") instanceof Long ? (Long)c.get("timestamp") : 0L;

            h.tvName.setText(name);
            h.tvText.setText(text);
            h.tvTime.setText(ts > 0
                ? new SimpleDateFormat("dd/MM HH:mm", new Locale("ar"))
                    .format(new Date(ts))
                : "");

            if (!photoUrl.isEmpty())
                CoilImageLoader.loadCircle(ctx, photoUrl, h.ivAvatar, R.drawable.ic_person);
            else
                h.ivAvatar.setImageResource(R.drawable.ic_person);

            // زر إبلاغ — مخفي لو التعليق تبعه
            if (myUid.equals(uid)) {
                h.btnReport.setVisibility(View.GONE);
            } else {
                h.btnReport.setVisibility(View.VISIBLE);
                h.btnReport.setOnClickListener(v ->
                    reportComment(ctx, reportId, uid, text));
            }

            // النقر المطول = تعديل تعليقك
            if (myUid.equals(uid))
                h.itemView.setOnLongClickListener(v -> {
                    showAddCommentDialog(ctx, reportId);
                    return true;
                });
        }

        @Override public int getItemCount() { return items.size(); }

        private String safeStr(Map<String, Object> m, String k) {
            Object v = m.get(k);
            return v instanceof String ? (String)v : "";
        }

        static class VH extends RecyclerView.ViewHolder {
            de.hdodenhof.circleimageview.CircleImageView ivAvatar;
            TextView tvName, tvText, tvTime, btnReport;
            VH(@NonNull View v) {
                super(v);
                ivAvatar  = v.findViewById(R.id.iv_comment_avatar);
                tvName    = v.findViewById(R.id.tv_comment_name);
                tvText    = v.findViewById(R.id.tv_comment_text);
                tvTime    = v.findViewById(R.id.tv_comment_time);
                btnReport = v.findViewById(R.id.btn_report_comment);
            }
        }
    }
}
