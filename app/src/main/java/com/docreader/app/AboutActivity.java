package com.docreader.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * Экран «О программе»: версия, кнопка обновления, контакты и частые вопросы.
 */
public class AboutActivity extends AppCompatActivity {
    private static final int[] QUESTIONS = {
            R.id.q1, R.id.q2, R.id.q3, R.id.q4, R.id.q5, R.id.q6, R.id.q7, R.id.q8
    };
    private static final int[] ANSWERS = {
            R.id.a1, R.id.a2, R.id.a3, R.id.a4, R.id.a5, R.id.a6, R.id.a7, R.id.a8
    };

    private MaterialButton btnUpdate;
    private ProgressBar progress;
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_about);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        btnUpdate = findViewById(R.id.btnUpdate);
        progress = findViewById(R.id.updateProgress);
        status = findViewById(R.id.updateStatus);

        ((TextView) findViewById(R.id.versionText))
                .setText("Версия " + BuildConfig.VERSION_NAME + " · сборка " + BuildConfig.VERSION_CODE);
        ((TextView) findViewById(R.id.footer))
                .setText("ZI Office · " + getString(R.string.developer_name) + "\nСборка приложения — GitHub Actions, APK: "
                        + UpdateManager.RELEASE_REPO);

        btnUpdate.setOnClickListener(v -> checkUpdates());
        findViewById(R.id.btnCall).setOnClickListener(v -> call());
        setupFaq();
    }

    /** Кнопка обновления: индикатор проверки, затем при наличии — скачивание. */
    private void checkUpdates() {
        btnUpdate.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText("Проверяем обновления…");
        btnUpdate.setText("Проверить обновления");
        btnUpdate.setOnClickListener(v -> checkUpdates());
        UpdateManager.check(this, new UpdateManager.CheckListener() {
            @Override public void onStart() {
                if (!isFinishing() && !isDestroyed()) status.setText("Проверяем обновления…");
            }
            @Override public void onResult(String message, String version, String apkUrl) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                status.setText(message);
                btnUpdate.setEnabled(true);
                if (version == null || apkUrl == null) return;
                // появилась новая версия — кнопка сразу скачивает её
                btnUpdate.setText("Скачать и установить " + version);
                btnUpdate.setOnClickListener(v -> download(version, apkUrl));
            }
        });
    }

    private void download(final String version, final String apkUrl) {
        btnUpdate.setEnabled(false);
        status.setText("Скачиваем версию " + version + "…");
        progress.setVisibility(View.VISIBLE);
        UpdateManager.startDownload(this, apkUrl, version);
    }

    private void call() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + getString(R.string.developer_phone))));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.developer_phone_pretty), Toast.LENGTH_LONG).show();
        }
    }

    /** Вопросы раскрываются и сворачиваются по нажатию. */
    private void setupFaq() {
        for (int i = 0; i < QUESTIONS.length; i++) {
            final TextView question = findViewById(QUESTIONS[i]);
            final TextView answer = findViewById(ANSWERS[i]);
            final String text = question.getText().toString();
            question.setText("▸ " + text);
            question.setOnClickListener(v -> {
                boolean expand = answer.getVisibility() != View.VISIBLE;
                answer.setVisibility(expand ? View.VISIBLE : View.GONE);
                question.setText((expand ? "▾ " : "▸ ") + text);
            });
        }
    }
}
