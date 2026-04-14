package com.missingpersons.app.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AdminChatsActivity — الأدمن يطّلع على جميع المحادثات
 *
 * ✅ Search bar — بحث بالاسم
 * ✅ Stats card محسّن مع أيقونة
 * ✅ Empty state محسّن
 * ✅ Entry animations
 */
public class AdminChatsActivity extends AppCompatActivity {

    // ─── Views ──────────────────────────────────────────────────────
    private RecyclerView             recyclerView;
    private LinearProgressIndicator  progressIndicator;
    private LinearLayout             layoutEmpty;
    private TextView                 tvEmpty, tvCount;
    private TextInputEditText        etSearch;

    // ─── Firebase ───────────────────────────────────────────────────
    private DatabaseReference chatsRef, usersRef;

    // ─── Data ────────────────────────────────────────────────────────
    private final List<AdminChatItem> allChatItems      = new ArrayList<>();
    private final List<AdminChatItem> filteredChatItems = new ArrayList<>();
    private final HashMap<String, String> userNames     = new HashMap<>();
    private AdminChatsAdapter adapter;

    // ────────────────────────────────────────────────────────────────

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_chats);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        // ── Admin only — عبر RoleManager (Firebase DB) لا ADMIN_EMAIL ──
        if (!com.missingpersons.app.utils.RoleManager.get().isAdmin()) {
            Toast.makeText(this, "غير مصرح", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ── Toolbar ──────────────────────────────────────────────────
        MaterialToolbar toolbar = findViewById(R.id.toolbar_admin_chats);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("محادثات المستخدمين");
        }

        // ── Bind Views ───────────────────────────────────────────────
        recyclerView      = findViewById(R.id.rv_admin_chats);
        progressIndicator = findViewById(R.id.progress_admin_chats);
        layoutEmpty       = findViewById(R.id.layout_empty_chats);
        tvEmpty           = findViewById(R.id.tv_empty_admin_chats);
        tvCount           = findViewById(R.id.tv_admin_chats_count);
        etSearch          = findViewById(R.id.et_search_chats);

        // ── RecyclerView ─────────────────────────────────────────────
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminChatsAdapter(filteredChatItems, this::openChat);
        recyclerView.setAdapter(adapter);

        // ── Search ───────────────────────────────────────────────────
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    filterChats(s.toString().trim());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // ── Firebase ─────────────────────────────────────────────────
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        loadAllUserNames();
    }

    // ────────────────────────────────────────────────────────────────
    //  Search / Filter
    // ────────────────────────────────────────────────────────────────

    private void filterChats(String query) {
        filteredChatItems.clear();
        if (query.isEmpty()) {
            filteredChatItems.addAll(allChatItems);
        } else {
            String lower = query.toLowerCase();
            for (AdminChatItem item : allChatItems) {
                if (item.name1.toLowerCase().contains(lower) ||
                    item.name2.toLowerCase().contains(lower)) {
                    filteredChatItems.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState(query.isEmpty() && filteredChatItems.isEmpty());
    }

    // ────────────────────────────────────────────────────────────────
    //  Data Loading
    // ────────────────────────────────────────────────────────────────

    private void loadAllUserNames() {
        if (progressIndicator != null) progressIndicator.setVisibility(View.VISIBLE);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                for (DataSnapshot child : snap.getChildren()) {
                    String name = child.child("name").getValue(String.class);
                    userNames.put(child.getKey(), name != null ? name : "مجهول");
                }
                loadAllChats();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (progressIndicator != null) progressIndicator.setVisibility(View.GONE);
            }
        });
    }

    private void loadAllChats() {
        chatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allChatItems.clear();

                for (DataSnapshot chatSnap : snapshot.getChildren()) {
                    String chatId = chatSnap.getKey();
                    if (chatId == null) continue;

                    String[] uids = chatId.split("_");
                    if (uids.length != 2) continue;

                    String name1 = userNames.getOrDefault(uids[0], uids[0]);
                    String name2 = userNames.getOrDefault(uids[1], uids[1]);

                    String lastMsg  = "";
                    long   lastTime = 0;
                    int    msgCount = (int) chatSnap.getChildrenCount();

                    for (DataSnapshot msgSnap : chatSnap.getChildren()) {
                        Long ts = msgSnap.child("timestamp").getValue(Long.class);
                        if (ts != null && ts > lastTime) {
                            lastTime = ts;
                            String text = msgSnap.child("text").getValue(String.class);
                            lastMsg = text != null ? text : "";
                        }
                    }

                    allChatItems.add(new AdminChatItem(
                        chatId, uids[0], uids[1], name1, name2,
                        lastMsg, lastTime, msgCount));
                }

                allChatItems.sort((a, b) -> Long.compare(b.lastTime, a.lastTime));

                if (progressIndicator != null) progressIndicator.setVisibility(View.GONE);

                // Apply current search query
                String currentQuery = etSearch != null && etSearch.getText() != null
                    ? etSearch.getText().toString().trim() : "";
                filterChats(currentQuery);

                // Update count label
                updateCountLabel();

                // Animate list items on first load
                animateList();
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (progressIndicator != null) progressIndicator.setVisibility(View.GONE);
            }
        });
    }

    private void updateCountLabel() {
        if (tvCount != null) {
            tvCount.setText("إجمالي المحادثات: " + allChatItems.size());
        }
    }

    private void updateEmptyState(boolean empty) {
        if (layoutEmpty != null) layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ────────────────────────────────────────────────────────────────
    //  Open Chat
    // ────────────────────────────────────────────────────────────────

    private void openChat(AdminChatItem item) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("otherUid",      item.uid1);
        intent.putExtra("otherName",     item.name1 + " ↔ " + item.name2);
        intent.putExtra("adminView",     true);
        intent.putExtra("chatIdOverride", item.chatId);
        startActivity(intent);
    }

    // ────────────────────────────────────────────────────────────────
    //  List Entry Animation
    // ────────────────────────────────────────────────────────────────

    private void animateList() {
        recyclerView.post(() -> {
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                if (child == null) continue;
                child.setAlpha(0f);
                child.setTranslationY(32f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 40L)
                    .setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    // ════════════════════════════════════════════════════════════════
    //  Data Model
    // ════════════════════════════════════════════════════════════════

    static class AdminChatItem {
        String chatId, uid1, uid2, name1, name2, lastMessage;
        long   lastTime;
        int    messageCount;

        AdminChatItem(String chatId, String uid1, String uid2, String name1,
                      String name2, String lastMsg, long lastTime, int count) {
            this.chatId       = chatId;
            this.uid1         = uid1;
            this.uid2         = uid2;
            this.name1        = name1;
            this.name2        = name2;
            this.lastMessage  = lastMsg;
            this.lastTime     = lastTime;
            this.messageCount = count;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ADAPTER
    // ════════════════════════════════════════════════════════════════

    static class AdminChatsAdapter extends RecyclerView.Adapter<AdminChatsAdapter.VH> {

        private final List<AdminChatItem> items;
        private final OnClick             listener;
        interface OnClick { void onClick(AdminChatItem item); }

        AdminChatsAdapter(List<AdminChatItem> items, OnClick l) {
            this.items    = items;
            this.listener = l;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_chat, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AdminChatItem item = items.get(pos);

            h.tvNames.setText(item.name1 + " ↔ " + item.name2);
            h.tvLastMsg.setText(item.lastMessage.isEmpty() ? "لا توجد رسائل" : item.lastMessage);
            h.tvMsgCount.setText(item.messageCount + " رسالة");

            if (item.lastTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", new Locale("ar"));
                h.tvTime.setText(sdf.format(new Date(item.lastTime)));
            } else {
                h.tvTime.setText("");
            }

            h.card.setOnClickListener(v -> listener.onClick(item));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView         tvNames, tvLastMsg, tvTime, tvMsgCount;

            VH(@NonNull View v) {
                super(v);
                card       = v.findViewById(R.id.card_admin_chat);
                tvNames    = v.findViewById(R.id.tv_admin_chat_names);
                tvLastMsg  = v.findViewById(R.id.tv_admin_chat_last_msg);
                tvTime     = v.findViewById(R.id.tv_admin_chat_time);
                tvMsgCount = v.findViewById(R.id.tv_admin_chat_count);
            }
        }
    }
}
