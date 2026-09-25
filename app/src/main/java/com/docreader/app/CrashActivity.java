package com.docreader.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Показывает отчёт о последнем сбое и позволяет отправить его разработчику.
 * Открывается из главного окна, если приложение в прошлый раз упало.
 */
public class CrashActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_crash);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        final String report = CrashGuard.readReport(this);
        TextView text = findViewById(R.id.crashText);
        text.setText(report);

        findViewById(R.id.btnSend).setOnClickListener(v -> send(report));
        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ZI Office", report));
            Toast.makeText(this, "Отчёт скопирован", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnClose).setOnClickListener(v -> { CrashGuard.clear(this); finish(); });
    }

    private void send(String report) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "ZI Office " + BuildConfig.VERSION_NAME + " — отчёт об ошибке");
            i.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(i, "Отправить отчёт"));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось отправить — скопируйте текст", Toast.LENGTH_LONG).show();
        }
    }
}
