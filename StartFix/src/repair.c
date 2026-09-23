/* ============================================================================
 *  ZI Office StartFix — ядро: планы ремонта, запуск PowerShell, журнал, отчёт
 * ==========================================================================*/
#include "app.h"

zi_app g_app;

/* --- встроенные скрипты (генерируется tools/embed_scripts.py) ------------- */
#include "embedded_scripts.h"

/* ==========================================================================
 * Планы работ
 * ========================================================================*/
static const zi_step S_DIAG[] = {
    { L"diag", L"Диагностика меню «Пуск» и системных компонентов", 0, 0, 0, 0 }
};

static const zi_step S_QUICK[] = {
    { L"backup",       L"Резервное копирование ветвей реестра",                1, 0, 0, 0 },
    { L"fix-reg",      L"Исправление параметров реестра",                     1, 0, 0, 0 },
    { L"fix-services", L"Проверка и запуск системных служб",                  0, 0, 0, 0 },
    { L"fix-start",    L"Перерегистрация компонентов меню «Пуск»",            0, 0, 0, 0 },
    { L"fix-explorer", L"Перезапуск Проводника",                             0, 0, 0, 1 }
};

static const zi_step S_FULL[] = {
    { L"diag",         L"Диагностика (до ремонта)",                           0, 0, 0, 0 },
    { L"restorepoint", L"Точка восстановления системы",                       0, 1, 0, 0 },
    { L"backup",       L"Резервное копирование ветвей реестра",               1, 0, 0, 0 },
    { L"fix-reg",      L"Исправление параметров реестра",                     1, 0, 0, 0 },
    { L"fix-services", L"Проверка и запуск системных служб",                  0, 0, 0, 0 },
    { L"fix-start",    L"Перерегистрация компонентов меню «Пуск»",            0, 0, 0, 0 },
    { L"reset-layout", L"Сброс раскладки и плиток меню «Пуск»",               1, 0, 1, 0 },
    { L"fix-search",   L"Восстановление поиска (Windows Search / Кортана)",   0, 0, 0, 0 },
    { L"fix-explorer", L"Перезапуск Проводника",                              0, 0, 0, 1 }
};

static const zi_step S_START[] = {
    { L"diag",         L"Диагностика (до ремонта)",                           0, 0, 0, 0 },
    { L"backup",       L"Резервное копирование ветвей реестра",               1, 0, 0, 0 },
    { L"fix-reg",      L"Исправление параметров реестра",                     1, 0, 0, 0 },
    { L"fix-start",    L"Перерегистрация компонентов меню «Пуск»",            0, 0, 0, 0 },
    { L"fix-explorer", L"Перезапуск Проводника",                              0, 0, 0, 1 }
};

static const zi_step S_SEARCH[] = {
    { L"fix-search",   L"Восстановление компонентов поиска",                  0, 0, 0, 0 },
    { L"fix-explorer", L"Перезапуск Проводника",                              0, 0, 0, 1 }
};

static const zi_step S_EXPL[] = {
    { L"fix-explorer", L"Перезапуск Проводника",                              0, 0, 0, 0 }
};

static const zi_step S_SFC[] = {
    { L"sfc",  L"Проверка целостности системных файлов (SFC)",                0, 1, 0, 0 }
};

static const zi_step S_DISM[] = {
    { L"dism", L"Восстановление образа системы (DISM /RestoreHealth)",        0, 1, 0, 0 }
};

static const zi_step S_APPS[] = {
    { L"apps", L"Перерегистрация всех приложений Windows",                    0, 1, 0, 0 }
};

static const zi_step S_ROLLBACK[] = {
    { L"rollback", L"Откат изменений из резервной копии",                     0, 0, 0, 0 }
};

static const zi_plan PLANS[] = {
    { L"diag",     L"Диагностика меню «Пуск»",
      L"Только проверка: ничего не изменяется, в конце формируется отчёт",
      S_DIAG,  1, 0, 1 },
    { L"quick",    L"Быстрый ремонт меню «Пуск»",
      L"Реестр, службы, перерегистрация компонентов меню «Пуск»",
      S_QUICK, 5, 0, 0 },
    { L"full",     L"Полный ремонт (рекомендуется)",
      L"Диагностика, точка восстановления, резервные копии, полное восстановление компонентов",
      S_FULL,  9, 1, 0 },
    { L"start",    L"Ремонт только меню «Пуск»",
      L"Точечно: реестр и компоненты меню «Пуск» без остальных операций",
      S_START, 5, 0, 0 },
    { L"search",   L"Ремонт поиска и Проводника",
      L"Windows Search, Кортана, перезапуск Проводника",
      S_SEARCH, 2, 0, 0 },
    { L"explorer", L"Перезапуск Проводника",
      L"Быстрая операция: перезапуск explorer.exe (панель задач и меню «Пуск»)",
      S_EXPL,  1, 0, 0 },
    { L"sfc",      L"Проверка системных файлов (SFC)",
      L"sfc /scannow — длительная операция (10-40 минут)",
      S_SFC,   1, 1, 0 },
    { L"dism",     L"Восстановление образа системы (DISM)",
      L"DISM /Online /Cleanup-Image /RestoreHealth — длительная операция",
      S_DISM,  1, 1, 0 },
    { L"apps",     L"Перерегистрация всех приложений",
      L"Перерегистрация всех AppX-пакетов пользователя (может занять 10+ минут)",
      S_APPS,  1, 1, 0 },
    { L"rollback", L"Откат изменений (резервная копия)",
      L"Возврат параметров реестра и раскладки меню «Пуск» из последней резервной копии",
      S_ROLLBACK, 1, 1, 0 }
};

#define PLAN_COUNT ((int)(sizeof(PLANS) / sizeof(PLANS[0])))

