/* ============================================================================
 *  ZI Office StartFix — режим командной строки (консоль)
 * ==========================================================================*/
#include "app.h"

static HANDLE g_out = INVALID_HANDLE_VALUE;
static HANDLE g_in = INVALID_HANDLE_VALUE;
static int g_console_attached = 0;
static int g_verbose_cli = 0;

/* -------------------------------------------------------------------------- */
static void out_raw(const wchar_t* s, size_t len)
{
    DWORD mode = 0, written = 0;
    if (g_out == INVALID_HANDLE_VALUE) return;
    if (GetConsoleMode(g_out, &mode)) {
        WriteConsoleW(g_out, s, (DWORD)len, &written, 0);
    } else {
        char buf[8192];
        int n = rt_wide_to_mb(rt_oem_cp(), s, (int)len, buf, sizeof(buf));
        if (n > 0) WriteFile(g_out, buf, (DWORD)n, &written, 0);
    }
}

static void out(const wchar_t* s)
{
    if (!s) return;
    out_raw(s, rt_wlen(s));
}

static void outln(const wchar_t* s)
{
    out(s);
    out(L"\r\n");
}

static void outf(const wchar_t* fmt, ...)
{
    wchar_t buf[4096];
    va_list ap;
    va_start(ap, fmt);
    rt_fmtv(buf, 4096, fmt, ap);
    va_end(ap);
    outln(buf);
}

/* ---- колбэки интерфейса -------------------------------------------------- */
void cli_ui_puts(const wchar_t* text) { outln(text ? text : L""); }
void cli_ui_puts_owned(wchar_t* text) { cli_ui_puts(text); rt_free(text); }
void cli_ui_status(const wchar_t* text) { if (g_verbose_cli && text) outf(L"  >> %s", text); }
void cli_ui_progress(int step, int total)
{
    if (g_verbose_cli && total > 0 && step > 0) outf(L"  -- шаг %d из %d", step, total);
}
void cli_ui_done(int plan_index, int exit_code, int cancelled) { (void)plan_index; (void)exit_code; (void)cancelled; }
void cli_ui_ready(void) { }

/* ---- консоль ------------------------------------------------------------- */
static BOOL WINAPI ctrl_handler(DWORD type)
{
    if (type == CTRL_C_EVENT || type == CTRL_BREAK_EVENT || type == CTRL_CLOSE_EVENT) {
        InterlockedExchange(&g_app.cancel, 1);
        outln(L"");
        outln(L"Получен сигнал прерывания — останавливаю текущую операцию...");
        return TRUE;
    }
    return FALSE;
}

static int setup_console(int console_build)
{
    if (!GetConsoleWindow()) {
        if (!AttachConsole(ATTACH_PARENT_PROCESS)) {
            if (!AllocConsole()) return 0;
        }
    }
    g_out = GetStdHandle(STD_OUTPUT_HANDLE);
    g_in = GetStdHandle(STD_INPUT_HANDLE);
    if (g_out == INVALID_HANDLE_VALUE || g_out == 0) g_out = CreateFileW(L"CONOUT$",
        GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, 0,
        OPEN_EXISTING, 0, 0);
    if (g_in == INVALID_HANDLE_VALUE || g_in == 0) g_in = CreateFileW(L"CONIN$",
        GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, 0,
        OPEN_EXISTING, 0, 0);
    g_console_attached = 1;
    SetConsoleCtrlHandler(ctrl_handler, TRUE);
    SetConsoleOutputCP(GetOEMCP());
    SetConsoleTitleW(ZI_APP_NAME L" — " ZI_APP_TITLE);
    (void)console_build;
    return 1;
}

