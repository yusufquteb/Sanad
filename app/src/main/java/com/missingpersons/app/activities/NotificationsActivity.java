package com.missingpersons.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import java.text.SimpleDateFormat;
import java.util.*;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView      rv;
    private TextView          tvEmpty, btnMarkAll;
    private final List<NotifItem> items = new ArrayList<>();
    private NotifAdapter      adapter;
    private DatabaseReference notifRef;

    @Override
    protected void attachBaseContext(android.content.Context ctx) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(ctx));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        // ── Edge-to-Edge: يمنع تداخل المحتوى مع Navigation Bar ──
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int navBot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                         v.getPaddingRight(), navBot);
            return insets;
        });

        com.google.android.material.appbar.MaterialToolbar tb = findViewById(R.id.toolbar_notif);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvEmpty    = findViewById(R.id.tv_notif_empty);
        btnMarkAll = findViewById(R.id.btn_mark_all_read);
        rv         = findViewById(R.id.rv_notifications);

        adapter = new NotifAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnMarkAll.setOnClickListener(v -> markAllRead());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        notifRef = FirebaseDatabase.getInstance()
            .getReference("notifications").child(user.getUid());
        loadNotifications();
    }

    private void loadNotifications() {
        notifRef.orderByChild("timestamp").limitToLast(50)
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    items.clear();
                    for (DataSnapshot c : snap.getChildren()) {
                        NotifItem item = new NotifItem();
                        item.id         = c.getKey();
                        item.type       = safeStr(c, "type");
                        item.message    = safeStr(c, "message");
                        item.personName = safeStr(c, "personName");
                        item.reportId   = safeStr(c, "reportId");
                        Long ts = c.child("timestamp").getValue(Long.class);
                        item.timestamp  = ts != null ? ts : 0L;
                        Boolean read = c.child("read").getValue(Boolean.class);
                        item.read       = Boolean.TRUE.equals(read);
                        items.add(0, item); // أحدث أولاً
                    }
                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.notifyDataSetChanged();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private void markAllRead() {
        for (NotifItem item : items) {
            if (!item.read)
                notifRef.child(item.id).child("read").setValue(true);
        }
    }

    private String safeStr(DataSnapshot c, String key) {
        Object v = c.child(key).getValue();
        return v instanceof String ? (String) v : "";
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    // ── Model ──────────────────────────────────────────────────────────────
    static class NotifItem {
        String id, type, message, personName, reportId;
        long   timestamp;
        boolean read;
    }

    // ── Adapter ────────────────────────────────────────────────────────────
    class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup p, int t) {
            return new VH(getLayoutInflater().inflate(R.layout.item_notification, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            NotifItem item = items.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", new Locale("ar"));

            // أيقونة حسب النوع
            String icon;
            String title;
            switch (item.type) {
                case "face_match":
                case "found_matches_report":
                case "report_matches_found":
                    icon  = "🔍";
                    title = "تطابق وجه محتمل";
                    break;
                case "report_approved":
                    icon  = "✅";
                    title = "تمت الموافقة على بلاغك";
                    break;
                case "report_rejected":
                    icon  = "❌";
                    title = "تم رفض البلاغ";
                    break;
                case "new_message":
                    icon  = "💬";
                    title = "رسالة جديدة";
                    break;
                case "amber_alert":
                    icon  = "🚨";
                    title = "تنبيه عاجل في منطقتك";
                    break;
                default:
                    icon  = "🔔";
                    title = "إشعار";
            }

            h.tvIcon.setText(icon);
            h.tvTitle.setText(title);
            h.tvBody.setText(item.message.isEmpty() ? item.personName : item.message);
            h.tvTime.setText(item.timestamp > 0 ? sdf.format(new Date(item.timestamp)) : "");
            h.dotUnread.setVisibility(item.read ? View.GONE : View.VISIBLE);

            // خلفية مميزة للغير مقروء
            h.itemView.setAlpha(item.read ? 0.75f : 1.0f);

            h.itemView.setOnClickListener(v -> {
                // علّم كمقروء
                notifRef.child(item.id).child("read").setValue(true);
                item.read = true;
                notifyItemChanged(pos);

                // افتح تفاصيل البلاغ لو موجود
                if (!item.reportId.isEmpty()) {
                    startActivity(new android.content.Intent(
                        NotificationsActivity.this, CaseDetailActivity.class)
                        .putExtra("reportId", item.reportId));
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvTitle, tvBody, tvTime;
            View     dotUnread;
            VH(@NonNull View v) {
                super(v);
                tvIcon    = v.findViewById(R.id.tv_notif_icon);
                tvTitle   = v.findViewById(R.id.tv_notif_title);
                tvBody    = v.findViewById(R.id.tv_notif_body);
                tvTime    = v.findViewById(R.id.tv_notif_time);
                dotUnread = v.findViewById(R.id.view_unread_dot);
            }
        }
    }
}
