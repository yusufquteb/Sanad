package com.missingpersons.app.activities;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.missingpersons.app.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter خفيف لعرض آخر 5 بلاغات في Dashboard
 */
public class AdminDashboardReportAdapter
        extends RecyclerView.Adapter<AdminDashboardReportAdapter.ReportVH> {

    private final Context context;
    private List<Map<String, Object>> reports;
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("dd/MM  HH:mm", new Locale("ar"));

    public AdminDashboardReportAdapter(Context context, List<Map<String, Object>> reports) {
        this.context = context;
        this.reports = reports;
    }

    public void setReports(List<Map<String, Object>> newReports) {
        this.reports = newReports;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_admin_report_dash, parent, false);
        return new ReportVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportVH h, int pos) {
        Map<String, Object> item = reports.get(pos);

        String name   = safe(item, "personName", "مجهول");
        String status = safe(item, "status", "pending");
        String gov    = safe(item, "governorate", "");
        String id     = safe(item, "reportId", "");
        Object ts     = item.get("timestamp");

        h.tvName.setText(name);
        h.tvStatus.setText(statusAr(status));
        h.tvGov.setText(gov.isEmpty() ? "غير محدد" : gov);

        if (ts instanceof Long) {
            h.tvTime.setText(sdf.format(new Date((Long) ts)));
        } else {
            h.tvTime.setText("");
        }

        // status dot color
        h.viewDot.setBackgroundColor(statusColor(status));

        // ripple animation on bind
        h.itemView.setAlpha(0f);
        h.itemView.animate().alpha(1f).setDuration(300)
                .setStartDelay(pos * 50L).start();

        // open button
        final String reportId = id;
        h.btnOpen.setOnClickListener(v -> {
            if (!reportId.isEmpty()) {
                Intent i = new Intent(context, CaseDetailActivity.class);
                i.putExtra("reportId", reportId);
                context.startActivity(i);
            }
        });

        // scale animation on card click
        h.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                    .start();
            if (!reportId.isEmpty()) {
                Intent i = new Intent(context, CaseDetailActivity.class);
                i.putExtra("reportId", reportId);
                context.startActivity(i);
            }
        });
    }

    @Override
    public int getItemCount() {
        return reports == null ? 0 : reports.size();
    }

    // ─── ViewHolder ─────────────────────────────────────────
    static class ReportVH extends RecyclerView.ViewHolder {
        View viewDot;
        TextView tvName, tvStatus, tvGov, tvTime;
        MaterialButton btnOpen;

        ReportVH(@NonNull View itemView) {
            super(itemView);
            viewDot  = itemView.findViewById(R.id.view_status_dot);
            tvName   = itemView.findViewById(R.id.tv_dash_report_name);
            tvStatus = itemView.findViewById(R.id.tv_dash_report_status);
            tvGov    = itemView.findViewById(R.id.tv_dash_report_gov);
            tvTime   = itemView.findViewById(R.id.tv_dash_report_time);
            btnOpen  = itemView.findViewById(R.id.btn_dash_open);
        }
    }

    // ─── Helpers ────────────────────────────────────────────
    private String safe(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v != null ? v.toString() : def;
    }

    private int statusColor(String s) {
        if (s == null) return 0xFF9E9E9E;
        switch (s) {
            case "pending":      return 0xFFE65100;
            case "approved":     return 0xFF2E7D32;
            case "resolved":     return 0xFF1565C0;
            case "under_review": return 0xFFF57F17;
            default:             return 0xFF9E9E9E;
        }
    }

    private String statusAr(String s) {
        if (s == null) return "؟";
        switch (s) {
            case "pending":      return "معلق";
            case "under_review": return "قيد المراجعة";
            case "approved":     return "معتمد";
            case "resolved":     return "محلول";
            case "deleted":      return "محذوف";
            default:             return s;
        }
    }
}