int zi_plan_count(void) { return PLAN_COUNT; }

const zi_plan* zi_get_plan(int index)
{
    if (index < 0 || index >= PLAN_COUNT) return 0;
    return &PLANS[index];
}

int zi_find_plan(const wchar_t* id)
{
    int i;
    for (i = 0; i < PLAN_COUNT; i++)
        if (rt_wequal_ci(PLANS[i].id, id)) return i;
    return -1;
}

/* ==========================================================================
 * Журнал
 * ========================================================================*/
static CRITICAL_SECTION g_log_cs;
static int g_log_cs_ready = 0;

void zi_log(const wchar_t* line)
{
    if (!g_log_cs_ready) return;
    EnterCriticalSection(&g_log_cs);
    rt_sb_puts(&g_app.log, line ? line : L"");
    rt_sb_puts(&g_app.log, L"\r\n");
    if (g_app.session_log_open) rt_file_writeln(&g_app.session_log, line ? line : L"");
    LeaveCriticalSection(&g_log_cs);
    zi_ui_puts(line ? line : L"");
}

void zi_logf(const wchar_t* fmt, ...)
{
    wchar_t buf[8192];
    va_list ap;
    va_start(ap, fmt);
    rt_fmtv(buf, 8192, fmt, ap);
    va_end(ap);
    zi_log(buf);
}

void zi_log_blank(void) { zi_log(L""); }

/* ==========================================================================
 * Разное: пути, поиск каталогов
 * ========================================================================*/
