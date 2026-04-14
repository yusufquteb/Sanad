package com.missingpersons.app.utils;

import java.util.*;

/**
 * مدير Pagination - يحل مشكلة تكرار العناصر عند السحب للأسفل
 * ✅ تتبع العناصر المحملة سابقاً
 * ✅ منع التكرار عند جلب البيانات الجديدة
 * ✅ إدارة رقم الصفحة تلقائياً
 */
/**
 * @deprecated كود ميت — لا يُستخدم في أي مكان.
 * يمكن حذف هذا الملف بعد التأكد من عدم الحاجة إليه.
 * آخر مراجعة: 2026-04
 */
@Deprecated
public class PaginationManager {

    private int currentPage = 0;
    private int pageSize = 10;
    private Set<String> loadedIds = new HashSet<>();
    private boolean isLoading = false;
    private boolean hasMoreData = true;

    public PaginationManager(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * بدء الـ loading
     */
    public void startLoading() {
        isLoading = true;
    }

    /**
     * إنهاء الـ loading
     */
    public void finishLoading() {
        isLoading = false;
    }

    /**
     * التحقق من الـ loading
     */
    public boolean isLoading() {
        return isLoading;
    }

    /**
     * التحقق من وجود بيانات أكثر
     */
    public boolean hasMore() {
        return hasMoreData;
    }

    /**
     * تحديد انتهاء البيانات
     */
    public void setNoMoreData(boolean noMore) {
        this.hasMoreData = !noMore;
    }

    /**
     * إضافة معرف العنصر للتتبع
     */
    public boolean addIfNotLoaded(String id) {
        if (id == null) return false;
        if (loadedIds.contains(id)) {
            return false; // العنصر تم تحميله بالفعل
        }
        loadedIds.add(id);
        return true; // العنصر جديد
    }

    /**
     * الحصول على الصفحة الحالية
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * الانتقال للصفحة التالية
     */
    public void nextPage() {
        currentPage++;
    }

    /**
     * إعادة تعيين الـ pagination
     */
    public void reset() {
        currentPage = 0;
        loadedIds.clear();
        hasMoreData = true;
        isLoading = false;
    }

    /**
     * الحصول على عدد العناصر المحملة
     */
    public int getLoadedCount() {
        return loadedIds.size();
    }

    /**
     * الحصول على حجم الصفحة
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * التحقق من أن معرف معين مُحمّل بالفعل
     */
    public boolean isLoaded(String id) {
        return loadedIds.contains(id);
    }
}