static void print_help(void)
{
    outln(L"");
    outln(L"======================================================================");
    outf(L"  %s %s — %s", ZI_APP_NAME, ZI_APP_VERSION, ZI_APP_TITLE);
    outln(L"======================================================================");
    outln(L"");
    outln(L"НАЗНАЧЕНИЕ");
    outln(L"  Восстановление кнопки и меню «Пуск», поиска и панели задач в Windows");
    outln(L"  Windows 10, Windows 11, Windows Server 2012 / 2012 R2 / 2016 / 2019 /");
    outln(L"  2022 / 2025. Работает без установки дополнительных компонентов.");
    outln(L"");
    outln(L"ИСПОЛЬЗОВАНИЕ");
    outln(L"  StartFix-x64.exe                      — графическое окно (двойной клик)");
    outln(L"  StartFix-cli-x64.exe                  — интерактивное консольное меню");
    outln(L"");
    outln(L"  StartFix-cli-x64.exe /diag            — только диагностика + отчёт");
    outln(L"  StartFix-cli-x64.exe /quick           — быстрый ремонт меню «Пуск»");
    outln(L"  StartFix-cli-x64.exe /full            — полный ремонт (рекомендуется)");
    outln(L"  StartFix-cli-x64.exe /start           — ремонт только меню «Пуск»");
    outln(L"  StartFix-cli-x64.exe /search          — ремонт поиска и Проводника");
    outln(L"  StartFix-cli-x64.exe /explorer        — перезапустить Проводник");
    outln(L"  StartFix-cli-x64.exe /sfc             — sfc /scannow (10-40 минут)");
    outln(L"  StartFix-cli-x64.exe /dism            — DISM /RestoreHealth");
    outln(L"  StartFix-cli-x64.exe /apps            — перерегистрация всех приложений");
    outln(L"  StartFix-cli-x64.exe /rollback        — откат последней резервной копии");
    outln(L"  StartFix-cli-x64.exe /folder          — открыть папку отчётов и журналов");
    outln(L"");
    outln(L"КЛЮЧИ");
    outln(L"  /cli         — принудительно консольный режим (для GUI-сборки)");
    outln(L"  /layout      — сбросить раскладку и плитки меню «Пуск»  (по умолчанию вкл.)");
    outln(L"  /nolayout    — не сбрасывать раскладку меню «Пуск»");
    outln(L"  /norestorepoint — не создавать точку восстановления");
    outln(L"  /norestart   — не перезапускать Проводник");
    outln(L"  /noreboot    — не перезагружать компьютер после ремонта");
    outln(L"  /reboot      — перезагрузить компьютер после успешного ремонта");
    outln(L"  /verbose     — подробный вывод (в том числе команд)");
    outln(L"  /?  /help    — эта справка");
    outln(L"");
    outln(L"КОДЫ ВОЗВРАТА");
    outln(L"  0 — успешно, 1 — завершено с замечаниями, 2 — ошибка,");
    outln(L"  3 — нет прав администратора, 4 — неподдерживаемая система");
    outln(L"");
    outln(L"ПОДРОБНЕЕ");
    outln(L"  Полный журнал и HTML-отчёт: %ProgramData%\\ZI-StartFix\\Reports\\<дата>");
    outln(L"  Резервные копии изменённых параметров: %ProgramData%\\ZI-StartFix\\Backup\\<дата>");
    outln(L"");
}

static int read_line(wchar_t* out, int cap)
{
    char buf[1024];
    DWORD got = 0;
    int i = 0;
    if (g_in == INVALID_HANDLE_VALUE) return 0;
    while (i < (int)sizeof(buf) - 1) {
        if (!ReadFile(g_in, buf + i, 1, &got, 0) || got == 0) break;
        if (buf[i] == '\n' || buf[i] == '\r') break;
        i++;
    }
    buf[i] = 0;
    if (i == 0) return 0;
    rt_mb_to_wide(rt_oem_cp(), buf, i, out, cap);
    out[cap - 1] = 0;
    return 1;
}

