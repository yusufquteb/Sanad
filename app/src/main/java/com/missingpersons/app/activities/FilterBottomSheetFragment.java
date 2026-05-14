package com.missingpersons.app.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.missingpersons.app.R;

import java.util.Arrays;
import java.util.List;

/**
 * FilterBottomSheetFragment — فلتر متطور
 *
 * [إصلاح] استبدال ChipGroup بـ AutoCompleteTextView للمحافظات
 * السبب: الـ Chips تزدحم مع 28 محافظة — AutoComplete أسرع وأوضح
 */
public class FilterBottomSheetFragment extends BottomSheetDialogFragment {

    private OnFiltersAppliedListener listener;
    private AutoCompleteTextView actvGovernorate;
    private RadioGroup radioGroupSort;

    private String selectedGovernorate = "الكل";
    private String currentSortArg = "newest";

    // قائمة المحافظات المصرية
    private final List<String> governorates = Arrays.asList(
        "الكل", "القاهرة", "الإسكندرية", "الجيزة", "القليوبية", "الشرقية",
        "الدقهلية", "البحيرة", "المنوفية", "الغربية", "كفر الشيخ", "دمياط",
        "بورسعيد", "الإسماعيلية", "السويس", "شمال سيناء", "جنوب سيناء",
        "مطروح", "الوادي الجديد", "أسيوط", "سوهاج", "قنا", "الأقصر", "أسوان",
        "المنيا", "بني سويف", "الفيوم", "البحر الأحمر"
    );

    public interface OnFiltersAppliedListener {
        void onFiltersApplied(String governorate, String sortOrder);
        void onFiltersReset();
    }

    public static FilterBottomSheetFragment newInstance() {
        return new FilterBottomSheetFragment();
    }

    /** [إصلاح] إنشاء الـ Fragment مع الحالة الحالية للفلتر */
    public static FilterBottomSheetFragment newInstance(String currentGov, String currentSort) {
        FilterBottomSheetFragment f = new FilterBottomSheetFragment();
        android.os.Bundle args = new android.os.Bundle();
        args.putString("current_gov",  currentGov  != null ? currentGov  : "الكل");
        args.putString("current_sort", currentSort != null ? currentSort : "newest");
        f.setArguments(args);
        return f;
    }

    public void setOnFiltersAppliedListener(OnFiltersAppliedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_filters, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // [إصلاح] استعادة حالة الفلتر الحالية
        android.os.Bundle args = getArguments();
        if (args != null) {
            selectedGovernorate = args.getString("current_gov", "الكل");
            currentSortArg      = args.getString("current_sort", "newest");
        }

        radioGroupSort  = view.findViewById(R.id.radio_group_sort);
        MaterialButton btnApply = view.findViewById(R.id.btn_apply_filters);
        MaterialButton btnReset = view.findViewById(R.id.btn_reset_filters);

        // تطبيق ترتيب السابق على RadioGroup
        if (radioGroupSort != null && currentSortArg != null) {
            if ("smart".equals(currentSortArg))
                radioGroupSort.check(R.id.radio_smart);
            else if ("nearest".equals(currentSortArg))
                radioGroupSort.check(R.id.radio_sort_nearest);
            else
                radioGroupSort.check(R.id.radio_newest);
        }

        setupGovernorateAutocomplete(view);

        btnApply.setOnClickListener(v -> {
            applyFilters();
            dismiss();
        });

        btnReset.setOnClickListener(v -> {
            selectedGovernorate = "الكل";
            if (actvGovernorate != null) actvGovernorate.setText("الكل", false);
            if (listener != null) listener.onFiltersReset();
            dismiss();
        });
    }

    /**
     * [إصلاح] إعداد AutoCompleteTextView بدلاً من ChipGroup
     * يدعم البحث بالكتابة + اختيار من القائمة
     */
    private void setupGovernorateAutocomplete(View root) {
        // نحاول نجد الـ AutoCompleteTextView — إن لم يوجد layout_gov_autocomplete
        // نرجع للـ chip_group_governorate كـ fallback
        View actvView = root.findViewById(R.id.actv_governorate);
        if (actvView instanceof AutoCompleteTextView) {
            actvGovernorate = (AutoCompleteTextView) actvView;
        } else {
            // fallback: إخفاء الـ ChipGroup وإنشاء AutoComplete برمجياً
            View chipGroup = root.findViewById(R.id.chip_group_governorate);
            if (chipGroup != null) {
                ViewGroup parent = (ViewGroup) chipGroup.getParent();
                if (parent != null) {
                    int idx = parent.indexOfChild(chipGroup);
                    chipGroup.setVisibility(View.GONE);

                    AutoCompleteTextView actv = new AutoCompleteTextView(requireContext());
                    actv.setHint("اختر المحافظة أو اكتب للبحث...");
                    actv.setThreshold(1);
                    ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                    actv.setLayoutParams(lp);
                    actv.setPadding(32, 24, 32, 24);
                    actv.setTextSize(14f);
                    parent.addView(actv, idx);
                    actvGovernorate = actv;
                }
            }
        }

        if (actvGovernorate != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                governorates);
            actvGovernorate.setAdapter(adapter);
            actvGovernorate.setText(selectedGovernorate, false);
            actvGovernorate.setOnItemClickListener((parent, v2, pos, id) ->
                selectedGovernorate = governorates.get(pos));
        }
    }

    private void applyFilters() {
        if (listener == null) return;
        String gov = (selectedGovernorate == null || selectedGovernorate.equals("الكل"))
            ? "الكل" : selectedGovernorate;

        int sortId = (radioGroupSort != null) ? radioGroupSort.getCheckedRadioButtonId() : -1;
        String sortOrder = "newest";
        if (sortId == R.id.radio_smart)        sortOrder = "smart";
        else if (sortId == R.id.radio_sort_nearest) sortOrder = "nearest";
        else if (sortId == R.id.radio_newest)  sortOrder = "newest";

        listener.onFiltersApplied(gov, sortOrder);
    }
}
