package com.docreader.app;
import android.app.Application;
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashGuard.install(this);      // отчёт о сбое пользователь сможет отправить
        ThemePrefs.apply(this);
        CloudSession.restore(this);
        UpdateManager.schedule(this);   // фоновая проверка обновлений (раз в 6 часов)
    }
}