static int menu_loop(void)
{
    for (;;) {
        wchar_t line[64];
        int choice = -1;
        outln(L"");
        outln(L"----------------------------------------------------------------------");
        outln(L"  ГЛАВНОЕ МЕНЮ");
        outln(L"----------------------------------------------------------------------");
        outln(L"   1 — Диагностика меню «Пуск» (ничего не меняет)");
        outln(L"   2 — Быстрый ремонт меню «Пуск»");
        outln(L"   3 — Полный ремонт (рекомендуется)");
        outln(L"   4 — Ремонт только меню «Пуск»");
        outln(L"   5 — Ремонт поиска и Проводника");
        outln(L"   6 — Перезапустить Проводник");
        outln(L"   7 — Проверка системных файлов (SFC)");
        outln(L"   8 — Восстановление образа (DISM /RestoreHealth)");
        outln(L"   9 — Перерегистрация всех приложений");
        outln(L"  10 — Откат изменений из резервной копии");
        outln(L"  11 — Открыть папку отчётов и журналов");
        outln(L"   0 — Выход");
        outln(L"");
        out(L"Ваш выбор: ");
        if (!read_line(line, 64)) { outln(L""); outln(L"Ввод недоступен — завершаю работу."); return 0; }
        outln(L"");
        if (line[0] == L'0') return 0;
        if (line[0] >= L'1' && line[0] <= L'9' && line[1] == 0) choice = (line[0] - L'0') - 1;
        else if (line[0] == L'1' && line[1] == L'0' && line[2] == 0) choice = 9;
        else if (line[0] == L'1' && line[1] == L'1' && line[2] == 0) choice = 10;
        else {
            outln(L"Не понял выбор. Введите число из списка.");
            continue;
        }
        if (choice == 10) { zi_open_reports_folder(); continue; }
        {
            const zi_plan* p = zi_get_plan(choice);
            if (!p) { outln(L"Недоступный пункт."); continue; }
            if (p->confirm) {
                wchar_t q[512];
                rt_fmt(q, 512, L"Выполнить «%s»? (y/n): ", p->title);
                out(q);
                if (!read_line(line, 64)) return 0;
                outln(L"");
                if (!(line[0] == L'y' || line[0] == L'Y' || line[0] == L'д' || line[0] == L'Д')) continue;
            }
            zi_worker_run(choice);
            outf(L"Операция завершена. Код: %d", g_app.last_exit);
        }
    }
}

