package com.missingpersons.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.*;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.AbuseReportHelper;
import com.missingpersons.app.utils.CoilImageLoader;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ChatActivity
 *
 * [مرحلة 1.3] ميزات جديدة:
 *   ✅ Typing Indicator — يظهر "... يكتب" للطرف الآخر
 *   ✅ Pagination — "تحميل رسائل أقدم" عند الوصول لأعلى القائمة
 *   ✅ إرسال صور — زر 📎 يفتح Gallery ويرفع الصورة لـ Firebase Storage
 *   ✅ Image messages — عرض الصور في الـ RecyclerView
 *   ✅ Edge-to-Edge
 */
public class ChatActivity extends AppCompatActivity {

    private static final int    PAGE_SIZE      = 30;
    private static final int    PICK_IMAGE_REQ = 301;
    private static final long   TYPING_TIMEOUT = 3000L; // 3 ثوانٍ

    private RecyclerView      rvMessages;
    private TextInputEditText etMessage;
    private MaterialButton    btnSend;
    private ImageButton       btnReportAbuse;
    private ImageButton       btnAttach;          // [1.3] زر إرسال صورة
    private TextView          tvTypingIndicator;  // [1.3] "... يكتب"
    private MaterialButton    btnLoadMore;        // [1.3] تحميل المزيد

    private final List<HashMap<String, Object>> messages = new ArrayList<>();
    private ChatAdapter adapter;

    private String myUid, otherUid, chatId, otherName;
    private boolean isAdminView = false;
    private DatabaseReference chatRef;
    private DatabaseReference usersRef;

    // [1.3] Pagination
    private long     oldestTimestamp = Long.MAX_VALUE;
    private boolean  allLoaded       = false;
    private boolean  isLoadingMore   = false;

    // [1.3] Typing
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable clearTypingRunnable;
    private boolean  iAmTyping = false;

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        myUid    = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        otherUid  = getIntent().getStringExtra("otherUid");
        otherName = getIntent().getStringExtra("otherName");
        isAdminView = getIntent().getBooleanExtra("adminView", false);

        String chatIdOverride = getIntent().getStringExtra("chatIdOverride");
        if (chatIdOverride != null && !chatIdOverride.isEmpty()) {
            chatId = chatIdOverride;
            isAdminView = true;
        }

        if (!isAdminView && (otherUid == null || otherUid.isEmpty())) {
            Toast.makeText(this, "❌ خطأ في بيانات المحادثة", Toast.LENGTH_SHORT).show();
            finish(); return;
        }

        if (getSupportActionBar() != null) {
            String title = otherName != null ? otherName : "المحادثة";
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // بناء chatId من الـ uid المرتبَين أبجدياً
        if (chatId == null && otherUid != null) {
            String[] sorted = {myUid, otherUid};
            Arrays.sort(sorted);
            chatId = sorted[0] + "_" + sorted[1];
        }

        chatRef  = FirebaseDatabase.getInstance().getReference("chats").child(chatId != null ? chatId : "");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        bindViews();
        setupAdapter();
        setupSendButton();
        setupAttachButton();       // [1.3]
        setupTypingWatcher();      // [1.3]
        setupLoadMoreButton();     // [1.3]
        listenForMessages();
        listenTyping();            // [1.3]
        markAllRead();

        // وضع الأدمن: قراءة فقط
        if (isAdminView) {
            etMessage.setEnabled(false);
            etMessage.setHint("👁️ وضع المراقبة — قراءة فقط");
            if (btnSend != null) btnSend.setEnabled(false);
            if (btnAttach != null) btnAttach.setEnabled(false);
        }
    }

    // ════════════════════════════════════════════════
    //  Views
    // ════════════════════════════════════════════════

