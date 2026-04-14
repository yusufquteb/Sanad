package com.missingpersons.app.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.missingpersons.app.data.repository.AuthRepository;
import com.missingpersons.app.data.repository.ReportRepository;
import com.missingpersons.app.data.repository.UserRepository;
import com.missingpersons.app.ui.auth.AuthViewModel;
import com.missingpersons.app.ui.home.HomeViewModel;
import com.missingpersons.app.ui.profile.ProfileViewModel;
import com.missingpersons.app.ui.report.ReportViewModel;

/**
 * AppViewModelFactory — المصنع المركزي لكل الـ ViewModels
 *
 * الاستخدام في أي Activity:
 *
 *   AppViewModelFactory factory = new AppViewModelFactory(this);
 *
 *   HomeViewModel vm = new ViewModelProvider(this, factory)
 *       .get(HomeViewModel.class);
 *
 * يحل محل:
 *   new ViewModelProvider(this).get(HomeViewModel.class)
 * الذي يفشل لأن ViewModels تحتاج Constructor arguments.
 */
public class AppViewModelFactory implements ViewModelProvider.Factory {

    private final Context context;

    public AppViewModelFactory(Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {

        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(
                    ReportRepository.getInstance(context),
                    new UserRepository()
            );
        }

        if (modelClass.isAssignableFrom(AuthViewModel.class)) {
            return (T) new AuthViewModel(new AuthRepository());
        }

        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(new UserRepository());
        }

        if (modelClass.isAssignableFrom(ReportViewModel.class)) {
            return (T) new ReportViewModel(ReportRepository.getInstance(context));
        }

        throw new IllegalArgumentException(
                "ViewModel غير معروف: " + modelClass.getName()
                + "\nأضفه في AppViewModelFactory"
        );
    }
}
