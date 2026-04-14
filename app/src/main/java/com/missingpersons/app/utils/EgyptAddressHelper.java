package com.missingpersons.app.utils;

import java.util.*;

/**
 * EgyptAddressHelper — بيانات العنوان المصري
 * محافظات ← مدن ← مراكز
 */
public class EgyptAddressHelper {

    private static final LinkedHashMap<String, LinkedHashMap<String, String[]>> DATA = new LinkedHashMap<>();

    static {
        // ═══ القاهرة ═══
        LinkedHashMap<String, String[]> cairo = new LinkedHashMap<>();
        cairo.put("مدينة نصر", new String[]{"الحي الأول", "الحي السابع", "الحي العاشر", "المنطقة الأولى"});
        cairo.put("مصر الجديدة", new String[]{"النزهة", "المطرية", "عين شمس", "الأميرية"});
        cairo.put("المعادي", new String[]{"المعادي الجديدة", "دجلة", "زهراء المعادي", "ثكنات المعادي"});
        cairo.put("حلوان", new String[]{"حلوان", "15 مايو", "التبين", "المعصرة"});
        cairo.put("شبرا", new String[]{"شبرا", "الساحل", "روض الفرج", "الشرابية"});
        cairo.put("وسط البلد", new String[]{"عابدين", "الأزبكية", "قصر النيل", "السيدة زينب"});
        cairo.put("التجمع الخامس", new String[]{"التجمع الأول", "التجمع الثالث", "التجمع الخامس", "الرحاب"});
        DATA.put("القاهرة", cairo);

        // ═══ الجيزة ═══
        LinkedHashMap<String, String[]> giza = new LinkedHashMap<>();
        giza.put("الجيزة", new String[]{"الدقي", "العجوزة", "بولاق الدكرور", "الهرم"});
        giza.put("6 أكتوبر", new String[]{"الحي الأول", "الحي الثاني", "الحي العاشر", "الشيخ زايد"});
        giza.put("الشيخ زايد", new String[]{"الحي الأول", "الحي الثاني", "الحي الرابع", "هايبر وان"});
        giza.put("البدرشين", new String[]{"البدرشين", "الحوامدية", "أبو النمرس"});
        giza.put("الصف", new String[]{"الصف", "أطفيح", "العياط"});
        DATA.put("الجيزة", giza);

        // ═══ الإسكندرية ═══
        LinkedHashMap<String, String[]> alex = new LinkedHashMap<>();
        alex.put("وسط الإسكندرية", new String[]{"محطة الرمل", "المنشية", "العطارين", "كرموز"});
        alex.put("شرق الإسكندرية", new String[]{"سيدي بشر", "المندرة", "كليوباترا", "سموحة"});
        alex.put("غرب الإسكندرية", new String[]{"العجمي", "الدخيلة", "المكس", "الأنفوشي"});
        alex.put("العامرية", new String[]{"العامرية الأولى", "العامرية الثانية", "برج العرب"});
        DATA.put("الإسكندرية", alex);

        // ═══ الشرقية ═══
        LinkedHashMap<String, String[]> sharqia = new LinkedHashMap<>();
        sharqia.put("الزقازيق", new String[]{"أول الزقازيق", "ثاني الزقازيق", "ثالث الزقازيق"});
        sharqia.put("العاشر من رمضان", new String[]{"الحي الأول", "الحي الثاني", "المنطقة الصناعية"});
        sharqia.put("بلبيس", new String[]{"بلبيس", "كفر صقر", "الحسينية"});
        sharqia.put("أبو حماد", new String[]{"أبو حماد", "القرين", "ديرب نجم"});
        sharqia.put("فاقوس", new String[]{"فاقوس", "منيا القمح", "أبو كبير"});
        DATA.put("الشرقية", sharqia);

        // ═══ الدقهلية ═══
        LinkedHashMap<String, String[]> daqahlia = new LinkedHashMap<>();
        daqahlia.put("المنصورة", new String[]{"أول المنصورة", "ثاني المنصورة", "ثالث المنصورة"});
        daqahlia.put("طلخا", new String[]{"طلخا", "نبروه", "بني عبيد"});
        daqahlia.put("ميت غمر", new String[]{"ميت غمر", "أجا", "السنبلاوين"});
        daqahlia.put("دكرنس", new String[]{"دكرنس", "المطرية", "بلقاس"});
        DATA.put("الدقهلية", daqahlia);

        // ═══ الغربية ═══
        LinkedHashMap<String, String[]> gharbiya = new LinkedHashMap<>();
        gharbiya.put("طنطا", new String[]{"أول طنطا", "ثاني طنطا", "قطور"});
        gharbiya.put("المحلة الكبرى", new String[]{"أول المحلة", "ثاني المحلة", "بسيون"});
        gharbiya.put("كفر الزيات", new String[]{"كفر الزيات", "السنطة", "سمنود"});
        gharbiya.put("زفتى", new String[]{"زفتى", "شبين الكوم"});
        DATA.put("الغربية", gharbiya);

        // ═══ المنوفية ═══
        LinkedHashMap<String, String[]> monofia = new LinkedHashMap<>();
        monofia.put("شبين الكوم", new String[]{"شبين الكوم", "منوف", "أشمون"});
        monofia.put("مدينة السادات", new String[]{"الحي الأول", "المنطقة الصناعية"});
        monofia.put("قويسنا", new String[]{"قويسنا", "الباجور", "تلا"});
        DATA.put("المنوفية", monofia);

        // ═══ القليوبية ═══
        LinkedHashMap<String, String[]> qaliubia = new LinkedHashMap<>();
        qaliubia.put("بنها", new String[]{"بنها", "كفر شكر", "قليوب"});
        qaliubia.put("شبرا الخيمة", new String[]{"أول شبرا", "ثاني شبرا", "الخصوص"});
        qaliubia.put("العبور", new String[]{"الحي الأول", "الحي الثاني", "الحي الثالث"});
        DATA.put("القليوبية", qaliubia);

        // ═══ البحيرة ═══
        LinkedHashMap<String, String[]> beheira = new LinkedHashMap<>();
        beheira.put("دمنهور", new String[]{"دمنهور", "كوم حمادة", "شبراخيت"});
        beheira.put("كفر الدوار", new String[]{"كفر الدوار", "أبو المطامير", "حوش عيسى"});
        beheira.put("رشيد", new String[]{"رشيد", "إدكو", "المحمودية"});
        DATA.put("البحيرة", beheira);

        // ═══ الفيوم ═══
        LinkedHashMap<String, String[]> fayoum = new LinkedHashMap<>();
        fayoum.put("الفيوم", new String[]{"الفيوم", "سنورس", "إطسا"});
        fayoum.put("طامية", new String[]{"طامية", "يوسف الصديق"});
        DATA.put("الفيوم", fayoum);

        // ═══ بني سويف ═══
        LinkedHashMap<String, String[]> beniSuef = new LinkedHashMap<>();
        beniSuef.put("بني سويف", new String[]{"بني سويف", "ناصر", "إهناسيا"});
        beniSuef.put("الواسطى", new String[]{"الواسطى", "ببا", "الفشن"});
        DATA.put("بني سويف", beniSuef);

        // ═══ المنيا ═══
        LinkedHashMap<String, String[]> minya = new LinkedHashMap<>();
        minya.put("المنيا", new String[]{"المنيا", "ملوي", "سمالوط"});
        minya.put("أبو قرقاص", new String[]{"أبو قرقاص", "بني مزار", "مطاي"});
        DATA.put("المنيا", minya);

        // ═══ أسيوط ═══
        LinkedHashMap<String, String[]> assiut = new LinkedHashMap<>();
        assiut.put("أسيوط", new String[]{"أسيوط", "ديروط", "القوصية"});
        assiut.put("منفلوط", new String[]{"منفلوط", "أبو تيج", "أبنوب"});
        DATA.put("أسيوط", assiut);

        // ═══ سوهاج ═══
        LinkedHashMap<String, String[]> sohag = new LinkedHashMap<>();
        sohag.put("سوهاج", new String[]{"سوهاج", "أخميم", "المنشاة"});
        sohag.put("جرجا", new String[]{"جرجا", "البلينا", "طهطا"});
        DATA.put("سوهاج", sohag);

        // ═══ قنا ═══
        LinkedHashMap<String, String[]> qena = new LinkedHashMap<>();
        qena.put("قنا", new String[]{"قنا", "نجع حمادي", "فرشوط"});
        qena.put("دشنا", new String[]{"دشنا", "قفط", "قوص"});
        DATA.put("قنا", qena);

        // ═══ الأقصر ═══
        LinkedHashMap<String, String[]> luxor = new LinkedHashMap<>();
        luxor.put("الأقصر", new String[]{"الأقصر", "الطود", "أرمنت"});
        DATA.put("الأقصر", luxor);

        // ═══ أسوان ═══
        LinkedHashMap<String, String[]> aswan = new LinkedHashMap<>();
        aswan.put("أسوان", new String[]{"أسوان", "كوم أمبو", "إدفو"});
        aswan.put("دراو", new String[]{"دراو", "نصر النوبة"});
        DATA.put("أسوان", aswan);

        // ═══ الإسماعيلية ═══
        LinkedHashMap<String, String[]> ismailia = new LinkedHashMap<>();
        ismailia.put("الإسماعيلية", new String[]{"الإسماعيلية", "فايد", "القنطرة"});
        ismailia.put("التل الكبير", new String[]{"التل الكبير", "أبو صوير"});
        DATA.put("الإسماعيلية", ismailia);

        // ═══ بورسعيد ═══
        LinkedHashMap<String, String[]> portsaid = new LinkedHashMap<>();
        portsaid.put("بورسعيد", new String[]{"الشرق", "الضواحي", "المناخ", "العرب", "الزهور", "بور فؤاد"});
        DATA.put("بورسعيد", portsaid);

        // ═══ السويس ═══
        LinkedHashMap<String, String[]> suez = new LinkedHashMap<>();
        suez.put("السويس", new String[]{"الأربعين", "فيصل", "عتاقة", "الجناين"});
        DATA.put("السويس", suez);

        // ═══ البحر الأحمر ═══
        LinkedHashMap<String, String[]> redSea = new LinkedHashMap<>();
        redSea.put("الغردقة", new String[]{"الغردقة", "سفاجا", "القصير"});
        redSea.put("مرسى علم", new String[]{"مرسى علم", "الشلاتين"});
        DATA.put("البحر الأحمر", redSea);

        // ═══ شمال سيناء ═══
        LinkedHashMap<String, String[]> nSinai = new LinkedHashMap<>();
        nSinai.put("العريش", new String[]{"العريش", "بئر العبد", "الشيخ زويد"});
        nSinai.put("رفح", new String[]{"رفح", "نخل", "الحسنة"});
        DATA.put("شمال سيناء", nSinai);

        // ═══ جنوب سيناء ═══
        LinkedHashMap<String, String[]> sSinai = new LinkedHashMap<>();
        sSinai.put("شرم الشيخ", new String[]{"شرم الشيخ", "دهب", "نويبع"});
        sSinai.put("الطور", new String[]{"الطور", "رأس سدر", "أبو زنيمة"});
        DATA.put("جنوب سيناء", sSinai);

        // ═══ الوادي الجديد ═══
        LinkedHashMap<String, String[]> newValley = new LinkedHashMap<>();
        newValley.put("الخارجة", new String[]{"الخارجة", "الداخلة", "الفرافرة"});
        DATA.put("الوادي الجديد", newValley);

        // ═══ مطروح ═══
        LinkedHashMap<String, String[]> matrouh = new LinkedHashMap<>();
        matrouh.put("مرسى مطروح", new String[]{"مرسى مطروح", "الحمام", "العلمين"});
        matrouh.put("سيوة", new String[]{"سيوة", "الضبعة"});
        DATA.put("مطروح", matrouh);

        // ═══ كفر الشيخ ═══
        LinkedHashMap<String, String[]> kafrSheikh = new LinkedHashMap<>();
        kafrSheikh.put("كفر الشيخ", new String[]{"كفر الشيخ", "دسوق", "فوه"});
        kafrSheikh.put("بيلا", new String[]{"بيلا", "الحامول", "مطوبس"});
        DATA.put("كفر الشيخ", kafrSheikh);

        // ═══ دمياط ═══
        LinkedHashMap<String, String[]> damietta = new LinkedHashMap<>();
        damietta.put("دمياط", new String[]{"دمياط", "رأس البر", "فارسكور"});
        damietta.put("دمياط الجديدة", new String[]{"دمياط الجديدة", "كفر سعد"});
        DATA.put("دمياط", damietta);
    }

    /** قائمة المحافظات */
    public static String[] getGovernorates() {
        return DATA.keySet().toArray(new String[0]);
    }

    /** مدن محافظة */
    public static String[] getCities(String governorate) {
        LinkedHashMap<String, String[]> cities = DATA.get(governorate);
        return cities != null ? cities.keySet().toArray(new String[0]) : new String[0];
    }

    /** مراكز مدينة */
    public static String[] getDistricts(String governorate, String city) {
        LinkedHashMap<String, String[]> cities = DATA.get(governorate);
        if (cities == null) return new String[0];
        String[] districts = cities.get(city);
        return districts != null ? districts : new String[0];
    }

    /** بناء العنوان الكامل */
    public static String buildAddress(String governorate, String city, String district, String detail) {
        StringBuilder sb = new StringBuilder();
        if (detail != null && !detail.isEmpty()) sb.append(detail).append("، ");
        if (district != null && !district.isEmpty()) sb.append(district).append("، ");
        if (city != null && !city.isEmpty()) sb.append(city).append("، ");
        if (governorate != null && !governorate.isEmpty()) sb.append(governorate);
        return sb.toString().replaceAll("،\\s*$", "");
    }
}
