package com.missingpersons.app.activities;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.HumanReviewQueueManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter لعرض عناصر طابور المراجعة البشرية.
 */
public class ReviewQueueAdapter
        extends RecyclerView.Adapter<ReviewQueueAdapter.ViewHolder> {

    public interface ActionListener {
        void onApprove(HumanReviewQueueManager.ReviewItem item, int position);
        void onReject(HumanReviewQueueManager.ReviewItem item, int position);
    }

    private final Context context;
    private final List<HumanReviewQueueManager.ReviewItem> items;
    private final ActionListener listener;
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("dd/MM  HH:mm", new Locale("ar"));

    public ReviewQueueAdapter(
            Context context,
            List<HumanReviewQueueManager.ReviewItem> items,
            ActionListener listener) {
        this.context  = context;
        this.items    = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_review_queue, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        HumanReviewQueueManager.ReviewItem item = items.get(position);

        // Person name
        String name = (item.personName != null && !item.personName.isEmpty())
                ? item.personName : "غير معروف";
        h.tvName.setText(name);

        // IDs line
        StringBuilder ids = new StringBuilder();
        if (item.matchId != null && !item.matchId.isEmpty()) {
            ids.append("تطابق: ").append(item.matchId, 0,
                    Math.min(item.matchId.length(), 10)).append("…");
        } else if (item.reportId != null && !item.reportId.isEmpty()) {
            ids.append("بلاغ: ").append(item.reportId, 0,
                    Math.min(item.reportId.length(), 10)).append("…");
        } else if (item.sightingId != null && !item.sightingId.isEmpty()) {
            ids.append("رؤية: ").append(item.sightingId, 0,
                    Math.min(item.sightingId.length(), 10)).append("…");
        } else {
            ids.append("معرف: —");
        }
        h.tvIds.setText(ids.toString());

        // Similarity badge
        int pct = item.getPercent();
        h.tvSimilarity.setText(pct + "%");
        h.tvSimilarity.setBackgroundColor(similarityColor(pct));

        // Type chip
        String typeLabel = typeLabel(item.type);
        h.chipType.setText(typeLabel);
        h.chipType.setChipBackgroundColorResource(typeChipColor(item.type));

        // Timestamp
        if (item.timestamp > 0) {
            h.tvTimestamp.setText(sdf.format(new Date(item.timestamp)));
        } else {
            h.tvTimestamp.setText("");
        }

        // Fade-in animation
        h.itemView.setAlpha(0f);
        h.itemView.animate().alpha(1f).setDuration(250)
                .setStartDelay(position * 40L).start();

        // Button listeners (use getAdapterPosition() to handle removals safely)
        h.btnApprove.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null)
                listener.onApprove(item, pos);
        });

        h.btnReject.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null)
                listener.onReject(item, pos);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    // ── ViewHolder ─────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView       tvName, tvIds, tvSimilarity, tvTimestamp;
        Chip           chipType;
        MaterialButton btnApprove, btnReject;

        ViewHolder(@NonNull View v) {
            super(v);
            tvName       = v.findViewById(R.id.tv_review_person_name);
            tvIds        = v.findViewById(R.id.tv_review_ids);
            tvSimilarity = v.findViewById(R.id.tv_review_similarity);
            tvTimestamp  = v.findViewById(R.id.tv_review_timestamp);
            chipType     = v.findViewById(R.id.chip_review_type);
            btnApprove   = v.findViewById(R.id.btn_review_approve);
            btnReject    = v.findViewById(R.id.btn_review_reject);
        }
    }

    // ── Helpers ────────────────────────────────────────────────

    /**
     * Returns a color int for the similarity badge:
     *   >= 80% → success_color (green)
     *   60-79% → color_warning (orange)
     *    < 60% → md_error     (red)
     */
    private int similarityColor(int pct) {
        if (pct >= 80) return context.getResources().getColor(R.color.success_color, null);
        if (pct >= 60) return context.getResources().getColor(R.color.color_warning, null);
        return context.getResources().getColor(R.color.md_error, null);
    }

    private String typeLabel(String type) {
        if (type == null) return "غير محدد";
        switch (type) {
            case "sighting_match": return "تطابق رؤية";
            case "report_match":   return "تطابق بلاغ";
            case "sighting":       return "رؤية";
            case "report":         return "بلاغ";
            default:               return type;
        }
    }

    private int typeChipColor(String type) {
        if ("sighting_match".equals(type) || "sighting".equals(type))
            return R.color.status_sighting_container;
        return R.color.status_missing_container;
    }
}
