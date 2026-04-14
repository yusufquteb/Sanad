package com.missingpersons.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.*;
import com.missingpersons.app.utils.CommentManager;
import com.missingpersons.app.utils.MatchConfidenceHelper;
import com.missingpersons.app.utils.PointsManager;
import java.text.SimpleDateFormat;
import java.util.*;

public class CaseDetailActivity extends AppCompatActivity {

    private String reportId;
    private String personName, location, gender;
    private int    personAge;
    private long   timestamp;
    private String reporterId;
    private Bitmap mainPhotoBitmap;

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_case_detail);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int navBot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                         v.getPaddingRight(), navBot);
            return insets;
        });

        reportId = getIntent().getStringExtra("reportId");

        // Deep link: sanad://report/{reportId}
        if (reportId == null && getIntent() != null && getIntent().getData() != null) {
            reportId = ShareHelper.extractReportId(getIntent().getData());
        }
        if (reportId == null) { finish(); return; }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("تفاصيل الحالة");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        AnalyticsHelper.logScreenView("CaseDetail");

        // Ads: interstitial بعد كل 5 حالات
        android.content.SharedPreferences prefs =
            getSharedPreferences("ads_prefs", MODE_PRIVATE);
        int detailViews = prefs.getInt("detail_view_count", 0) + 1;
        prefs.edit().putInt("detail_view_count", detailViews).apply();
        if (detailViews % 5 == 0) {
            AdsManager.getInstance(this).showInterstitialAd(this, null);
        }

        ensureAuthAndLoad();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Auth & Load
    // ════════════════════════════════════════════════════════════════════

    private void ensureAuthAndLoad() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            loadCaseDetails();
            return;
        }
        auth.signInAnonymously()
            .addOnSuccessListener(r -> loadCaseDetails())
            .addOnFailureListener(e -> {
                Toast.makeText(this, "❌ تعذّر الاتصال: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
                loadCaseDetails();
            });
    }

    private void loadCaseDetails() {
        FirebaseDatabase.getInstance().getReference("reports")
            .child(reportId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    if (!snap.exists()) {
                        Toast.makeText(CaseDetailActivity.this,
                            "⚠️ الحالة غير موجودة أو تم حذفها", Toast.LENGTH_LONG).show();
                        return;
                    }
                    bindData(snap);
                    loadTimeline(snap);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError e) {
                    Toast.makeText(CaseDetailActivity.this,
                        "⚠️ تعذّر تحميل البيانات: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
    }

    // ════════════════════════════════════════════════════════════════════
    //  bindData
    // ════════════════════════════════════════════════════════════════════

    private void bindData(DataSnapshot snap) {
        try {
            TextView     tvName      = findViewById(R.id.tv_detail_name);
            TextView     tvAddr      = findViewById(R.id.tv_detail_addr);
            TextView     tvAge       = findViewById(R.id.tv_detail_age);
            TextView     tvGender    = findViewById(R.id.tv_detail_gender);
            TextView     tvTime      = findViewById(R.id.tv_detail_time);
            ImageView    ivMain      = findViewById(R.id.iv_detail_main);
            LinearLayout llPhotos    = findViewById(R.id.ll_detail_photos);
            MaterialButton btnChat   = findViewById(R.id.btn_open_chat);
            TextView     tvWarning   = findViewById(R.id.tv_safety_warning);
            TextView     tvChatStatus = findViewById(R.id.tv_chat_status);
            MaterialButton btnShare  = findViewById(R.id.btn_share_report);
            MaterialButton btnPDF    = findViewById(R.id.btn_export_pdf);
            ImageView    ivQR        = findViewById(R.id.iv_qr_code);

            // ── قراءة البيانات ──────────────────────────────────────
            personName = snap.child("personName").getValue(String.class);
            gender     = snap.child("personGender").getValue(String.class);
            reporterId = snap.child("reporterId").getValue(String.class);

            // بناء العنوان من محافظة/مدينة/حي، ثم manualAddress كاحتياط
            String gov    = snap.child("governorate").getValue(String.class);
            String city   = snap.child("city").getValue(String.class);
            String area   = snap.child("area").getValue(String.class);
            String manual = snap.child("manualAddress").getValue(String.class);
            if (gov != null && !gov.isEmpty()) {
                StringBuilder lb = new StringBuilder(gov);
                if (city != null && !city.isEmpty()) lb.append(" ← ").append(city);
                if (area != null && !area.isEmpty()) lb.append(" ← ").append(area);
                location = lb.toString();
            } else {
                location = manual;
            }

            // personAge يُخزَّن Long أو String
            Object ageRaw = snap.child("personAge").getValue();
            if (ageRaw instanceof Long)         personAge = ((Long) ageRaw).intValue();
            else if (ageRaw instanceof Integer) personAge = (Integer) ageRaw;
            else                                personAge = 0;

            Long ts = snap.child("timestamp").getValue(Long.class);
            timestamp = ts != null ? ts : 0;

            // ── ملء الـ Views ────────────────────────────────────────
            if (tvName   != null) tvName.setText(personName != null ? personName : "غير محدد");
            if (tvAddr   != null) tvAddr.setText("📍 " + (location != null ? location : "غير محدد"));
            if (tvAge    != null) tvAge.setText("العمر: " + (personAge > 0 ? personAge : "؟"));
            if (tvGender != null) tvGender.setText("النوع: " + (gender != null ? gender : "غير محدد"));
            if (tvWarning != null)
                tvWarning.setText("⚠️ تحذير: لا تلتقِ بأحد في مكان منعزل. لا تدفع مالاً مقابل أي معلومة.");
            if (tvTime != null && timestamp > 0)
                tvTime.setText("🕐 " + new SimpleDateFormat("dd/MM/yyyy HH:mm",
                    new Locale("ar")).format(new Date(timestamp)));

            // تاريخ العثور
            TextView tvFoundDate = findViewById(R.id.tv_detail_found_date);
            Long foundTs = snap.child("foundDate").getValue(Long.class);
            if (tvFoundDate != null) {
                if (foundTs != null && foundTs > 0) {
                    tvFoundDate.setVisibility(View.VISIBLE);
                    tvFoundDate.setText("📅 تاريخ العثور: " +
                        new SimpleDateFormat("dd/MM/yyyy", new Locale("ar"))
                            .format(new Date(foundTs)));
                } else {
                    tvFoundDate.setVisibility(View.GONE);
                }
            }

            // ── الصور ────────────────────────────────────────────────
            if (ivMain != null) {
                Object urlsObj = snap.child("imageUrls").getValue();
                String firstUrl = null;
                if (urlsObj instanceof List && !((List<?>) urlsObj).isEmpty()) {
                    firstUrl = ((List<?>) urlsObj).get(0).toString();
                    if (llPhotos != null) {
                        List<?> urls = (List<?>) urlsObj;
                        for (int i = 1; i < urls.size(); i++) {
                            ImageView iv = new ImageView(this);
                            LinearLayout.LayoutParams lp =
                                new LinearLayout.LayoutParams(150, 150);
                            lp.setMargins(4, 0, 4, 0);
                            iv.setLayoutParams(lp);
                            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            CoilImageLoader.loadRounded(this, urls.get(i).toString(),
                                iv, R.drawable.ic_face_placeholder, 12f);
                            llPhotos.addView(iv);
                        }
                    }
                }
                if (firstUrl == null)
                    firstUrl = snap.child("imageUrl").getValue(String.class);
                if (firstUrl != null && !firstUrl.isEmpty()) {
                    CoilImageLoader.loadRounded(this, firstUrl,
                        ivMain, R.drawable.ic_face_placeholder, 12f);
                    final String finalUrl = firstUrl;
                    new Thread(() -> {
                        try {
                            java.net.URL url = new java.net.URL(finalUrl);
                            Bitmap bmp = android.graphics.BitmapFactory
                                .decodeStream(url.openConnection().getInputStream());
                            if (bmp != null) mainPhotoBitmap = bmp;
                        } catch (Exception ignored) {}
                    }).start();
                }
            }

            // ── [مرحلة 2.2] Match Confidence ─────────────────────────
            // يعرض نسبة التطابق من match_candidates لصاحب البلاغ والمدير فقط
            LinearLayout llMatchContainer = findViewById(R.id.ll_match_confidence_container);
            MatchConfidenceHelper.loadAndDisplay(this, reportId, reporterId, llMatchContainer);

            // ── QR Code ──────────────────────────────────────────────
            if (ivQR != null) {
                Bitmap qr = QRCodeHelper.generateReportQR(reportId);
                if (qr != null) ivQR.setImageBitmap(qr);
            }

            // ── التعليقات ─────────────────────────────────────────────
            androidx.recyclerview.widget.RecyclerView rvComments =
                findViewById(R.id.rv_comments);
            TextView tvCommentsCount = findViewById(R.id.tv_comments_count);
            TextView tvCommentsEmpty = findViewById(R.id.tv_comments_empty);
            TextView btnAddComment   = findViewById(R.id.btn_add_comment);

            if (rvComments != null) {
                CommentManager.loadComments(
                    CaseDetailActivity.this, reportId, rvComments, tvCommentsCount);
                if (tvCommentsEmpty != null)
                    tvCommentsEmpty.setVisibility(View.GONE);
            }

            // زر تقييم المُبلِّغ
            TextView btnRate = findViewById(R.id.btn_rate_reporter);
            if (btnRate != null && reporterId != null && !reporterId.isEmpty()) {
                final String fReporterId = reporterId;
                final String fPersonName = personName;
                btnRate.setOnClickListener(bv ->
                    RatingManager.showRatingDialog(CaseDetailActivity.this,
                        fReporterId, fPersonName));
            }
            if (btnAddComment != null)
                btnAddComment.setOnClickListener(bv ->
                    CommentManager.showAddCommentDialog(CaseDetailActivity.this, reportId));

            // ── تصدير PDF ─────────────────────────────────────────────
            if (btnPDF != null) {
                btnPDF.setOnClickListener(v ->
                    PDFExportHelper.exportReport(this, personName, location,
                        String.valueOf(personAge), gender, timestamp,
                        reporterId, reportId, mainPhotoBitmap));
            }

            // ── BookmarkManager ───────────────────────────────────────
            MaterialButton btnBookmark = findViewById(R.id.btn_bookmark);
            if (btnBookmark != null) {
                updateBookmarkButton(btnBookmark, reportId);
                btnBookmark.setOnClickListener(bv -> {
                    boolean saved = BookmarkManager.toggle(CaseDetailActivity.this, reportId);
                    updateBookmarkButton(btnBookmark, reportId);
                    Toast.makeText(CaseDetailActivity.this,
                        saved ? "✅ تم حفظ البلاغ في المفضلة"
                              : "🗑️ تم إزالة البلاغ من المفضلة",
                        Toast.LENGTH_SHORT).show();
                });
            }

            // ── مشاركة ────────────────────────────────────────────────
            if (btnShare != null) {
                btnShare.setOnClickListener(v ->
                    ShareHelper.shareReport(this, personName, location,
                        String.valueOf(personAge), gender, timestamp,
                        reportId, mainPhotoBitmap));
            }
            MaterialButton btnWA = findViewById(R.id.btn_whatsapp_share);
            if (btnWA != null) {
                btnWA.setOnClickListener(v ->
                    ShareHelper.shareToWhatsApp(this, personName, location,
                        String.valueOf(personAge), gender, timestamp,
                        reportId, mainPhotoBitmap));
            }

            // ── زر المحادثة ───────────────────────────────────────────
            String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
            Boolean chatEnabled  = snap.child("chatEnabled").getValue(Boolean.class);
            boolean isChatAllowed = chatEnabled == null || chatEnabled;

            if (reporterId != null && !reporterId.equals(myUid)) {
                if (isChatAllowed) {
                    if (btnChat != null) {
                        btnChat.setVisibility(View.VISIBLE);
                        btnChat.setEnabled(true);
                        btnChat.setText("💬 تواصل مع صاحب البلاغ");
                    }
                    if (tvChatStatus != null) tvChatStatus.setVisibility(View.GONE);
                    String reporterName = snap.child("reporterName").getValue(String.class);
                    if (btnChat != null) {
                        btnChat.setOnClickListener(v -> {
                            Intent intent = new Intent(this, ChatActivity.class);
                            intent.putExtra("otherUid", reporterId);
                            intent.putExtra("otherName",
                                reporterName != null ? reporterName : "صاحب البلاغ");
                            startActivity(intent);
                        });
                    }
                } else {
                    if (btnChat != null) {
                        btnChat.setVisibility(View.VISIBLE);
                        btnChat.setEnabled(false);
                        btnChat.setText("🔒 الشات غير متاح");
                    }
                    if (tvChatStatus != null) {
                        tvChatStatus.setVisibility(View.VISIBLE);
                        tvChatStatus.setText("ℹ️ صاحب البلاغ أغلق خاصية التواصل");
                    }
                }
            } else if (reporterId != null && reporterId.equals(myUid)) {
                if (btnChat != null) btnChat.setVisibility(View.GONE);
                addUpdateButton(reportId, reportNode(snap));
            }

            // ── أزرار الأدمن ──────────────────────────────────────────
            if (RateLimiter.isAdmin(this)) {
                addAdminActionButtons(reportId, reporterId,
                    snap.child("pinned").getValue(Boolean.class));
            }

        } catch (Exception ex) {
            android.util.Log.e("CaseDetail", "bindData error: " + ex.getMessage(), ex);
            Toast.makeText(this, "⚠️ خطأ في عرض البيانات", Toast.LENGTH_SHORT).show();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Timeline
    // ════════════════════════════════════════════════════════════════════

    private void loadTimeline(DataSnapshot snap) {
        LinearLayout llTimeline = findViewById(R.id.ll_timeline);
        if (llTimeline == null) return;
        llTimeline.removeAllViews();

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("ar"));

        Long ts = snap.child("timestamp").getValue(Long.class);
        if (ts != null)
            addTimelineItem(llTimeline, "📤 تم رفع البلاغ",
                sdf.format(new Date(ts)), "#1565C0");

        Long approvedAt = snap.child("approvedAt").getValue(Long.class);
        String approvedBy = snap.child("approvedBy").getValue(String.class);
        if (approvedAt != null)
            addTimelineItem(llTimeline,
                "✅ تمت الموافقة" + (approvedBy != null ? " بواسطة " + approvedBy : ""),
                sdf.format(new Date(approvedAt)), "#2E7D32");

        Long foundDate = snap.child("foundDate").getValue(Long.class);
        if (foundDate != null && foundDate > 0)
            addTimelineItem(llTimeline, "🎉 تم العثور عليه",
                new SimpleDateFormat("dd/MM/yyyy", new Locale("ar"))
                    .format(new Date(foundDate)), "#F57C00");

        Long editedAt = snap.child("editedAt").getValue(Long.class);
        String editedBy = snap.child("editedBy").getValue(String.class);
        if (editedAt != null)
            addTimelineItem(llTimeline,
                "✏️ تم التعديل" + (editedBy != null ? " بواسطة " + editedBy : ""),
                sdf.format(new Date(editedAt)), "#FF9800");

        if (llTimeline.getChildCount() == 0)
            addTimelineItem(llTimeline, "⏳ في انتظار المراجعة", "", "#757575");

        String rId = snap.getKey();
        if (rId == null) return;
        FirebaseDatabase.getInstance().getReference("report_updates").child(rId)
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot updates) {
                    for (DataSnapshot u : updates.getChildren()) {
                        String text = u.child("text").getValue(String.class);
                        String type = u.child("type").getValue(String.class);
                        Long   uTs  = u.child("timestamp").getValue(Long.class);
                        if (text == null) continue;
                        String label = (type != null ? type + ": " : "") + text;
                        String time  = uTs != null ? sdf.format(new Date(uTs)) : "";
                        addTimelineItem(llTimeline, label, time, "#6A1B9A");
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private void addTimelineItem(LinearLayout container, String title,
                                  String time, String color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(16, 16);
        dotLp.setMargins(0, 0, 12, 0);
        dot.setLayoutParams(dotLp);
        dot.setBackgroundColor(android.graphics.Color.parseColor(color));
        row.addView(dot);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(13);
        tvTitle.setTextColor(android.graphics.Color.parseColor(color));
        textCol.addView(tvTitle);

        if (!time.isEmpty()) {
            TextView tvTime = new TextView(this);
            tvTime.setText(time);
            tvTime.setTextSize(11);
            tvTime.setTextColor(0xFF999999);
            textCol.addView(tvTime);
        }

        row.addView(textCol);
        container.addView(row);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private String reportNode(DataSnapshot snap) {
        String type = snap.child("reportType").getValue(String.class);
        if ("found".equals(type))    return "found_persons";
        if ("sighting".equals(type)) return "sightings";
        return "reports";
    }

    private void addUpdateButton(String reportId, String node) {
        MaterialButton btnUpdate = findViewById(R.id.btn_add_update);
        if (btnUpdate == null) return;
        btnUpdate.setVisibility(View.VISIBLE);
        btnUpdate.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportUpdateActivity.class);
            intent.putExtra("reportId",   reportId);
            intent.putExtra("reportNode", node);
            startActivity(intent);
        });
    }

    private void addAdminActionButtons(String reportId, String reporterId,
                                        Boolean isPinned) {
        LinearLayout container = findViewById(R.id.layout_admin_actions);
        if (container == null) return;
        container.setVisibility(View.VISIBLE);

        // زر تثبيت
        MaterialButton btnPin = new MaterialButton(this);
        boolean pinned = Boolean.TRUE.equals(isPinned);
        btnPin.setText(pinned ? "📌 إلغاء التثبيت" : "📌 تثبيت البلاغ");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        btnPin.setLayoutParams(lp);
        btnPin.setOnClickListener(v -> {
            boolean newState = !Boolean.TRUE.equals(isPinned);
            FirebaseDatabase.getInstance().getReference("reports")
                .child(reportId).child("pinned").setValue(newState)
                .addOnSuccessListener(x -> {
                    btnPin.setText(newState ? "📌 إلغاء التثبيت" : "📌 تثبيت البلاغ");
                    Toast.makeText(this,
                        newState ? "📌 تم تثبيت البلاغ" : "تم إلغاء التثبيت",
                        Toast.LENGTH_SHORT).show();
                });
        });
        container.addView(btnPin);

        // زر رسالة للعضو
        if (reporterId != null && !reporterId.isEmpty()) {
            MaterialButton btnAdminMsg = new MaterialButton(this);
            btnAdminMsg.setText("✉️ رسالة للعضو");
            btnAdminMsg.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            btnAdminMsg.setOnClickListener(v -> showAdminMessageDialog(reporterId));
            container.addView(btnAdminMsg);
        }
    }

    private void showAdminMessageDialog(String targetUid) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("اكتب رسالتك للعضو...");
        et.setMaxLines(4);
        et.setPadding(40, 20, 40, 20);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("✉️ رسالة مباشرة للعضو")
            .setView(et)
            .setPositiveButton("إرسال", (d, w) -> {
                String msg = et.getText().toString().trim();
                if (msg.isEmpty()) return;

                String adminUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "admin";

                String chatId = adminUid.compareTo(targetUid) < 0
                    ? adminUid + "_" + targetUid
                    : targetUid + "_" + adminUid;

                HashMap<String, Object> chatMsg = new HashMap<>();
                chatMsg.put("senderId",  adminUid);
                chatMsg.put("text",      "🔔 [الإدارة]: " + msg);
                chatMsg.put("timestamp", System.currentTimeMillis());
                chatMsg.put("read",      false);
                chatMsg.put("fromAdmin", true);

                FirebaseDatabase.getInstance().getReference("chats")
                    .child(chatId).push().setValue(chatMsg);

                HashMap<String, Object> notif = new HashMap<>();
                notif.put("type",      "admin_message");
                notif.put("message",   "📩 رسالة جديدة من الإدارة: " +
                    msg.substring(0, Math.min(msg.length(), 60)));
                notif.put("chatId",    chatId);
                notif.put("timestamp", System.currentTimeMillis());
                notif.put("read",      false);

                FirebaseDatabase.getInstance().getReference("notifications")
                    .child(targetUid).push().setValue(notif)
                    .addOnSuccessListener(x ->
                        Toast.makeText(this, "✅ تم إرسال الرسالة",
                            Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void updateBookmarkButton(MaterialButton btn, String reportId) {
        boolean saved = BookmarkManager.isBookmarked(this, reportId);
        btn.setText(saved ? "🔖 محفوظ" : "🔖 حفظ");
        btn.setAlpha(saved ? 1.0f : 0.7f);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
