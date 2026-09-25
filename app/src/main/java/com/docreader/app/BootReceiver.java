package com.docreader.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После перезагрузки снова ставим периодическую проверку обновлений. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        try {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) UpdateManager.schedule(ctx);
        } catch (Throwable t) {
            CrashGuard.log(ctx, "перезапуск проверки обновлений", t);
        }
    }
}