    private void bindViews() {
        rvMessages        = findViewById(R.id.rv_messages);
        etMessage         = findViewById(R.id.et_message);
        btnSend           = findViewById(R.id.btn_send);
        btnReportAbuse    = findViewById(R.id.btn_report_abuse);
        btnAttach         = findViewById(R.id.btn_attach);
        tvTypingIndicator = findViewById(R.id.tv_typing_indicator);
        btnLoadMore       = findViewById(R.id.btn_load_more);

        if (btnReportAbuse != null) {
            if (isAdminView) btnReportAbuse.setVisibility(View.GONE);
            else btnReportAbuse.setOnClickListener(v -> showAbuseReportDialog());
        }
        if (tvTypingIndicator != null) tvTypingIndicator.setVisibility(View.GONE);
        if (btnLoadMore != null) {
            btnLoadMore.setVisibility(View.GONE);
            btnLoadMore.setText("⬆️ تحميل رسائل أقدم");
        }
    }

    private void setupAdapter() {
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        adapter = new ChatAdapter(messages, myUid, chatId != null ? chatId : "");
        rvMessages.setAdapter(adapter);
    }

    // ════════════════════════════════════════════════
    //  إرسال رسالة نصية
    // ════════════════════════════════════════════════

    private void setupSendButton() {
        if (btnSend != null) btnSend.setOnClickListener(v -> sendTextMessage());
    }

    private void sendTextMessage() {
        String text = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) return;

        if (!com.missingpersons.app.utils.RateLimiter.canSendMessage(this)) {
            Toast.makeText(this, "⚠️ أنت ترسل رسائل بسرعة كبيرة. انتظر قليلاً.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean senderIsAdmin = com.missingpersons.app.utils.RateLimiter.isAdmin(this);
        if (!senderIsAdmin && otherUid != null) {
            FirebaseDatabase.getInstance().getReference("user_settings")
                .child(otherUid).child("chatBlocked")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        Boolean blocked = snap.getValue(Boolean.class);
                        if (Boolean.TRUE.equals(blocked)) {
                            Toast.makeText(ChatActivity.this,
                                "🚫 هذا المستخدم أوقف استقبال الرسائل", Toast.LENGTH_SHORT).show();
                        } else {
                            doSendText(text);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { doSendText(text); }
                });
        } else {
            doSendText(text);
        }
    }

    private void doSendText(String text) {
        HashMap<String, Object> msg = new HashMap<>();
        msg.put("senderId",  myUid);
        msg.put("text",      text);
        msg.put("type",      "text");
        msg.put("timestamp", ServerValue.TIMESTAMP);
        msg.put("read",      false);

        chatRef.push().setValue(msg).addOnSuccessListener(x -> {
            etMessage.setText("");
            clearTyping();
            sendMessageNotification(text);
            if (otherUid != null)
                FirebaseDatabase.getInstance().getReference("unread_counts")
                    .child(otherUid).child(myUid)
                    .setValue(ServerValue.increment(1));
        });
    }

    // ════════════════════════════════════════════════
    //  [1.3] إرسال صورة
    // ════════════════════════════════════════════════

