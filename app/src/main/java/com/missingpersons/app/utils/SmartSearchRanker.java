package com.missingpersons.app.utils;

import android.location.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * SmartSearchRanker — ترتيب ذكي لنتائج البحث
 *
 * [مرحلة 3.1] Smart Score للنتائج:
 *   score = (حداثة × 0.40) + (قرب الموقع × 0.30) + (اكتمال البيانات × 0.20) + (حالة البلاغ × 0.10)
 *
 * الاستخدام:
 *   List<HashMap<String,Object>> sorted = SmartSearchRanker.rank(results, userLat, userLng, query);
 */
public class SmartSearchRanker {

    // أوزان العوامل
    private static final float WEIGHT_RECENCY     = 0.40f;
    private static final float WEIGHT_PROXIMITY   = 0.30f;
    private static final float WEIGHT_COMPLETENESS = 0.20f;
    private static final float WEIGHT_STATUS      = 0.10f;

    // عمر البلاغ (ميلي ثانية)
    private static final long FRESH_THRESHOLD  = 7L  * 24 * 60 * 60 * 1000;   // 7 أيام — طازج
    private static final long RECENT_THRESHOLD = 30L * 24 * 60 * 60 * 1000;   // 30 يوم — حديث
    private static final long OLD_THRESHOLD    = 90L * 24 * 60 * 60 * 1000;   // 90 يوم — قديم

    // مسافة القرب (كم)
    private static final float VERY_CLOSE_KM = 10f;
    private static final float CLOSE_KM      = 50f;
    private static final float MEDIUM_KM     = 150f;

