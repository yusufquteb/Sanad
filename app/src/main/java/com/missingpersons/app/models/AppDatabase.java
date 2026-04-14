package com.missingpersons.app.models;

import android.content.Context;
import androidx.room.*;

/**
 * AppDatabase — قاعدة البيانات المحلية (Room)
 *
 * Singleton — استخدم getInstance() دائماً
 *
 * الاستخدام:
 *   AppDatabase db = AppDatabase.getInstance(context);
 *   db.reportDao().getApprovedReports().observe(this, reports -> { ... });
 */
@Database(
    entities = { ReportEntity.class },
    version  = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DB_NAME = "missing_persons.db";

    public abstract ReportDao reportDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME)
                        .fallbackToDestructiveMigration() // بسيط للمرحلة الأولى
                        .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void closeInstance() {
        if (INSTANCE != null && INSTANCE.isOpen()) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}
