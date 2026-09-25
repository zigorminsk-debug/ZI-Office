package com.docreader.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Периодическая проверка обновлений в фоне (ставится через JobScheduler,
 * работает и когда приложение закрыто): проверить → скачать → уведомить.
 */
public class UpdateJobService extends JobService {
    @Override public boolean onStartJob(final JobParameters params) {
        final android.content.Context ctx = getApplicationContext();
        new Thread(() -> {
            try {
                UpdateManager.backgroundCheck(ctx);
            } catch (Throwable t) {
                CrashGuard.log(ctx, "фоновая проверка обновлений", t);
            } finally {
                jobFinished(params, false);
            }
        }, "zi-update").start();
        return true; // работа продолжается в фоновом потоке
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