int zi_cli_run(int argc, wchar_t** argv)
{
    int i, plan = -1, explicit_plan = 0, rc = 0;

    if (!setup_console(1)) return 2;

    g_app.options = ZI_OPT_RESTORE_POINT | ZI_OPT_RESET_LAYOUT | ZI_OPT_RESTART_EXPL;

    for (i = 1; i < argc; i++) {
        const wchar_t* a = argv[i];
        if (a[0] != L'/' && a[0] != L'-') {
            if (rt_wequal_ci(a, L"diag") || rt_wequal_ci(a, L"scan")) { plan = zi_find_plan(L"diag"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"quick")) { plan = zi_find_plan(L"quick"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"full") || rt_wequal_ci(a, L"repair")) { plan = zi_find_plan(L"full"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"start") || rt_wequal_ci(a, L"menu")) { plan = zi_find_plan(L"start"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"search")) { plan = zi_find_plan(L"search"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"explorer")) { plan = zi_find_plan(L"explorer"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"sfc")) { plan = zi_find_plan(L"sfc"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"dism")) { plan = zi_find_plan(L"dism"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"apps")) { plan = zi_find_plan(L"apps"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"rollback") || rt_wequal_ci(a, L"undo")) { plan = zi_find_plan(L"rollback"); explicit_plan = 1; }
            else if (rt_wequal_ci(a, L"folder") || rt_wequal_ci(a, L"reports")) { zi_open_reports_folder(); return 0; }
            else if (rt_wequal_ci(a, L"help") || rt_wequal_ci(a, L"?")) { print_help(); return 0; }
            else { outf(L"Неизвестный параметр: %s", a); return 2; }
            continue;
        }
        while (*a == L'/' || *a == L'-') a++;
        if (rt_wequal_ci(a, L"cli") || rt_wequal_ci(a, L"console")) continue;
        else if (rt_wequal_ci(a, L"help") || rt_wequal_ci(a, L"h") || rt_wequal_ci(a, L"?")) { print_help(); return 0; }
        else if (rt_wequal_ci(a, L"verbose") || rt_wequal_ci(a, L"v")) { g_app.options |= ZI_OPT_VERBOSE; g_verbose_cli = 1; }
        else if (rt_wequal_ci(a, L"layout")) { g_app.options |= ZI_OPT_RESET_LAYOUT; }
        else if (rt_wequal_ci(a, L"nolayout")) { g_app.options &= ~ZI_OPT_RESET_LAYOUT; }
        else if (rt_wequal_ci(a, L"norestorepoint")) { g_app.options &= ~ZI_OPT_RESTORE_POINT; }
        else if (rt_wequal_ci(a, L"norestart")) { g_app.options &= ~ZI_OPT_RESTART_EXPL; }
        else if (rt_wequal_ci(a, L"reboot")) { g_app.options |= ZI_OPT_REBOOT_AFTER; }
        else if (rt_wequal_ci(a, L"noreboot")) { g_app.options &= ~ZI_OPT_REBOOT_AFTER; }
        else if (rt_wequal_ci(a, L"version")) { outf(L"%s %s", ZI_APP_NAME, ZI_APP_VERSION); return 0; }
        else if (rt_wequal_ci(a, L"diag")) { plan = zi_find_plan(L"diag"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"quick")) { plan = zi_find_plan(L"quick"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"full")) { plan = zi_find_plan(L"full"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"start")) { plan = zi_find_plan(L"start"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"search")) { plan = zi_find_plan(L"search"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"explorer")) { plan = zi_find_plan(L"explorer"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"sfc")) { plan = zi_find_plan(L"sfc"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"dism")) { plan = zi_find_plan(L"dism"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"apps")) { plan = zi_find_plan(L"apps"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"rollback")) { plan = zi_find_plan(L"rollback"); explicit_plan = 1; }
        else if (rt_wequal_ci(a, L"folder") || rt_wequal_ci(a, L"reports")) { zi_open_reports_folder(); return 0; }
        else { outf(L"Неизвестный ключ: /%s", a); outln(L"Подсказка: /? — справка."); return 2; }
    }

    if (!explicit_plan && argc > 1) {
        /* был только /cli или ключи без действия — работаем через меню */
        outf(L"");
    }

    if (!g_app.is_admin) {
        outln(L"");
        outln(L"**********************************************************************");
        outln(L"  ВНИМАНИЕ: нет прав администратора! Часть операций не выполнится.");
        outln(L"  Запустите утилиту от имени администратора.");
        outln(L"**********************************************************************");
    }

    if (!explicit_plan) {
        if (argc <= 1) {
            /* запуск без параметров из консольной сборки — показываем меню */
            outln(L"");
            outln(L"Добро пожаловать! Для справки введите /?");
            rc = menu_loop();
            zi_app_shutdown();
            return rc;
        }
        rc = menu_loop();
        zi_app_shutdown();
        return rc;
    }

    if (plan < 0) { outln(L"Неизвестная операция."); zi_app_shutdown(); return 2; }
    if (!g_app.supported) {
        outln(L"Система не поддерживается: требуется Windows 8 / Server 2012 или новее.");
        zi_app_shutdown();
        return 4;
    }
    if (!g_app.is_admin) rc = 3;

    {
        const zi_plan* p = zi_get_plan(plan);
        outf(L"Запуск: %s", p->title);
    }
    zi_worker_run(plan);

    if (g_app.last_exit != 0) rc = 1;
    if (rc == 0 && InterlockedCompareExchange(&g_app.cancel, 0, 0)) rc = 1;

    outln(L"");
    outf(L"Итог: критично — %d, предупреждений — %d, исправлено — %d",
         g_app.n_crit, g_app.n_warn, g_app.n_fixed);
    outf(L"Отчёты и журналы: %s", g_app.report_dir);

    if ((g_app.options & ZI_OPT_REBOOT_AFTER) && rc == 0) {
        outln(L"Перезагрузка компьютера через 15 секунд (отмена: shutdown /a)...");
        rt_run_and_wait(L"\"%SystemRoot%\\System32\\shutdown.exe\" /r /t 15 /c "
                        L"\"Перезагрузка по требованию ZI Office StartFix\"",
                        g_app.work_dir, 1);
    }
    zi_app_shutdown();
    return rc;
}