    /**
     * رتّب قائمة النتائج حسب Smart Score
     *
     * @param reports     قائمة البلاغات (HashMap من Firebase)
     * @param userLat     خط عرض المستخدم (0 = غير معروف)
     * @param userLng     خط طول المستخدم (0 = غير معروف)
     * @param query       نص البحث (اختياري — لحساب text relevance)
     * @return قائمة مرتبة تنازلياً
     */
    public static List<HashMap<String, Object>> rank(
            List<HashMap<String, Object>> reports,
            double userLat, double userLng,
            String query) {

        List<ScoredReport> scored = new ArrayList<>();

        for (HashMap<String, Object> report : reports) {
            float score = computeScore(report, userLat, userLng, query);
            scored.add(new ScoredReport(report, score));
        }

        // ترتيب تنازلي
        Collections.sort(scored, (a, b) -> Float.compare(b.score, a.score));

        List<HashMap<String, Object>> result = new ArrayList<>();
        for (ScoredReport s : scored) {
            s.report.put("_smartScore", s.score); // حفظ الـ score للعرض
            result.add(s.report);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════════

    private static float computeScore(HashMap<String, Object> report,
                                       double userLat, double userLng,
                                       String query) {
        float score = 0;

        // 1. حداثة البلاغ (0–1)
        score += WEIGHT_RECENCY * recencyScore(report);

        // 2. قرب الموقع (0–1)
        if (userLat != 0 && userLng != 0) {
            score += WEIGHT_PROXIMITY * proximityScore(report, userLat, userLng);
        } else {
            // إذا لا موقع متاح — وزّع الـ weight على الحداثة
            score += WEIGHT_PROXIMITY * recencyScore(report) * 0.5f;
        }

        // 3. اكتمال البيانات (0–1)
        score += WEIGHT_COMPLETENESS * completenessScore(report);

        // 4. حالة البلاغ (0–1)
        score += WEIGHT_STATUS * statusScore(report);

        // 5. Text relevance bonus (0–0.15) — إضافي
        if (query != null && !query.isEmpty()) {
            score += 0.15f * textRelevanceScore(report, query);
        }

        return Math.min(1.0f, score);
    }

    /** درجة الحداثة — كلما كان البلاغ أحدث كلما ارتفعت الدرجة */
    private static float recencyScore(HashMap<String, Object> report) {
        Object tsObj = report.get("timestamp");
        if (tsObj == null) tsObj = report.get("createdAt");
        if (tsObj == null) return 0.3f; // افتراضي

        long ts  = toLong(tsObj);
        long age = System.currentTimeMillis() - ts;

        if (age <= FRESH_THRESHOLD)  return 1.0f;
        if (age <= RECENT_THRESHOLD) return 0.75f;
        if (age <= OLD_THRESHOLD)    return 0.50f;
        return 0.20f;
    }

    /** درجة القرب الجغرافي */
    private static float proximityScore(HashMap<String, Object> report,
                                          double userLat, double userLng) {
        // محاولة قراءة إحداثيات البلاغ
        Object latObj = report.get("latitude");
        Object lngObj = report.get("longitude");

        if (latObj == null || lngObj == null) {
            // لا إحداثيات — نستخدم اسم المحافظة للتطابق التقريبي
            return governorateProximityScore(report, userLat, userLng);
        }

        double reportLat = toDouble(latObj);
        double reportLng = toDouble(lngObj);

        float[] results = new float[1];
        Location.distanceBetween(userLat, userLng, reportLat, reportLng, results);
        float distKm = results[0] / 1000f;

        if (distKm <= VERY_CLOSE_KM) return 1.0f;
        if (distKm <= CLOSE_KM)      return 0.80f;
        if (distKm <= MEDIUM_KM)     return 0.50f;
        return 0.20f;
    }

    /** تقدير القرب من اسم المحافظة (fallback) */
    private static float governorateProximityScore(HashMap<String, Object> report,
                                                     double userLat, double userLng) {
        // إذا لا يمكن تحديد المسافة نعيد 0.5 (متوسط)
        return 0.5f;
    }

    /** درجة اكتمال البيانات */
    private static float completenessScore(HashMap<String, Object> report) {
        int fields = 0, filled = 0;

        String[] required = {
            "personName", "personAge", "gender", "location",
            "description", "photoUrl", "contactPhone"
        };

        for (String f : required) {
            fields++;
            Object val = report.get(f);
            if (val != null && !val.toString().trim().isEmpty()) filled++;
        }

        // Face embedding = bonus
        if (report.get("faceEmbedding") != null) filled++;
        fields++;

        return (float) filled / fields;
    }

    /** درجة حالة البلاغ */
    private static float statusScore(HashMap<String, Object> report) {
        String status = (String) report.get("status");
        if (status == null) return 0.5f;

        switch (status) {
            case "approved": return 1.0f;
            case "pending":  return 0.6f;
            case "matched":  return 0.3f;  // لا يزال مفتوح مع تطابق
            case "resolved": return 0.0f;  // محلول — يظهر آخر القائمة
            default:         return 0.3f;
        }
    }

    /** درجة التطابق النصي مع استعلام البحث */
    private static float textRelevanceScore(HashMap<String, Object> report, String query) {
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return 0;

        float score = 0;

        // اسم الشخص — أعلى وزن
        String name = getStr(report, "personName");
        if (name.toLowerCase().contains(q)) score += 0.6f;

        // الموقع
        String loc = getStr(report, "location");
        if (loc.toLowerCase().contains(q)) score += 0.25f;

        // الوصف
        String desc = getStr(report, "description");
        if (desc.toLowerCase().contains(q)) score += 0.15f;

        return Math.min(1.0f, score);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private static long toLong(Object obj) {
        if (obj instanceof Long)    return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Double)  return ((Double) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return 0; }
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Double)  return (Double) obj;
        if (obj instanceof Float)   return ((Float) obj).doubleValue();
        if (obj instanceof Long)    return ((Long) obj).doubleValue();
        if (obj instanceof Integer) return ((Integer) obj).doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0; }
    }

    private static String getStr(HashMap<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    // ════════════════════════════════════════════════════════════════════

    private static class ScoredReport {
        final HashMap<String, Object> report;
        final float score;
        ScoredReport(HashMap<String, Object> report, float score) {
            this.report = report;
            this.score  = score;
        }
    }
}
