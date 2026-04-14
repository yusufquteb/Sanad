package com.missingpersons.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.*;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.missingpersons.app.R;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatsListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    private String myUid;
    private DatabaseReference chatsRef, usersRef;
    private final List<ChatItem> chatItems = new ArrayList<>();
    private ChatsAdapter adapter;

    @Override
    protected void attachBaseContext(android.content.Context c) {
        super.attachBaseContext(com.missingpersons.app.utils.LanguageHelper.applyLanguage(c));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats_list);
        // ── Edge-to-Edge: يمنع تداخل المحتوى مع Navigation Bar ──
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int navBot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                         v.getPaddingRight(), navBot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("💬 محادثاتي");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        if (myUid.isEmpty()) { finish(); return; }

        recyclerView = findViewById(R.id.rv_chats);
        progressBar  = findViewById(R.id.progress_chats);
        tvEmpty      = findViewById(R.id.tv_empty_chats);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatsAdapter(chatItems, this::openChat);
        recyclerView.setAdapter(adapter);

        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        loadChats();
    }

    private void loadChats() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        chatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatItems.clear();
                int pending = 0;

                for (DataSnapshot chatSnap : snapshot.getChildren()) {
                    String chatId = chatSnap.getKey();
                    if (chatId == null || !chatId.contains(myUid)) continue;

                    // استخراج UID الطرف الآخر
                    String[] uids = chatId.split("_");
                    if (uids.length != 2) continue;
                    String otherUid = uids[0].equals(myUid) ? uids[1] : uids[0];

                    // آخر رسالة
                    String lastMsg = "";
                    long lastTime = 0;
                    int unread = 0;

                    for (DataSnapshot msgSnap : chatSnap.getChildren()) {
                        String text = msgSnap.child("text").getValue(String.class);
                        Long ts = msgSnap.child("timestamp").getValue(Long.class);
                        String sender = msgSnap.child("senderId").getValue(String.class);
                        Boolean read = msgSnap.child("read").getValue(Boolean.class);

                        if (ts != null && ts > lastTime) {
                            lastTime = ts;
                            lastMsg = text != null ? text : "";
                        }
                        if (!myUid.equals(sender) && !Boolean.TRUE.equals(read)) {
                            unread++;
                        }
                    }

                    ChatItem item = new ChatItem(chatId, otherUid, "", lastMsg, lastTime, unread);
                    chatItems.add(item);
                    pending++;
                }

                // ترتيب حسب آخر رسالة
                chatItems.sort((a, b) -> Long.compare(b.lastTime, a.lastTime));

                if (chatItems.isEmpty()) {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }

                // جلب أسماء المستخدمين
                loadUserNames();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(ChatsListActivity.this,
                    "خطأ في تحميل المحادثات", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserNames() {
        final int[] remaining = {chatItems.size()};

        for (ChatItem item : chatItems) {
            usersRef.child(item.otherUid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        String name = snap.getValue(String.class);
                        item.otherName = name != null ? name : "مستخدم";
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            adapter.notifyDataSetChanged();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
        }
    }

    private void openChat(ChatItem item) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("otherUid", item.otherUid);
        intent.putExtra("otherName", item.otherName);
        startActivity(intent);
    }

    // ═══════════════════════════════════════
    //  MODEL
    // ═══════════════════════════════════════
    static class ChatItem {
        String chatId, otherUid, otherName, lastMessage;
        long lastTime;
        int unreadCount;

        ChatItem(String chatId, String otherUid, String otherName,
                 String lastMessage, long lastTime, int unread) {
            this.chatId = chatId;
            this.otherUid = otherUid;
            this.otherName = otherName;
            this.lastMessage = lastMessage;
            this.lastTime = lastTime;
            this.unreadCount = unread;
        }
    }

    // ═══════════════════════════════════════
    //  ADAPTER
    // ═══════════════════════════════════════
    static class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.VH> {
        private final List<ChatItem> items;
        private final OnChatClick listener;
        interface OnChatClick { void onClick(ChatItem item); }

        ChatsAdapter(List<ChatItem> items, OnChatClick l) {
            this.items = items; this.listener = l;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_preview, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ChatItem item = items.get(pos);
            h.tvName.setText(item.otherName);
            h.tvLastMsg.setText(item.lastMessage);

            if (item.lastTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm",
                    new Locale("ar"));
                h.tvTime.setText(sdf.format(new Date(item.lastTime)));
            }

            if (item.unreadCount > 0) {
                h.tvBadge.setVisibility(View.VISIBLE);
                h.tvBadge.setText(String.valueOf(item.unreadCount));
            } else {
                h.tvBadge.setVisibility(View.GONE);
            }

            h.card.setOnClickListener(v -> listener.onClick(item));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView tvName, tvLastMsg, tvTime, tvBadge;
            VH(@NonNull View v) {
                super(v);
                card      = v.findViewById(R.id.card_chat);
                tvName    = v.findViewById(R.id.tv_chat_name);
                tvLastMsg = v.findViewById(R.id.tv_chat_last_msg);
                tvTime    = v.findViewById(R.id.tv_chat_time);
                tvBadge   = v.findViewById(R.id.tv_chat_badge);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
