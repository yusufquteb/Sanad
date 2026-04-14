package com.missingpersons.app.utils;

import android.app.Activity;
import android.content.IntentSender;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

import com.missingpersons.app.R;

/**
 * InAppUpdateManager — تحديث التطبيق من داخله
 * [إصلاح 1] أضفنا import com.missingpersons.app.R
 * [إصلاح 2] لا يوجد import لـ play.core.tasks.Task
 */
public class InAppUpdateManager {

    private static final String TAG                = "InAppUpdateManager";
    public  static final int    UPDATE_REQUEST_CODE = 9001;

    private final Activity       activity;
    private final AppUpdateManager manager;
    private InstallStateUpdatedListener flexibleListener;

    public InAppUpdateManager(@NonNull Activity activity) {
        this.activity = activity;
        this.manager  = AppUpdateManagerFactory.create(activity);
    }

    // ════════════════════════════════════════════════════════
    //  checkForUpdate
    // ════════════════════════════════════════════════════════

    public void checkForUpdate(boolean forceImmediate) {
        manager.getAppUpdateInfo()
            .addOnSuccessListener(info -> {
                int avail = info.updateAvailability();
                Log.d(TAG, "avail=" + avail
                    + " staleness=" + info.clientVersionStalenessDays());

                if (avail == UpdateAvailability.UPDATE_AVAILABLE) {
                    Integer staleness = info.clientVersionStalenessDays();
                    boolean critical  = forceImmediate
                        || (staleness != null && staleness >= 7);

                    if (critical && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        startImmediateUpdate(info);
                    } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        startFlexibleUpdate(info);
                    }

                } else if (avail
                        == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    startImmediateUpdate(info);
                }
            })
            .addOnFailureListener(e ->
                Log.w(TAG, "checkForUpdate failed: " + e.getMessage())
            );
    }

    // ════════════════════════════════════════════════════════
    //  Immediate Update
    // ════════════════════════════════════════════════════════

    private void startImmediateUpdate(@NonNull AppUpdateInfo info) {
        try {
            manager.startUpdateFlowForResult(
                info, AppUpdateType.IMMEDIATE,
                activity, UPDATE_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "startImmediateUpdate: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  Flexible Update
    // ════════════════════════════════════════════════════════

    private void startFlexibleUpdate(@NonNull AppUpdateInfo info) {
        flexibleListener = state -> {
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                showFlexibleRestartSnackbar();
            } else if (state.installStatus() == InstallStatus.FAILED) {
                // [إصلاح] R.string متاح الآن بعد إضافة import R
                Toast.makeText(activity,
                    activity.getString(R.string.update_failed),
                    Toast.LENGTH_SHORT).show();
            }
        };
        manager.registerListener(flexibleListener);

        try {
            manager.startUpdateFlowForResult(
                info, AppUpdateType.FLEXIBLE,
                activity, UPDATE_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "startFlexibleUpdate: " + e.getMessage());
        }
    }

    private void showFlexibleRestartSnackbar() {
        try {
            android.view.View root = activity.getWindow()
                .getDecorView().findViewById(android.R.id.content);

            // [إصلاح] R.string متاح الآن
            com.google.android.material.snackbar.Snackbar
                .make(root,
                    activity.getString(R.string.update_available_title),
                    10_000)
                .setAction(
                    activity.getString(R.string.update_restart_action),
                    v -> manager.completeUpdate())
                .setActionTextColor(0xFF4CAF50)
                .show();
        } catch (Exception e) {
            // fallback: أكمل التحديث مباشرة
            manager.completeUpdate();
        }
    }

    // ════════════════════════════════════════════════════════
    //  onResume
    // ════════════════════════════════════════════════════════

    public void onResume() {
        manager.getAppUpdateInfo()
            .addOnSuccessListener(info -> {
                if (info.updateAvailability()
                        == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    startImmediateUpdate(info);
                }
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    showFlexibleRestartSnackbar();
                }
            });
    }

    // ════════════════════════════════════════════════════════
    //  Cleanup
    // ════════════════════════════════════════════════════════

    public void unregister() {
        if (flexibleListener != null) {
            manager.unregisterListener(flexibleListener);
            flexibleListener = null;
        }
    }

    public void onUpdateCancelled() {
        checkForUpdate(true);
    }
}
