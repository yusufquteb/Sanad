package com.missingpersons.app.utils;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * EgyptAddressHelper — تحميل المحافظات والمدن والأحياء من asset
 * ملف: assets/egypt_addresses.json
 * يتيح للإدارة تعديل القائمة لاحقاً بدون تعديل الكود
 */
public class EgyptAddressHelper {

    private static final String TAG    = "EgyptAddressHelper";
    private static final String ASSET  = "egypt_addresses.json";

    // Cache محلي
    private static List<String>              cachedGovernorates;
    private static Map<String, List<String>> cachedCities = new HashMap<>();
    private static Map<String, List<String>> cachedAreas  = new HashMap<>();
    private static JSONArray                 rootArray;

    /** تحميل الـ JSON مرة واحدة فقط */
    private static void ensureLoaded(Context ctx) {
        if (rootArray != null) return;
        try {
            InputStream is   = ctx.getAssets().open(ASSET);
            byte[] buf       = new byte[is.available()];
            is.read(buf); is.close();
            JSONObject json  = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            rootArray        = json.getJSONArray("governorates");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load " + ASSET + ": " + e.getMessage());
            rootArray = new JSONArray();
        }
    }

    /** قائمة المحافظات */
    public static List<String> getGovernorates(Context ctx) {
        ensureLoaded(ctx);
        if (cachedGovernorates != null) return cachedGovernorates;
        cachedGovernorates = new ArrayList<>();
        try {
            for (int i = 0; i < rootArray.length(); i++)
                cachedGovernorates.add(rootArray.getJSONObject(i).getString("name"));
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        return cachedGovernorates;
    }

    /** قائمة المحافظات بدون context (للتوافق مع الكود القديم) */
    public static List<String> getGovernorates() {
        if (cachedGovernorates != null) return cachedGovernorates;
        // Fallback hardcoded
        return Arrays.asList("القاهرة","الجيزة","الإسكندرية","الشرقية","الدقهلية",
            "الغربية","المنوفية","القليوبية","البحيرة","كفر الشيخ","دمياط",
            "بورسعيد","الإسماعيلية","السويس","شمال سيناء","جنوب سيناء",
            "مطروح","الوادي الجديد","البحر الأحمر","أسيوط","سوهاج","قنا",
            "الأقصر","أسوان","المنيا","بني سويف","الفيوم");
    }

    /** مدن/أقسام المحافظة */
    public static List<String> getCities(Context ctx, String governorate) {
        ensureLoaded(ctx);
        String key = governorate != null ? governorate : "";
        if (cachedCities.containsKey(key)) return cachedCities.get(key);
        List<String> result = new ArrayList<>();
        try {
            for (int i = 0; i < rootArray.length(); i++) {
                JSONObject gov = rootArray.getJSONObject(i);
                if (key.equals(gov.getString("name"))) {
                    JSONArray cities = gov.getJSONArray("cities");
                    for (int j = 0; j < cities.length(); j++)
                        result.add(cities.getJSONObject(j).getString("name"));
                    break;
                }
            }
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        cachedCities.put(key, result);
        return result;
    }

    /** أحياء/قرى المدينة */
    public static List<String> getAreas(Context ctx, String governorate, String city) {
        ensureLoaded(ctx);
        String key = (governorate != null ? governorate : "") + "|" + (city != null ? city : "");
        if (cachedAreas.containsKey(key)) return cachedAreas.get(key);
        List<String> result = new ArrayList<>();
        try {
            for (int i = 0; i < rootArray.length(); i++) {
                JSONObject gov = rootArray.getJSONObject(i);
                if ((governorate != null ? governorate : "").equals(gov.getString("name"))) {
                    JSONArray cities = gov.getJSONArray("cities");
                    for (int j = 0; j < cities.length(); j++) {
                        JSONObject c = cities.getJSONObject(j);
                        if ((city != null ? city : "").equals(c.getString("name"))) {
                            JSONArray areas = c.getJSONArray("areas");
                            for (int k = 0; k < areas.length(); k++)
                                result.add(areas.getString(k));
                            break;
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        cachedAreas.put(key, result);
        return result;
    }

    /** Legacy: للتوافق مع الكود القديم getCities(gov) بدون context */
    public static List<String> getCities(String governorate) {
        return new ArrayList<>(); // empty without context
    }

    /** alias للتوافق — getAreas بـ context */
    public static List<String> getDistricts(String governorate, String city) {
        return new ArrayList<>(); // empty without context — use getAreas(ctx,gov,city)
    }

    /** alias للتوافق — getDistricts بـ context */
    public static List<String> getDistricts(Context ctx, String governorate, String city) {
        return getAreas(ctx, governorate, city);
    }

    /** بناء عنوان نصي من المكونات */
    public static String buildAddress(String gov, String city, String area, String landmark) {
        StringBuilder sb = new StringBuilder();
        if (gov      != null && !gov.isEmpty())      sb.append(gov);
        if (city     != null && !city.isEmpty())     { if (sb.length()>0) sb.append(" - "); sb.append(city); }
        if (area     != null && !area.isEmpty())     { if (sb.length()>0) sb.append(" - "); sb.append(area); }
        if (landmark != null && !landmark.isEmpty()) { if (sb.length()>0) sb.append("، "); sb.append(landmark); }
        return sb.toString();
    }
}
