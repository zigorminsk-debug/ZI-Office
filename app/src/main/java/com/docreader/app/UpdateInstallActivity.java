package com.docreader.app;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Открывается нажатием на уведомление об обновлении: сразу запускает
 * системный установщик и закрывается. Если разрешение ещё не выдано —
 * показывает, как его включить.
 */
public class UpdateInstallActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String ver = UpdateManager.downloadedVersion(this);
        if (UpdateManager.downloadedFile(this) == null) {
            Toast.makeText(this, "Файл обновления не найден — проверьте обновления заново", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!ApkInstaller.allowed(this)) {
            Toast.makeText(this, "Разрешите установку из этого источника и нажмите «Установить» снова", Toast.LENGTH_LONG).show();
            ApkInstaller.openSettings(this);
            finish();
            return;
        }
        if (!ApkInstaller.install(this, UpdateManager.downloadedFile(this))) {
            Toast.makeText(this, "Не удалось запустить установщик", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Устанавливаем версию " + ver + "…", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
