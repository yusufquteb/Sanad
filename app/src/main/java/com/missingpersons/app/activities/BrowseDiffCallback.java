package com.missingpersons.app.activities;

import androidx.recyclerview.widget.DiffUtil;
import com.missingpersons.app.models.ReportEntity;
import java.util.List;
import java.util.Objects;

/**
 * BrowseDiffCallback — DiffUtil لقائمة البلاغات
 *
 * areItemsTheSame:    مقارنة reportId (المفتاح الفريد)
 * areContentsTheSame: مقارنة الحقول المعروضة فقط
 */
public class BrowseDiffCallback extends DiffUtil.Callback {

    private final List<ReportEntity> oldList;
    private final List<ReportEntity> newList;

    public BrowseDiffCallback(List<ReportEntity> oldList, List<ReportEntity> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() { return oldList.size(); }

    @Override
    public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        return oldList.get(oldPos).reportId.equals(newList.get(newPos).reportId);
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        ReportEntity o = oldList.get(oldPos);
        ReportEntity n = newList.get(newPos);
        return Objects.equals(o.status, n.status)
            && Objects.equals(o.imageUrl, n.imageUrl)
            && Objects.equals(o.personName, n.personName)
            && o.lastUpdated == n.lastUpdated;
    }
}