    private void setupAttachButton() {
        if (btnAttach == null) return;
        btnAttach.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setType("image/*");
            startActivityForResult(i, PICK_IMAGE_REQ);
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE_REQ && res == RESULT_OK && data != null && data.getData() != null) {
            uploadAndSendImage(data.getData());
        }
    }

    private void uploadAndSendImage(Uri imageUri) {
        if (chatId == null) return;
        Toast.makeText(this, "⏳ جارٍ رفع الصورة...", Toast.LENGTH_SHORT).show();

        String imgPath = "chat_images/" + chatId + "/" + System.currentTimeMillis() + ".jpg";
        StorageReference ref = FirebaseStorage.getInstance().getReference(imgPath);

        ref.putFile(imageUri)
            .addOnSuccessListener(snap -> ref.getDownloadUrl().addOnSuccessListener(dlUri -> {
                HashMap<String, Object> msg = new HashMap<>();
                msg.put("senderId",  myUid);
                msg.put("text",      "📷 صورة");
                msg.put("imageUrl",  dlUri.toString());
                msg.put("type",      "image");
                msg.put("timestamp", ServerValue.TIMESTAMP);
                msg.put("read",      false);
                chatRef.push().setValue(msg).addOnSuccessListener(x -> {
                    sendMessageNotification("📷 صورة");
                    if (otherUid != null)
                        FirebaseDatabase.getInstance().getReference("unread_counts")
                            .child(otherUid).child(myUid)
                            .setValue(ServerValue.increment(1));
                });
            }))
            .addOnFailureListener(e ->
                Toast.makeText(this, "❌ فشل رفع الصورة", Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════
    //  [1.3] Typing Indicator
    // ════════════════════════════════════════════════

    private void setupTypingWatcher() {
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (chatId == null || myUid.isEmpty()) return;
                if (s.length() > 0) {
                    if (!iAmTyping) {
                        iAmTyping = true;
                        FirebaseDatabase.getInstance().getReference("typing")
                            .child(chatId).child(myUid).setValue(true);
                    }
                    // reset timeout
                    typingHandler.removeCallbacks(clearTypingRunnable != null
                        ? clearTypingRunnable : () -> {});
                    clearTypingRunnable = () -> clearTyping();
                    typingHandler.postDelayed(clearTypingRunnable, TYPING_TIMEOUT);
                } else {
                    clearTyping();
                }
            }
        });
    }

    private void clearTyping() {
        if (!iAmTyping) return;
        iAmTyping = false;
        if (chatId != null)
            FirebaseDatabase.getInstance().getReference("typing")
                .child(chatId).child(myUid).setValue(false);
    }

    private void listenTyping() {
        if (chatId == null || otherUid == null) return;
        FirebaseDatabase.getInstance().getReference("typing")
            .child(chatId).child(otherUid)
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    Boolean typing = snap.getValue(Boolean.class);
                    if (tvTypingIndicator == null) return;
                    if (Boolean.TRUE.equals(typing)) {
                        String who = otherName != null ? otherName.split(" ")[0] : "الطرف الآخر";
                        tvTypingIndicator.setText("✍️ " + who + " يكتب...");
                        tvTypingIndicator.setVisibility(View.VISIBLE);
                    } else {
                        tvTypingIndicator.setVisibility(View.GONE);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ════════════════════════════════════════════════
    //  [1.3] Pagination — Load More
    // ════════════════════════════════════════════════

    private void setupLoadMoreButton() {
        if (btnLoadMore == null) return;
        btnLoadMore.setOnClickListener(v -> loadOlderMessages());
    }

    private void loadOlderMessages() {
        if (isLoadingMore || allLoaded || chatId == null) return;
        isLoadingMore = true;
        if (btnLoadMore != null) btnLoadMore.setText("⏳ جارٍ التحميل...");

        chatRef.orderByChild("timestamp")
            .endAt(oldestTimestamp - 1)
            .limitToLast(PAGE_SIZE)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    List<HashMap<String, Object>> older = new ArrayList<>();
                    for (DataSnapshot c : snap.getChildren()) {
                        HashMap<String, Object> msg = new HashMap<>();
                        for (DataSnapshot f : c.getChildren()) msg.put(f.getKey(), f.getValue());
                        msg.put("msgId", c.getKey());
                        Object ts = msg.get("timestamp");
                        if (ts instanceof Long && (Long) ts < oldestTimestamp)
                            oldestTimestamp = (Long) ts;
                        older.add(msg);
                    }
                    if (older.isEmpty()) {
                        allLoaded = true;
                        if (btnLoadMore != null) {
                            btnLoadMore.setText("لا توجد رسائل أقدم");
                            btnLoadMore.setEnabled(false);
                        }
                    } else {
                        messages.addAll(0, older);
                        adapter.notifyItemRangeInserted(0, older.size());
                        if (btnLoadMore != null) btnLoadMore.setText("⬆️ تحميل رسائل أقدم");
                    }
                    isLoadingMore = false;
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {
                    isLoadingMore = false;
                    if (btnLoadMore != null) btnLoadMore.setText("⬆️ تحميل رسائل أقدم");
                }
            });
    }

    // ════════════════════════════════════════════════
    //  Listen for new messages (real-time)
    // ════════════════════════════════════════════════

    private void listenForMessages() {
        chatRef.orderByChild("timestamp")
            .limitToLast(PAGE_SIZE)
            .addChildEventListener(new ChildEventListener() {
                @Override public void onChildAdded(@NonNull DataSnapshot snap, String prev) {
                    HashMap<String, Object> msg = new HashMap<>();
                    for (DataSnapshot f : snap.getChildren()) msg.put(f.getKey(), f.getValue());
                    msg.put("msgId", snap.getKey());

                    // تتبع أقدم timestamp للـ pagination
                    Object ts = msg.get("timestamp");
                    if (ts instanceof Long && (Long) ts < oldestTimestamp)
                        oldestTimestamp = (Long) ts;

                    messages.add(msg);
                    adapter.notifyItemInserted(messages.size() - 1);
                    rvMessages.scrollToPosition(messages.size() - 1);

                    // إظهار زر "تحميل المزيد" بعد التحميل الأول
                    if (messages.size() >= PAGE_SIZE && btnLoadMore != null)
                        btnLoadMore.setVisibility(View.VISIBLE);

                    String senderId = (String) msg.get("senderId");
                    if (!myUid.equals(senderId)) {
                        snap.getRef().child("read").setValue(true);
                        if (otherUid != null)
                            FirebaseDatabase.getInstance().getReference("unread_counts")
                                .child(myUid).child(otherUid).setValue(0);
                    }
                }
                @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
                @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
                @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    // ════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════

    private void markAllRead() {
        if (otherUid == null || otherUid.isEmpty()) return;
        FirebaseDatabase.getInstance().getReference("unread_counts")
            .child(myUid).child(otherUid).setValue(0);
    }

    private void sendMessageNotification(String messageText) {
        if (otherUid == null || otherUid.isEmpty()) return;
        usersRef.child(otherUid).child("fcmToken")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    String token = snap.getValue(String.class);
                    if (token == null || token.isEmpty()) return;
                    HashMap<String, Object> notif = new HashMap<>();
                    notif.put("type",      "chat_message");
                    notif.put("fromUid",   myUid);
                    notif.put("chatId",    chatId);
                    notif.put("message",   messageText.length() > 50
                                           ? messageText.substring(0, 50) + "..." : messageText);
                    notif.put("timestamp", System.currentTimeMillis());
                    notif.put("read",      false);
                    FirebaseDatabase.getInstance().getReference("notifications")
                        .child(otherUid).push().setValue(notif);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    private void showAbuseReportDialog() {
        String[] reasons = {"محتوى مسيء أو مزعج", "انتحال شخصية",
                            "احتيال أو ابتزاز", "معلومات مضللة", "أخرى"};
        new AlertDialog.Builder(this)
            .setTitle("🚨 الإبلاغ عن إساءة")
            .setItems(reasons, (d, which) -> {
                new AlertDialog.Builder(this)
                    .setTitle("تأكيد البلاغ")
                    .setMessage("الإبلاغ عن: \"" + reasons[which] + "\"?")
                    .setPositiveButton("إبلاغ", (d2, w) ->
                        AbuseReportHelper.submitReport(this,
                            AbuseReportHelper.ReportTarget.MEMBER,
                            otherUid, otherName, reasons[which], "من المحادثة",
                            ok -> runOnUiThread(() ->
                                Toast.makeText(this,
                                    ok ? "✅ تم الإبلاغ" : "❌ فشل",
                                    Toast.LENGTH_SHORT).show())))
                    .setNegativeButton("إلغاء", null).show();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearTyping();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    // ════════════════════════════════════════════════
    //  Adapter — يدعم text + image
    // ════════════════════════════════════════════════

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

        private static final int TYPE_SENT_TEXT  = 0;
        private static final int TYPE_RECV_TEXT  = 1;
        private static final int TYPE_SENT_IMG   = 2;
        private static final int TYPE_RECV_IMG   = 3;

        private final List<HashMap<String, Object>> msgs;
        private final String myUid;
        private final String chatIdRef;

        ChatAdapter(List<HashMap<String, Object>> msgs, String myUid, String chatIdRef) {
            this.msgs = msgs; this.myUid = myUid; this.chatIdRef = chatIdRef;
        }

        @Override
        public int getItemViewType(int pos) {
            HashMap<String, Object> m = msgs.get(pos);
            String sender = (String) m.getOrDefault("senderId", "");
            String type   = (String) m.getOrDefault("type", "text");
            boolean isMine = myUid.equals(sender);
            if ("image".equals(type)) return isMine ? TYPE_SENT_IMG : TYPE_RECV_IMG;
            return isMine ? TYPE_SENT_TEXT : TYPE_RECV_TEXT;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout;
            switch (viewType) {
                case TYPE_SENT_TEXT:  layout = R.layout.item_message_sent;     break;
                case TYPE_RECV_TEXT:  layout = R.layout.item_message_received; break;
                case TYPE_SENT_IMG:   layout = R.layout.item_message_sent;     break;
                case TYPE_RECV_IMG:   layout = R.layout.item_message_received; break;
                default:              layout = R.layout.item_message_received;
            }
            return new VH(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HashMap<String, Object> m = msgs.get(pos);
            String type = (String) m.getOrDefault("type", "text");

            // زمن الرسالة
            Object ts = m.get("timestamp");
            if (h.tvTime != null && ts instanceof Long)
                h.tvTime.setText(new SimpleDateFormat("HH:mm", new Locale("ar"))
                    .format(new Date((Long) ts)));

            if ("image".equals(type)) {
                // [1.3] عرض الصورة
                String imgUrl = (String) m.getOrDefault("imageUrl", "");
                if (h.tvText != null) h.tvText.setText("📷 صورة");
                if (h.ivImage != null && !imgUrl.isEmpty()) {
                    h.ivImage.setVisibility(View.VISIBLE);
                    CoilImageLoader.load(h.ivImage.getContext(), imgUrl, h.ivImage);
                    h.ivImage.setOnClickListener(v -> {
                        // فتح الصورة في نافذة كاملة
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(imgUrl));
                        i.setDataAndType(Uri.parse(imgUrl), "image/*");
                        v.getContext().startActivity(i);
                    });
                }
            } else {
                if (h.tvText != null) h.tvText.setText((String) m.getOrDefault("text", ""));
                if (h.ivImage != null) h.ivImage.setVisibility(View.GONE);
            }

            // حذف بالضغط المطوّل
            String sender = (String) m.get("senderId");
            if (myUid.equals(sender) && h.tvText != null) {
                h.tvText.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(v.getContext())
                        .setMessage("حذف هذه الرسالة؟")
                        .setPositiveButton("حذف", (d, w) -> {
                            String msgId = (String) m.get("msgId");
                            if (msgId != null) {
                                FirebaseDatabase.getInstance()
                                    .getReference("chats").child(chatIdRef).child(msgId)
                                    .removeValue()
                                    .addOnSuccessListener(x -> {
                                        int idx = msgs.indexOf(m);
                                        if (idx >= 0) { msgs.remove(idx); notifyItemRemoved(idx); }
                                    });
                            }
                        })
                        .setNegativeButton("إلغاء", null).show();
                    return true;
                });
            }
        }

        @Override public int getItemCount() { return msgs.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView  tvText, tvTime;
            ImageView ivImage;  // [1.3]
            VH(@NonNull View v) {
                super(v);
                tvText  = v.findViewById(R.id.tv_msg_text);
                tvTime  = v.findViewById(R.id.tv_msg_time);
                ivImage = v.findViewById(R.id.iv_msg_image); // nullable
            }
        }
    }
}
