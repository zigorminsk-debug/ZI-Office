/* ============================================================================
 *  ZI Office StartFix — маршрутизация вывода в графический интерфейс
 *  или в консоль (одна сборка содержит оба режима).
 * ==========================================================================*/
#include "app.h"

int g_ui_mode = 0;   /* 0 — окно, 1 — консоль */

void zi_ui_puts(const wchar_t* text)
{
#ifndef ZI_UI_NO_GUI
    if (g_ui_mode == 0) { gui_ui_puts(text); return; }
#endif
    cli_ui_puts(text);
}

void zi_ui_puts_owned(wchar_t* text)
{
#ifndef ZI_UI_NO_GUI
    if (g_ui_mode == 0) { gui_ui_puts_owned(text); return; }
#endif
    cli_ui_puts_owned(text);
}

void zi_ui_status(const wchar_t* text)
{
#ifndef ZI_UI_NO_GUI
    if (g_ui_mode == 0) { gui_ui_status(text); return; }
#endif
    cli_ui_status(text);
}

void zi_ui_progress(int step, int total)
{
#ifndef ZI_UI_NO_GUI
    if (g_ui_mode == 0) { gui_ui_progress(step, total); return; }
#endif
    cli_ui_progress(step, total);
}

void zi_ui_done(int plan_index, int exit_code, int cancelled)
{
#ifndef ZI_UI_NO_GUI
    if (g_ui_mode == 0) { gui_ui_done(plan_index, exit_code, cancelled); return; }
#endif
    cli_ui_done(plan_index, exit_code, cancelled);
}

void zi_ui_ready(void)
{
#ifndef ZI_UI_NO_GUI
    if (g_ui_mode == 0) { gui_ui_ready(); return; }
#endif
    cli_ui_ready();
}
