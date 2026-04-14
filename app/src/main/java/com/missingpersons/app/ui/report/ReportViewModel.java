package com.missingpersons.app.ui.report;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.missingpersons.app.data.repository.ReportRepository;
import com.missingpersons.app.domain.usecase.SubmitReportUseCase;

/**
 * ReportViewModel — يدير منطق شاشات إضافة البلاغات
 *
 * يُستخدم في:
 * - ReportActivity (إضافة مفقود)
 * - FoundPersonActivity (إضافة عثور)
 * - FoundSightingActivity (إضافة مشاهدة)
 *
 * يتعامل مع:
 * - حالة الـ Multi-step form
 * - Validation لكل خطوة
 * - إرسال البلاغ عبر SubmitReportUseCase
 */
public class ReportViewModel extends ViewModel {

    public enum SubmitState {
        IDLE, VALIDATING, LOADING, SUCCESS, SAVED_LOCALLY, ERROR
    }

    private final SubmitReportUseCase submitUseCase;

    // ── Form State ──────────────────────────────────────────
    private final MutableLiveData<Integer>      currentStep   = new MutableLiveData<>(1);
    private final MutableLiveData<SubmitState>  submitState   = new MutableLiveData<>(SubmitState.IDLE);
    private final MutableLiveData<String>       errorMsg      = new MutableLiveData<>();
    private final MutableLiveData<String>       savedReportId = new MutableLiveData<>();
    private final MutableLiveData<Boolean>      isLoading     = new MutableLiveData<>(false);

    // ── Field Validation Errors ────────────────────────────
    private final MutableLiveData<String> nameError       = new MutableLiveData<>();
    private final MutableLiveData<String> ageError        = new MutableLiveData<>();
    private final MutableLiveData<String> governorateError= new MutableLiveData<>();
    private final MutableLiveData<String> descError       = new MutableLiveData<>();

    // ── In-memory form data ────────────────────────────────
    private SubmitReportUseCase.ReportInput formData = new SubmitReportUseCase.ReportInput();

    public ReportViewModel(ReportRepository repository) {
        this.submitUseCase = new SubmitReportUseCase(repository);
    }

    // ════════════════════════════════════════════════════════
    //  Expose LiveData
    // ════════════════════════════════════════════════════════

    public LiveData<Integer>     currentStep()    { return currentStep;    }
    public LiveData<SubmitState> submitState()    { return submitState;    }
    public LiveData<String>      errorMsg()       { return errorMsg;       }
    public LiveData<String>      savedReportId()  { return savedReportId;  }
    public LiveData<Boolean>     isLoading()      { return isLoading;      }
    public LiveData<String>      nameError()      { return nameError;      }
    public LiveData<String>      ageError()       { return ageError;       }
    public LiveData<String>      governorateError(){ return governorateError; }
    public LiveData<String>      descError()      { return descError;      }

    // ════════════════════════════════════════════════════════
    //  Multi-step Navigation
    // ════════════════════════════════════════════════════════

    public void nextStep() {
        int step = currentStep.getValue() != null ? currentStep.getValue() : 1;
        if (validateStep(step)) {
            currentStep.setValue(step + 1);
        }
    }

    public void previousStep() {
        int step = currentStep.getValue() != null ? currentStep.getValue() : 1;
        if (step > 1) currentStep.setValue(step - 1);
    }

    public void goToStep(int step) {
        currentStep.setValue(step);
    }

    // ════════════════════════════════════════════════════════
    //  Form Data Setters
    // ════════════════════════════════════════════════════════

    public void setPersonName(String name) {
        formData.personName = name;
        nameError.setValue(null); // مسح الخطأ عند الكتابة
    }

    public void setPersonAge(String age) {
        formData.personAge = age;
        ageError.setValue(null);
    }

    public void setPersonGender(String gender) {
        formData.personGender = gender;
    }

    public void setDescription(String desc) {
        formData.description = desc;
        descError.setValue(null);
    }

    public void setGovernorate(String gov) {
        formData.governorate = gov;
        governorateError.setValue(null);
    }

    public void setManualAddress(String address) {
        formData.manualAddress = address;
    }

    public void setImageUrl(String url) {
        formData.imageUrl = url;
    }

    public void setLocation(double lat, double lng) {
        formData.lat = lat;
        formData.lng = lng;
    }

    public void setFaceEmbedding(String embedding) {
        formData.faceEmbedding = embedding;
    }

    public void setReportType(String type) {
        formData.reportType = type;
    }

    public void setReporterId(String uid) {
        formData.reporterId = uid;
    }

    // ════════════════════════════════════════════════════════
    //  Submit
    // ════════════════════════════════════════════════════════

    public void submitReport() {
        submitState.setValue(SubmitState.LOADING);
        isLoading.setValue(true);

        submitUseCase.execute(formData, new ReportRepository.OnSaveCallback() {
            @Override
            public void onSuccess(String reportId) {
                isLoading.setValue(false);
                savedReportId.setValue(reportId);
                submitState.setValue(SubmitState.SUCCESS);
            }

            @Override
            public void onSavedLocally(String reportId) {
                isLoading.setValue(false);
                // إذا كان رسالة خطأ validation
                if (reportId != null && reportId.contains(" ")) {
                    errorMsg.setValue(reportId);
                    submitState.setValue(SubmitState.ERROR);
                } else {
                    savedReportId.setValue(reportId);
                    submitState.setValue(SubmitState.SAVED_LOCALLY);
                }
            }
        });
    }

    /** إعادة الفورم إلى الحالة الابتدائية بعد الإرسال */
    public void resetForm() {
        formData = new SubmitReportUseCase.ReportInput();
        currentStep.setValue(1);
        submitState.setValue(SubmitState.IDLE);
        errorMsg.setValue(null);
        savedReportId.setValue(null);
        isLoading.setValue(false);
        nameError.setValue(null);
        ageError.setValue(null);
        governorateError.setValue(null);
        descError.setValue(null);
    }

    public SubmitReportUseCase.ReportInput getFormData() {
        return formData;
    }

    // ════════════════════════════════════════════════════════
    //  Step Validation
    // ════════════════════════════════════════════════════════

    private boolean validateStep(int step) {
        switch (step) {
            case 1: // الاسم والعمر
                boolean valid1 = true;
                if (formData.personName == null || formData.personName.trim().length() < 2) {
                    nameError.setValue("الاسم مطلوب (حرفان على الأقل)");
                    valid1 = false;
                }
                return valid1;

            case 2: // الصورة (اختيارية لكن مشجعة)
                // الصورة اختيارية — لا نمنع التقدم
                return true;

            case 3: // الموقع
                if (formData.governorate == null || formData.governorate.isEmpty()) {
                    governorateError.setValue("المحافظة مطلوبة");
                    return false;
                }
                return true;

            default:
                return true;
        }
    }
}
