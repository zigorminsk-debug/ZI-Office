package com.docreader.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Одноразовая настройка получения обновлений.
 *
 * Релизы лежат в закрытом репозитории, поэтому у приложения есть два пути:
 * либо токен доступа на этом телефоне (ничего больше не нужно), либо публичный
 * репозиторий обновлений (тогда токен нужен только сборочному workflow).
 * Экран показывает, какой канал работает сейчас, и ведёт по шагам.
 */
public class UpdateSetupActivity extends AppCompatActivity {

    private static final String URL_TOKEN = "https://github.com/settings/personal-access-tokens/new";
    private static final String URL_NEW_REPO = "https://github.com/new?name=ZI-Office-Release&visibility=public";
    private static final String URL_SECRET =
            "https://github.com/" + UpdateManager.PRIVATE_REPO + "/settings/secrets/actions/new";

    private TextView channelText, status;
    private EditText tokenInput;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_update_setup);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        channelText = findViewById(R.id.channelText);
        status = findViewById(R.id.setupStatus);
        progress = findViewById(R.id.setupProgress);
        tokenInput = findViewById(R.id.tokenInput);

        findViewById(R.id.btnMakeToken).setOnClickListener(v -> open(URL_TOKEN));
        findViewById(R.id.btnMakeWriteToken).setOnClickListener(v -> open(URL_TOKEN));
        findViewById(R.id.btnMakeRepo).setOnClickListener(v -> open(URL_NEW_REPO));
        findViewById(R.id.btnAddSecret).setOnClickListener(v -> open(URL_SECRET));
        findViewById(R.id.btnCheckNow).setOnClickListener(v -> check("Проверяем обновления…"));

        findViewById(R.id.btnSaveToken).setOnClickListener(v -> {
            UpdateManager.setToken(this, tokenInput.getText() == null ? "" : tokenInput.getText().toString());
            tokenInput.setText(UpdateManager.token(this));   // если вставили ссылку — показываем сам токен
            if (UpdateManager.hasToken(this)) check("Токен сохранён. Проверяем обновления…");
            else {
                refreshChannel();
                say("Токен не вставлен. Скопируйте его на странице GitHub и вставьте в поле выше.");
            }
        });
        findViewById(R.id.btnClearToken).setOnClickListener(v -> {
            UpdateManager.setToken(this, "");
            tokenInput.setText("");
            refreshChannel();
            say("Токен удалён. Обновления пойдут через публичный репозиторий " + UpdateManager.RELEASE_REPO + ".");
        });
    }

    @Override protected void onResume() {
        super.onResume();
        tokenInput.setText(UpdateManager.token(this));
        refreshChannel();
    }

    /** Показывает, откуда приложение берёт обновления сейчас. */
    private void refreshChannel() {
        boolean byToken = UpdateManager.hasToken(this);
        findViewById(R.id.btnClearToken).setVisibility(byToken ? View.VISIBLE : View.GONE);
        if (byToken) {
            channelText.setText("Сейчас: закрытый репозиторий " + UpdateManager.PRIVATE_REPO + " по токену доступа");
        } else {
            channelText.setText("Сейчас: публичный репозиторий " + UpdateManager.RELEASE_REPO
                    + " (пока он не создан, обновления недоступны)");
        }
    }

    private void check(String firstMessage) {
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText(firstMessage);
        UpdateManager.check(this, new UpdateManager.CheckListener() {
            @Override public void onStart() {
                if (!isFinishing() && !isDestroyed()) status.setText(firstMessage);
            }
            @Override public void onResult(String message, String version, String apkUrl) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                status.setText(version == null ? message
                        : message + "\nНажмите «Проверить обновления сейчас» ещё раз — или откройте «О программе», "
                        + "чтобы скачать и установить.");
            }
            @Override public void onNeedsSetup(String message) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                status.setText(message);
            }
        });
    }

    private void say(String text) {
        status.setVisibility(View.VISIBLE);
        status.setText(text);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }
}
