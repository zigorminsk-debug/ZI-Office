/* ============================================================================
 *  ZI Office StartFix — точка входа (без CRT)
 *
 *  Сборка с -Wl,--subsystem,windows  и -DZI_GUI=1  → графический интерфейс.
 *  Сборка с -Wl,--subsystem,console и -DZI_CONSOLE=1 → консольная утилита.
 * ==========================================================================*/
#include "app.h"

void* memcpy(void*, const void*, size_t);
void* memset(void*, int, size_t);

void WINAPI zi_entry(void)
{
    HINSTANCE hInst;
    int argc = 0;
    wchar_t** argv;
    int i;
    int force_cli = 0, start_plan = -1, rc = 0;

#ifdef ZI_CONSOLE
    int console_build = 1;
#else
    int console_build = 0;
#endif

    hInst = GetModuleHandleW(0);
    argv = CommandLineToArgvW(GetCommandLineW(), &argc);

    for (i = 1; i < argc; i++) {
        const wchar_t* a = argv[i];
        while (*a == L'/' || *a == L'-') a++;
        if (rt_wequal_ci(a, L"cli") || rt_wequal_ci(a, L"console")) force_cli = 1;
        else if (rt_wequal_ci(a, L"diag") || rt_wequal_ci(a, L"scan")) { start_plan = zi_find_plan(L"diag"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"quick")) { start_plan = zi_find_plan(L"quick"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"full") || rt_wequal_ci(a, L"repair")) { start_plan = zi_find_plan(L"full"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"start")) { start_plan = zi_find_plan(L"start"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"search")) { start_plan = zi_find_plan(L"search"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"explorer")) { start_plan = zi_find_plan(L"explorer"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"sfc")) { start_plan = zi_find_plan(L"sfc"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"dism")) { start_plan = zi_find_plan(L"dism"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"apps")) { start_plan = zi_find_plan(L"apps"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"rollback") || rt_wequal_ci(a, L"undo")) { start_plan = zi_find_plan(L"rollback"); force_cli = 1; }
        else if (rt_wequal_ci(a, L"help") || rt_wequal_ci(a, L"?")) { force_cli = 1; }
        else if (rt_wequal_ci(a, L"folder") || rt_wequal_ci(a, L"reports")) { force_cli = 1; }
        else if (rt_wequal_ci(a, L"verbose") || rt_wequal_ci(a, L"layout") ||
                 rt_wequal_ci(a, L"nolayout") || rt_wequal_ci(a, L"norestart") ||
                 rt_wequal_ci(a, L"reboot") || rt_wequal_ci(a, L"noreboot") ||
                 rt_wequal_ci(a, L"norestorepoint") || rt_wequal_ci(a, L"version")) {
            force_cli = 1;
        }
    }
    if (argv) LocalFree(argv);

    if (!zi_app_init()) {
        MessageBoxW(0, L"Не удалось инициализировать утилиту (нет доступа к рабочей папке).\r\n"
                       L"Запустите программу от имени администратора.",
                    ZI_APP_NAME, MB_OK | MB_ICONERROR);
        ExitProcess(2);
    }

#ifdef ZI_CONSOLE
    g_ui_mode = 1;
#else
    if (!force_cli) g_ui_mode = 0; else g_ui_mode = 1;
#endif

    if (console_build || force_cli) {
        argc = 0;
        argv = CommandLineToArgvW(GetCommandLineW(), &argc);
        rc = zi_cli_run(argc, argv);
        if (argv) LocalFree(argv);
    } else {
#ifndef ZI_CONSOLE
        rc = zi_gui_run(hInst, start_plan);
        zi_app_shutdown();
#else
        (void)hInst; (void)start_plan;
        rc = zi_cli_run(0, 0);
        zi_app_shutdown();
#endif
    }
    ExitProcess((UINT)rc);
}
