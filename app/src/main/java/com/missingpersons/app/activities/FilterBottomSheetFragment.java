package com.missingpersons.app.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.missingpersons.app.R;

import java.util.Arrays;
import java.util.List;

public class FilterBottomSheetFragment extends BottomSheetDialogFragment {

    private OnFiltersAppliedListener listener;
    private ChipGroup chipGroupGovernorate;
    private RadioGroup radioGroupSort;

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

        chipGroupGovernorate = view.findViewById(R.id.chip_group_governorate);
        radioGroupSort = view.findViewById(R.id.radio_group_sort);
        MaterialButton btnApply = view.findViewById(R.id.btn_apply_filters);
        MaterialButton btnReset = view.findViewById(R.id.btn_reset_filters);

        setupGovernorateChips();

        btnApply.setOnClickListener(v -> {
            applyFilters();
            dismiss();
        });

        btnReset.setOnClickListener(v -> {
            if (listener != null) listener.onFiltersReset();
            dismiss();
        });
    }

    private void setupGovernorateChips() {
        chipGroupGovernorate.removeAllViews();
        for (String gov : governorates) {
            Chip chip = new Chip(requireContext());
            chip.setText(gov);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            chipGroupGovernorate.addView(chip);
        }
        // تحديد "الكل" بشكل افتراضي
        if (chipGroupGovernorate.getChildCount() > 0) {
            ((Chip) chipGroupGovernorate.getChildAt(0)).setChecked(true);
        }
    }

    private void applyFilters() {
        // الحصول على المحافظة المحددة
        int checkedChipId = chipGroupGovernorate.getCheckedChipId();
        String selectedGov = null;
        if (checkedChipId != View.NO_ID) {
            Chip chip = chipGroupGovernorate.findViewById(checkedChipId);
            if (chip != null && !chip.getText().toString().equals("الكل")) {
                selectedGov = chip.getText().toString();
            }
        }

        // الحصول على ترتيب الفرز
        int checkedRadioId = radioGroupSort.getCheckedRadioButtonId();
        String sortOrder = "smart";
        if (checkedRadioId == R.id.radio_newest) {
            sortOrder = "newest";
        } else if (checkedRadioId == R.id.radio_nearest) {
            sortOrder = "nearest";
        }

        if (listener != null) {
            listener.onFiltersApplied(selectedGov, sortOrder);
        }
    }
}