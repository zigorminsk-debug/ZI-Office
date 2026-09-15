package com.docreader.app;
import android.app.Application;
public class App extends Application {
    @Override public void onCreate() { super.onCreate(); ThemePrefs.apply(this); CloudSession.restore(this); }
}