static void make_stamp(wchar_t* out, size_t cap)
{
    SYSTEMTIME st;
    GetLocalTime(&st);
    rt_fmt(out, cap, L"%04u-%02u-%02u_%02u-%02u-%02u",
           st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
}

static void ensure_dirs(void)
{
    rt_dir_create(g_app.work_dir);
    rt_dir_create(g_app.script_dir);
    rt_dir_create(g_app.backup_root);
    rt_dir_create(g_app.report_dir);
    if (g_app.backup_dir[0]) rt_dir_create(g_app.backup_dir);
}

/* Запись встроенных скриптов на диск (UTF-8 с BOM — важно для PowerShell 5.1) */
static int extract_scripts(void)
{
    int i;
    ensure_dirs();
    for (i = 0; i < ZI_EMBED_COUNT; i++) {
        wchar_t path[MAX_PATH * 2];
        rt_file f;
        rt_path_join(path, MAX_PATH * 2, g_app.script_dir, zi_embed_files[i].name);
        if (!rt_file_create(&f, path)) return 0;
        rt_file_put_bom(&f);
        rt_file_write_raw(&f, zi_embed_files[i].data, zi_embed_files[i].size);
        rt_file_close(&f);
    }
    return 1;
}

/* ==========================================================================
 * Инициализация приложения
 * ========================================================================*/
int zi_app_init(void)
{
    wchar_t base[MAX_PATH * 2];
    wchar_t powerShell[MAX_PATH * 2];
    wchar_t osName[128], edition[128], displayVer[64], buildStr[32];

    memset(&g_app, 0, sizeof(g_app));
    InitializeCriticalSection(&g_log_cs);
    g_log_cs_ready = 1;

    rt_sb_init(&g_app.log, 16384);
    rt_sb_init(&g_app.findings, 4096);

    /* --- рабочий каталог --------------------------------------------------- */
    if (!rt_env(L"ProgramData", base, MAX_PATH * 2) || !base[0])
        if (!rt_env(L"TEMP", base, MAX_PATH * 2)) rt_wncpy(base, L"C:\\Windows\\Temp", MAX_PATH * 2);
    rt_path_join(g_app.work_dir, MAX_PATH * 2, base, L"ZI-StartFix");

    if (!rt_dir_create(g_app.work_dir)) {
        if (rt_env(L"LOCALAPPDATA", base, MAX_PATH * 2) && base[0])
            rt_path_join(g_app.work_dir, MAX_PATH * 2, base, L"ZI-StartFix");
        if (!rt_dir_create(g_app.work_dir)) {
            if (!rt_env(L"TEMP", base, MAX_PATH * 2)) rt_wncpy(base, L"C:\\Windows\\Temp", MAX_PATH * 2);
            rt_path_join(g_app.work_dir, MAX_PATH * 2, base, L"ZI-StartFix");
            rt_dir_create(g_app.work_dir);
        }
    }

    make_stamp(g_app.stamp, 32);
    rt_path_join(g_app.script_dir, MAX_PATH * 2, g_app.work_dir, L"scripts");
    rt_path_join(g_app.backup_root, MAX_PATH * 2, g_app.work_dir, L"Backup");
    {
        wchar_t reports[MAX_PATH * 2];
        rt_path_join(reports, MAX_PATH * 2, g_app.work_dir, L"Reports");
        rt_path_join(g_app.report_dir, MAX_PATH * 2, reports, g_app.stamp);
    }
    rt_path_join(g_app.backup_dir, MAX_PATH * 2, g_app.backup_root, g_app.stamp);

    ensure_dirs();

    {
        wchar_t name[MAX_PATH * 2];
        rt_fmt(name, MAX_PATH * 2, L"session-%s.log", g_app.stamp);
        rt_path_join(g_app.log_path, MAX_PATH * 2, g_app.report_dir, name);
        rt_fmt(name, MAX_PATH * 2, L"findings-%s.txt", g_app.stamp);
        rt_path_join(g_app.findings_path, MAX_PATH * 2, g_app.report_dir, name);
        rt_fmt(name, MAX_PATH * 2, L"console-%s.log", g_app.stamp);
        rt_path_join(g_app.raw_path, MAX_PATH * 2, g_app.report_dir, name);
    }

    if (rt_file_create(&g_app.session_log, g_app.log_path)) {
        g_app.session_log_open = 1;
        rt_file_put_bom(&g_app.session_log);
    }
    /* файл заключений создаём пустым, чтобы PowerShell мог дописывать */
    {
        rt_file f;
        if (rt_file_create(&f, g_app.findings_path)) { rt_file_put_bom(&f); rt_file_close(&f); }
    }

    /* --- система ----------------------------------------------------------- */
    rt_os_version(&g_app.os);
    g_app.is_admin = rt_is_admin();
    g_app.is_server = (g_app.os.product_type != 0 && g_app.os.product_type != 1);
    g_app.is_win11 = (g_app.os.build >= 22000);
    g_app.is_win10 = (g_app.os.build >= 10240);
    g_app.supported = (g_app.os.major > 6) || (g_app.os.major == 6 && g_app.os.minor >= 2);

    osName[0] = edition[0] = displayVer[0] = buildStr[0] = 0;
    if (!rt_reg_get_str(HKEY_LOCAL_MACHINE,
                        L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                        L"ProductName", osName, 128) || !osName[0]) {
        if (g_app.os.major == 6 && g_app.os.minor == 2)
            rt_wncpy(osName, g_app.is_server ? L"Windows Server 2012" : L"Windows 8", 128);
        else if (g_app.os.major == 6 && g_app.os.minor == 3)
            rt_wncpy(osName, L"Windows 8.1 / Server 2012 R2", 128);
        else
            rt_wncpy(osName, L"Windows", 128);
    }
    rt_reg_get_str(HKEY_LOCAL_MACHINE,
                   L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                   L"EditionID", edition, 128);
    if (rt_reg_get_str(HKEY_LOCAL_MACHINE,
                       L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                       L"DisplayVersion", displayVer, 64) && displayVer[0]) {
        /* Windows 11 / Server 2022+ */
    } else if (rt_reg_get_str(HKEY_LOCAL_MACHINE,
                              L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                              L"ReleaseId", displayVer, 64) && displayVer[0]) {
        /* Windows 10 */
    } else {
        rt_reg_get_str(HKEY_LOCAL_MACHINE,
                       L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                       L"CurrentVersion", displayVer, 64);
    }
    {
        DWORD ubr = 0;
        if (rt_reg_get_dword(HKEY_LOCAL_MACHINE,
                             L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                             L"UBR", &ubr))
            rt_fmt(buildStr, 32, L".%u", ubr);
    }
    rt_fmt(g_app.os_name, 160, L"%s%s%s (сборка %u%s)",
           osName,
           displayVer[0] ? L" " : L"",
           displayVer[0] ? displayVer : L"",
           g_app.os.build, buildStr);

    rt_computer_name(g_app.pc_name, 64);
    rt_user_name(g_app.user_name, 80);

    /* --- скрипты ----------------------------------------------------------- */
    if (!extract_scripts())
        return 0;

    /* --- шапка журнала ----------------------------------------------------- */
    rt_powershell_path(powerShell, MAX_PATH * 2);
    zi_log(L"======================================================================");
    zi_logf(L"  %s  —  %s", ZI_APP_NAME, ZI_APP_TITLE);
    zi_logf(L"  версия %s (сборка %s)", ZI_APP_VERSION, ZI_APP_BUILD);
    zi_log(L"======================================================================");
    zi_logf(L"Дата:            %s", (rt_now_str(base, MAX_PATH * 2), base));
    zi_logf(L"Компьютер:       %s\\%s", g_app.pc_name, g_app.user_name);
    zi_logf(L"Система:         %s", g_app.os_name);
    zi_logf(L"Тип:             %s", g_app.is_server ? L"серверная ОС" : L"рабочая станция");
    zi_logf(L"Права:           %s", g_app.is_admin ? L"администратор" : L"ОБЫЧНЫЙ ПОЛЬЗОВАТЕЛЬ (не все операции доступны!)");
    zi_logf(L"Windows 11:      %s", g_app.is_win11 ? L"да" : L"нет");
    zi_logf(L"Рабочая папка:   %s", g_app.work_dir);
    zi_logf(L"Журнал сессии:   %s", g_app.log_path);
    zi_logf(L"Файл заключений: %s", g_app.findings_path);
    (void)powerShell;
    if (!g_app.supported) {
        zi_log(L"");
        zi_log(L"ВНИМАНИЕ: система старше Windows 8 / Server 2012. Полноценное меню «Пуск»");
        zi_log(L"в таких системах отсутствует, часть шагов ремонта будет пропущена.");
    }
    if (!g_app.is_admin) {
        zi_log(L"");
        zi_log(L"ВНИМАНИЕ: утилита запущена без прав администратора. Перезапустите её");
        zi_log(L"от имени администратора: правый клик по файлу — «Запуск от имени администратора».");
    }
    {
        wchar_t core[MAX_PATH * 2];
        rt_env_expand(L"%SystemRoot%\\explorer.exe", core, MAX_PATH * 2);
        if (g_app.is_server && !rt_file_exists(core)) {
            zi_log(L"");
            zi_log(L"ПРИМЕЧАНИЕ: explorer.exe не найден — похоже, это установка Server Core.");
            zi_log(L"Меню «Пуск» и графическая оболочка в Server Core недоступны.");
        }
    }
    zi_log_blank();
    return 1;
}

void zi_app_shutdown(void)
{
    if (g_app.session_log_open) {
        zi_log_blank();
        rt_file_close(&g_app.session_log);
        g_app.session_log_open = 0;
    }
    rt_sb_free(&g_app.log);
    rt_sb_free(&g_app.findings);
    if (g_log_cs_ready) { DeleteCriticalSection(&g_log_cs); g_log_cs_ready = 0; }
}

void zi_open_reports_folder(void)
{
    ensure_dirs();
    rt_gui_open_path(g_app.report_dir);
}

/* ==========================================================================
 * Построчный вывод журнала шага (реализация — в rt.c: rt_logtail_*)
 * ========================================================================*/
static void step_line_cb(const wchar_t* line, void* ud)
{
    (void)ud;
    zi_log(line);
}

/* ==========================================================================
 * Запуск шага
 * ========================================================================*/
static void build_step_log_name(zi_step* st, int index, wchar_t* out, size_t cap)
{
    wchar_t name[MAX_PATH];
    rt_fmt(name, MAX_PATH, L"%02d-%s.log", index, st->mode);
    rt_path_join(out, cap, g_app.report_dir, name);
}

static void build_ps_command(rt_sb* sb, const wchar_t* mode, const wchar_t* stepLog,
                             int use_backup, int restore_point, int reset_layout,
                             int no_explorer)
{
    wchar_t ps[MAX_PATH * 2];
    wchar_t script[MAX_PATH * 2];

    rt_powershell_path(ps, MAX_PATH * 2);
    rt_path_join(script, MAX_PATH * 2, g_app.script_dir, L"zi-repair.ps1");

    rt_sb_printf(sb,
        L"\"%s\" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass "
        L"-InputFormat Text -OutputFormat Text "
        L"-File \"%s\" -Mode %s -Log \"%s\" -Findings \"%s\"",
        ps, script, mode, stepLog, g_app.findings_path);
    if (use_backup)    rt_sb_printf(sb, L" -BackupDir \"%s\"", g_app.backup_dir);
    if (restore_point) rt_sb_puts(sb, L" -RestorePoint");
    if (reset_layout)  rt_sb_puts(sb, L" -ResetLayout");
    if (no_explorer)   rt_sb_puts(sb, L" -NoExplorer");
}

/* Вариант запуска в обход политики выполнения скриптов */
static void build_ps_command_iex(rt_sb* sb, const wchar_t* mode, const wchar_t* stepLog,
                                 int use_backup, int restore_point, int reset_layout,
                                 int no_explorer)
{
    wchar_t ps[MAX_PATH * 2];
    wchar_t script[MAX_PATH * 2];

    rt_powershell_path(ps, MAX_PATH * 2);
    rt_path_join(script, MAX_PATH * 2, g_app.script_dir, L"zi-repair.ps1");
    rt_sb_printf(sb,
        L"\"%s\" -NoLogo -NoProfile -NonInteractive "
        L"-Command \"& ([scriptblock]::Create((Get-Content -Raw -LiteralPath '%s'))) "
        L"-Mode %s -Log '%s' -Findings '%s'",
        ps, script, mode, stepLog, g_app.findings_path);
    if (use_backup)    rt_sb_printf(sb, L" -BackupDir '%s'", g_app.backup_dir);
    if (restore_point) rt_sb_puts(sb, L" -RestorePoint");
    if (reset_layout)  rt_sb_puts(sb, L" -ResetLayout");
    if (no_explorer)   rt_sb_puts(sb, L" -NoExplorer");
    rt_sb_puts(sb, L"\"");
}

/* Вывести текст консольного вывода (OEM) в журнал построчно */
static void dump_console(const char* data, int len)
{
    wchar_t conv[8192];
    int n;
    if (len <= 0) return;
    if (len > 4000) { data += (len - 4000); len = 4000; }
    n = rt_mb_to_wide(rt_oem_cp(), data, len, conv, 8190);
    if (n <= 0) return;
    conv[n] = 0;
    {
        wchar_t* p = conv;
        while (*p) {
            wchar_t* nl = (wchar_t*)rt_wfind_ch(p, L'\n');
            if (nl) *nl = 0;
            {
                wchar_t* line = rt_wtrim(p);
                if (*line) zi_logf(L"    | %s", line);
            }
            if (!nl) break;
            p = nl + 1;
        }
    }
}

/* Запустить один шаг; возвращает код возврата процесса */
static int run_step(const zi_step* st, int index)
{
    rt_sb cmd;
    rt_logtail tail;
    rt_proc proc;
    rt_file raw;
    wchar_t stepLog[MAX_PATH * 2];
    int rc = -1, attempt, policy_blocked = 0;
    int use_backup, use_restorepoint, use_layout;
    char* console_buf = (char*)rt_alloc(1 << 20);
    int console_len = 0;

    if (!console_buf) return -1;

    build_step_log_name((zi_step*)st, index, stepLog, MAX_PATH * 2);
    DeleteFileW(stepLog);

    rt_sb_init(&cmd, 4096);

    use_backup       = st->needs_backup;
    use_restorepoint = (g_app.options & ZI_OPT_RESTORE_POINT) && rt_wequal(st->mode, L"restorepoint");
    use_layout       = (g_app.options & ZI_OPT_RESET_LAYOUT) && st->layout_option;

    for (attempt = 0; attempt < 2; attempt++) {
        int spin = 0;

        memset(&proc, 0, sizeof(proc));
        rt_sb_reset(&cmd);
        if (attempt == 0)
            build_ps_command(&cmd, st->mode, stepLog, use_backup, use_restorepoint, use_layout, 0);
        else
            build_ps_command_iex(&cmd, st->mode, stepLog, use_backup, use_restorepoint, use_layout, 0);

        if (g_app.options & ZI_OPT_VERBOSE)
            zi_logf(L"    команда: %s", cmd.p);

        if (!rt_proc_start(&proc, cmd.p, g_app.work_dir)) {
            zi_log(L"    ОШИБКА: не удалось запустить PowerShell (powershell.exe недоступен).");
            rt_sb_free(&cmd);
            rt_free(console_buf);
            return -1;
        }

        console_len = 0;
        console_buf[0] = 0;
        rt_logtail_open(&tail, stepLog);
        memset(&raw, 0, sizeof(raw));
        if (!rt_file_open_append(&raw, g_app.raw_path)) raw.h = 0;

        for (;;) {
            DWORD got = 0;
            int r;
            if (console_len > (1 << 20) - 8192) console_len = 0;   /* буфер переполнен — сдвигаем */
            r = rt_pipe_read(proc.hOutRead, console_buf + console_len,
                             (DWORD)((1 << 20) - 4096 - console_len), &got);
            if (r == 1 && got > 0) {
                if (raw.h) rt_file_write_raw(&raw, console_buf + console_len, got);
                console_len += (int)got;
                console_buf[console_len] = 0;
                if ((spin % 4) == 0) rt_logtail_poll(&tail, step_line_cb, 0);
            }
            rt_logtail_poll(&tail, step_line_cb, 0);
            if (g_app.cancel) {
                zi_log(L"    ... операция прервана по требованию пользователя");
                rt_proc_kill(&proc);
                break;
            }
            if (!rt_proc_running(&proc)) break;
            rt_sleep_ms(120);
            spin++;
        }

        /* дочитать остатки вывода */
        rt_sleep_ms(200);
        for (;;) {
            DWORD got = 0;
            int r;
            if (console_len > (1 << 20) - 8192) console_len = 0;   /* буфер переполнен — сдвигаем */
            r = rt_pipe_read(proc.hOutRead, console_buf + console_len,
                             (DWORD)((1 << 20) - 4096 - console_len), &got);
            if (r != 1 || got == 0) break;
            if (raw.h) rt_file_write_raw(&raw, console_buf + console_len, got);
            console_len += (int)got;
            console_buf[console_len] = 0;
        }
        rt_proc_wait(&proc, 5000);
        rc = (int)rt_proc_exit_code(&proc);
        rt_logtail_poll(&tail, step_line_cb, 0);
        rt_logtail_flush(&tail, step_line_cb, 0);
        rt_logtail_close(&tail);
        if (raw.h) rt_file_close(&raw);
        rt_proc_close(&proc);

        if (g_app.cancel) break;

        /* если скрипт ничего не записал — вывести консольный вывод и проверить
           блокировку политики выполнения скриптов */
        if (rt_file_size(stepLog) <= 3) {
            wchar_t conv[8192];
            int n = rt_mb_to_wide(rt_oem_cp(), console_buf,
                                  console_len > 4000 ? 4000 : console_len, conv, 8190);
            if (n > 0) {
                conv[n] = 0;
                if (rt_wcontains_ci(conv, L"не разрешен") ||
                    rt_wcontains_ci(conv, L"не может быть загружен") ||
                    rt_wcontains_ci(conv, L"cannot be loaded") ||
                    rt_wcontains_ci(conv, L"is not allowed") ||
                    rt_wcontains_ci(conv, L"UnauthorizedAccess"))
                    policy_blocked = 1;
            }
        }
        if (rc == 0 || !policy_blocked || attempt == 1) break;
        zi_log(L"    Политика выполнения скриптов блокирует запуск файла — повтор без файла...");
        policy_blocked = 0;
    }

    if (rc != 0 && rt_file_size(stepLog) <= 3) dump_console(console_buf, console_len);

    if (rc == 3010 || rc == 1641) {
        zi_log(L"    (для завершения требуется перезагрузка)");
        rc = 0;
    }

    rt_sb_free(&cmd);
    rt_free(console_buf);
    return rc;
}

/* ==========================================================================
 * Разбор файла заключений
 * ========================================================================*/
static void refresh_findings(void)
{
    rt_file f;
    static char buf[1 << 20];
    DWORD got = 0;
    int crit = 0, warn = 0, info = 0;
    if (!rt_file_open_read(&f, g_app.findings_path)) return;
    if (!ReadFile(f.h, buf, sizeof(buf) - 1, &got, 0)) got = 0;
    rt_file_close(&f);
    buf[got] = 0;

    rt_sb_reset(&g_app.findings);

    {
        char* line = buf;
        while (*line) {
            char* nl = line;
            while (*nl && *nl != '\n' && *nl != '\r') nl++;
            {
                char save = *nl;
                *nl = 0;
                if (*line) {
                    /* формат: SEV<TAB>КОД<TAB>текст */
                    wchar_t w[MAX_PATH * 4];
                    int n = rt_mb_to_wide(CP_UTF8, line, -1, w, MAX_PATH * 4 - 1);
                    if (n > 0) {
                        int sep = 0;
                        w[n] = 0;
                        if (w[0] == 0xFEFF) { /* BOM при первой строке */ memmove(w, w + 1, (size_t)n * sizeof(wchar_t)); }
                        if (rt_wstarts_ci(w, L"CRIT")) { crit++; sep = 4; }
                        else if (rt_wstarts_ci(w, L"WARN")) { warn++; sep = 4; }
                        else if (rt_wstarts_ci(w, L"INFO")) { info++; sep = 4; }
                        else if (rt_wstarts_ci(w, L"OK")) { g_app.n_fixed++; sep = 2; }
                        {
                            wchar_t* rest = w + sep;
                            rt_sb_puts(&g_app.findings, w);
                            rt_sb_puts(&g_app.findings, L"\r\n");
                            (void)rest;
                        }
                    }
                }
                if (save == 0) break;
                line = nl + 1;
                while (*line == '\r' || *line == '\n') line++;
            }
        }
    }
    g_app.n_crit = crit;
    g_app.n_warn = warn;
    g_app.n_info = info;
}

/* ==========================================================================
 * Отчёт
 * ========================================================================*/
static void html_escape(rt_sb* out, const wchar_t* s)
{
    for (; *s; s++) {
        if (*s == L'<') rt_sb_puts(out, L"&lt;");
        else if (*s == L'>') rt_sb_puts(out, L"&gt;");
        else if (*s == L'&') rt_sb_puts(out, L"&amp;");
        else rt_sb_putc(out, *s);
    }
}

static void findings_table(rt_sb* out, const wchar_t* title)
{
    rt_sb_printf(out, L"<h2>%s</h2>\r\n", title);
    if (g_app.findings.len == 0) {
        rt_sb_puts(out, L"<p class=\"none\">Заключений нет — критичных проблем не обнаружено.</p>\r\n");
        return;
    }
    rt_sb_puts(out, L"<table><thead><tr><th>Уровень</th><th>Заключение</th></tr></thead><tbody>\r\n");
    {
        const wchar_t* p = g_app.findings.p;
        while (p && *p) {
            const wchar_t* nl = rt_wfind_ch(p, L'\n');
            wchar_t line[2048];
            size_t len = nl ? (size_t)(nl - p) : rt_wlen(p);
            const wchar_t* tab;
            const wchar_t* cls = L"info";
            const wchar_t* lvl = L"ИНФО";
            if (len >= 2047) len = 2047;
            memcpy(line, p, len * sizeof(wchar_t));
            line[len] = 0;
            rt_wtrim(line);
            tab = rt_wfind_ch(line, L'\t');
            if (rt_wstarts_ci(line, L"CRIT")) { cls = L"crit"; lvl = L"КРИТИЧНО"; }
            else if (rt_wstarts_ci(line, L"WARN")) { cls = L"warn"; lvl = L"ВНИМАНИЕ"; }
            else if (rt_wstarts_ci(line, L"INFO")) { cls = L"info"; lvl = L"ИНФО"; }
            else if (rt_wstarts_ci(line, L"OK")) { cls = L"ok"; lvl = L"ИСПРАВЛЕНО"; }
            rt_sb_puts(out, L"<tr><td class=\"");
            rt_sb_puts(out, cls);
            rt_sb_puts(out, L"\">");
            rt_sb_puts(out, lvl);
            rt_sb_puts(out, L"</td><td>");
            if (tab) {
                const wchar_t* text = tab + 1;
                const wchar_t* tab2 = rt_wfind_ch(text, L'\t');
                wchar_t tmp[2048];
                if (tab2) {
                    size_t l2 = (size_t)(tab2 - text);
                    if (l2 > 2047) l2 = 2047;
                    memcpy(tmp, text, l2 * sizeof(wchar_t));
                    tmp[l2] = 0;
                    html_escape(out, tmp);
                    rt_sb_puts(out, L" <span class=\"src\">[");
                    html_escape(out, tab + 1);
                    rt_sb_puts(out, L"]</span>");
                } else {
                    html_escape(out, text);
                }
            } else {
                html_escape(out, line);
            }
            rt_sb_puts(out, L"</td></tr>\r\n");
            if (!nl) break;
            p = nl + 1;
            while (*p == L'\r' || *p == L'\n') p++;
        }
    }
    rt_sb_puts(out, L"</tbody></table>\r\n");
}

int zi_write_report(int plan_index, int rc)
{
    const zi_plan* plan = zi_get_plan(plan_index);
    rt_sb html;
    rt_file f;
    wchar_t path[MAX_PATH * 2];
    wchar_t name[MAX_PATH];
    wchar_t now[MAX_PATH];

    rt_sb_init(&html, 65536);
    rt_now_str(now, MAX_PATH);

    rt_sb_puts(&html,
        L"<!DOCTYPE html>\r\n<html lang=\"ru\"><head><meta charset=\"utf-8\">\r\n"
        L"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n"
        L"<title>ZI Office StartFix — отчёт</title>\r\n<style>\r\n"
        L"body{font-family:Segoe UI,Arial,sans-serif;margin:0;background:#f4f6f8;color:#1c2530}\r\n"
        L".wrap{max-width:1100px;margin:0 auto;padding:24px}\r\n"
        L"header{background:#0b3d91;color:#fff;padding:24px;border-radius:10px}\r\n"
        L"header h1{margin:0 0 6px;font-size:22px}\r\nheader p{margin:2px 0;opacity:.9;font-size:14px}\r\n"
        L".cards{display:flex;gap:14px;flex-wrap:wrap;margin:18px 0}\r\n"
        L".card{flex:1 1 180px;background:#fff;border-radius:10px;padding:14px 16px;box-shadow:0 1px 3px rgba(0,0,0,.08)}\r\n"
        L".card b{display:block;font-size:26px}\r\n.card span{font-size:13px;color:#5a6b7c}\r\n"
        L"h2{font-size:17px;margin:26px 0 10px}\r\n"
        L"table{width:100%;border-collapse:collapse;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08)}\r\n"
        L"th,td{padding:9px 12px;border-bottom:1px solid #e6ebf0;font-size:14px;text-align:left;vertical-align:top}\r\n"
        L"td.crit{color:#b00020;font-weight:600;white-space:nowrap}\r\n"
        L"td.warn{color:#a05a00;font-weight:600;white-space:nowrap}\r\n"
        L"td.info{color:#0b5cab;font-weight:600;white-space:nowrap}\r\n"
        L"td.ok{color:#0a7a3d;font-weight:600;white-space:nowrap}\r\n"
        L".src{color:#8794a3;font-size:12px}\r\n"
        L"pre{background:#0f1720;color:#d6e1ec;padding:16px;border-radius:10px;overflow:auto;font-size:12.5px;line-height:1.45}\r\n"
        L".none{color:#0a7a3d}\r\nfooter{margin:24px 0;color:#6b7a8a;font-size:12.5px}\r\n"
        L"</style></head><body><div class=\"wrap\">\r\n<header>\r\n");

    rt_sb_printf(&html, L"<h1>%s — отчёт о ремонте меню «Пуск»</h1>\r\n", ZI_APP_NAME);
    rt_sb_printf(&html, L"<p>Устройство: %s\\%s</p>\r\n", g_app.pc_name, g_app.user_name);
    html_escape(&html, g_app.os_name);
    rt_sb_printf(&html, L"<p>Система: ");
    html_escape(&html, g_app.os_name);
    rt_sb_printf(&html, L"</p>\r\n<p>Операция: %s</p>\r\n<p>Дата: %s</p>\r\n", plan ? plan->title : L"—", now);
    rt_sb_puts(&html, L"</header>\r\n<div class=\"cards\">\r\n");
    rt_sb_printf(&html, L"<div class=\"card\"><b>%d</b><span>критичных проблем</span></div>\r\n", g_app.n_crit);
    rt_sb_printf(&html, L"<div class=\"card\"><b>%d</b><span>предупреждений</span></div>\r\n", g_app.n_warn);
    rt_sb_printf(&html, L"<div class=\"card\"><b>%d</b><span>исправлено</span></div>\r\n", g_app.n_fixed);
    rt_sb_printf(&html, L"<div class=\"card\"><b>%d</b><span>замечаний справочно</span></div>\r\n", g_app.n_info);
    rt_sb_printf(&html, L"<div class=\"card\"><b>%s</b><span>код завершения</span></div>\r\n", rc == 0 ? L"ОК" : L"ошибка");
    rt_sb_puts(&html, L"</div>\r\n");

    findings_table(&html, L"Заключения диагностики");

    rt_sb_puts(&html, L"<h2>Что делать дальше</h2>\r\n<ul>\r\n");
    rt_sb_puts(&html, L"<li>Если меню «Пуск» по-прежнему не открывается — выполните «Полный ремонт», затем перезагрузите компьютер.</li>\r\n");
    rt_sb_puts(&html, L"<li>Проверьте систему командами <code>sfc /scannow</code> и <code>DISM /Online /Cleanup-Image /RestoreHealth</code> (кнопки в утилите).</li>\r\n");
    rt_sb_puts(&html, L"<li>Если проблема только у одного пользователя — создайте новую учётную запись и проверьте работу меню «Пуск» в ней: это укажет на повреждение профиля.</li>\r\n");
    rt_sb_puts(&html, L"<li>Резервные копии изменённых параметров реестра: <code>");
    html_escape(&html, g_app.backup_dir);
    rt_sb_puts(&html, L"</code>. Откат — кнопка «Откат изменений» в утилите.</li>\r\n</ul>\r\n");

    rt_sb_puts(&html, L"<h2>Полный журнал</h2>\r\n<pre>");
    {
        const wchar_t* logText = g_app.log.p ? g_app.log.p : L"";
        size_t logLen = rt_wlen(logText);
        if (logLen > 200000) {
            logText += (logLen - 200000);
            rt_sb_puts(&html, L"(показаны последние 200 000 символов; полный журнал — в файле session-*.log)\r\n\r\n");
        }
        html_escape(&html, logText);
    }
    rt_sb_puts(&html, L"</pre>\r\n<footer>Файлы журнала: <code>");
    html_escape(&html, g_app.report_dir);
    rt_sb_puts(&html, L"</code><br>Сформировано ZI Office StartFix ");
    rt_sb_puts(&html, ZI_APP_VERSION);
    rt_sb_puts(&html, L"</footer>\r\n</div></body></html>\r\n");

    rt_fmt(name, MAX_PATH, L"report-%s.html", g_app.stamp);
    rt_path_join(path, MAX_PATH * 2, g_app.report_dir, name);
    if (!rt_file_create(&f, path)) { rt_sb_free(&html); return 0; }
    rt_file_put_bom(&f);
    rt_file_write(&f, html.p);
    rt_file_close(&f);

    zi_log_blank();
    zi_logf(L"Отчёт сохранён: %s", path);
    g_app.last_exit = rc;
    rt_sb_free(&html);
    return 1;
}

/* ==========================================================================
 * Резервные копии
 * ========================================================================*/
const wchar_t* zi_latest_backup_dir(void)
{
    static wchar_t best[MAX_PATH * 2];
    WIN32_FIND_DATAW fd;
    HANDLE h;
    wchar_t mask[MAX_PATH * 2];
    int found = 0;

    best[0] = 0;
    rt_path_join(mask, MAX_PATH * 2, g_app.backup_root, L"*");
    h = FindFirstFileW(mask, &fd);
    if (h == INVALID_HANDLE_VALUE) return best;
    do {
        if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
        if (rt_wequal(fd.cFileName, L".") || rt_wequal(fd.cFileName, L"..")) continue;
        {
            wchar_t dir[MAX_PATH * 2], probe[MAX_PATH * 2];
            rt_path_join(dir, MAX_PATH * 2, g_app.backup_root, fd.cFileName);
            rt_path_join(probe, MAX_PATH * 2, dir, L"actions.txt");
            if (!rt_file_exists(probe)) {
                wchar_t regmode[MAX_PATH];
                rt_fmt(regmode, MAX_PATH, L"%s\\*.reg", dir);
                {
                    WIN32_FIND_DATAW rd;
                    HANDLE rh = FindFirstFileW(regmode, &rd);
                    if (rh == INVALID_HANDLE_VALUE) continue;
                    FindClose(rh);
                }
            }
            if (!found || rt_wcmp(dir, best) > 0) {
                rt_wncpy(best, dir, MAX_PATH * 2);
                found = 1;
            }
        }
    } while (FindNextFileW(h, &fd));
    FindClose(h);
    return best;
}

int zi_rollback_available(void)
{
    const wchar_t* d = zi_latest_backup_dir();
    return d && d[0];
}

/* ==========================================================================
 * Перезапуск Проводника
 * ========================================================================*/
int zi_restart_explorer(void)
{
    wchar_t cmd[MAX_PATH * 2];
    wchar_t explorer[MAX_PATH * 2];
    rt_env_expand(L"%SystemRoot%\\explorer.exe", explorer, MAX_PATH * 2);

    if (!rt_file_exists(explorer)) {
        zi_log(L"Проводник (explorer.exe) не найден — пропускаем перезапуск.");
        return 0;
    }

    zi_log(L"Завершение explorer.exe...");
    rt_run_and_wait(L"\"%SystemRoot%\\System32\\taskkill.exe\" /f /im explorer.exe",
                    g_app.work_dir, 1);
    rt_sleep_ms(1500);

    if (rt_is_admin()) {
        /* понижение прав: Проводник должен запускаться без прав администратора */
        rt_fmt(cmd, MAX_PATH * 2, L"\"%SystemRoot%\\System32\\runas.exe\" /trustlevel:0x20000 \"%s\"",
               explorer);
        zi_log(L"Запуск explorer.exe без повышенных прав...");
    } else {
        rt_fmt(cmd, MAX_PATH * 2, L"\"%s\"", explorer);
        zi_log(L"Запуск explorer.exe...");
    }
    {
        int rc = rt_run_and_wait(cmd, g_app.work_dir, 1);
        rt_sleep_ms(1200);
        zi_logf(L"Проводник запущен (код %d).", rc);
    }
    return 1;
}

/* ==========================================================================
 * Выполнение плана
 * ========================================================================*/
static void log_system_summary(void)
{
    zi_log(L"----------------------------------------------------------------------");
    zi_logf(L"Сводка: критично — %d, предупреждений — %d, исправлено — %d",
            g_app.n_crit, g_app.n_warn, g_app.n_fixed);
    zi_log(L"----------------------------------------------------------------------");
}

int zi_worker_run(int plan_index)
{
    const zi_plan* plan = zi_get_plan(plan_index);
    int i, rc = 0, cancelled = 0;
    wchar_t stepLog[MAX_PATH * 2];

    if (!plan) return -1;

    InterlockedExchange(&g_app.cancel, 0);
    g_app.cur_plan = plan_index;
    g_app.total_steps = plan->count;
    g_app.cur_step = 0;

    zi_log_blank();
    zi_logf(L"######################################################################");
    zi_logf(L"#  %s", plan->title);
    zi_logf(L"######################################################################");
    zi_logf(L"Режим выполнения: PowerShell, скрытое окно, все действия протоколируются.");
    zi_logf(L"Скрипты: %s", g_app.script_dir);
    if (plan->confirm) zi_log(L"(операция длительная, не закрывайте окно утилиты)");
    zi_log_blank();

    for (i = 0; i < plan->count; i++) {
        const zi_step* st = &plan->steps[i];
        char dummy = 0;
        (void)dummy;

        if (g_app.cancel) { cancelled = 1; break; }

        if (st->layout_option && !(g_app.options & ZI_OPT_RESET_LAYOUT)) {
            zi_logf(L"[%d/%d] %s — пропущено (опция выключена)", i + 1, plan->count, st->title);
            continue;
        }
        if (st->expl_option && !(g_app.options & ZI_OPT_RESTART_EXPL)) {
            zi_logf(L"[%d/%d] %s — пропущено (опция выключена)", i + 1, plan->count, st->title);
            continue;
        }
        if (rt_wequal(st->mode, L"restorepoint") && !(g_app.options & ZI_OPT_RESTORE_POINT)) {
            zi_logf(L"[%d/%d] %s — пропущено (опция выключена)", i + 1, plan->count, st->title);
            continue;
        }

        g_app.cur_step = i + 1;
        zi_ui_progress(i + 1, plan->count);
        zi_ui_status(st->title);
        zi_log_blank();
        zi_logf(L"[%d/%d] %s", i + 1, plan->count, st->title);
        zi_log(L"----------------------------------------------------------------------");

        build_step_log_name((zi_step*)st, i, stepLog, MAX_PATH * 2);

        {
            int code = run_step(st, i);
            if (code != 0) {
                zi_logf(L"    Шаг завершён с кодом %d.", code);
                if (rc == 0) rc = 1;
            } else {
                zi_log(L"    Шаг завершён успешно.");
            }
        }
        refresh_findings();

        if (g_app.cancel) { cancelled = 1; break; }
    }

    refresh_findings();

    if (cancelled) {
        zi_log_blank();
        zi_log(L"!!! Выполнение прервано пользователем !!!");
    }
    log_system_summary();

    zi_write_report(plan_index, rc);
    zi_ui_progress(0, plan->count);
    zi_ui_done(plan_index, rc, cancelled);
    return rc;
}

/* --------------------------------------------------------------------------
 * Рабочий поток
 * ------------------------------------------------------------------------*/
static DWORD WINAPI worker_thread(LPVOID param)
{
    int plan = (int)(INT_PTR)param;
    zi_worker_run(plan);
    InterlockedExchange(&g_app.running, 0);
    return 0;
}

int zi_start_plan(int plan_index)
{
    HANDLE h;
    if (!zi_get_plan(plan_index)) return 0;
    if (InterlockedCompareExchange(&g_app.running, 1, 0) != 0) return 0;
    InterlockedExchange(&g_app.cancel, 0);
    g_app.cur_plan = plan_index;
    h = CreateThread(0, 0, worker_thread, (LPVOID)(INT_PTR)plan_index, 0, 0);
    if (!h) { InterlockedExchange(&g_app.running, 0); return 0; }
    CloseHandle(h);
    return 1;
}
