/* ============================================================================
 *  ZI Office StartFix — графический интерфейс (USER32, без CRT)
 * ==========================================================================*/
#include "app.h"

#ifndef PBS_MARQUEE
#define PBS_MARQUEE 0x08
#endif
#ifndef PBM_SETMARQUEE
#define PBM_SETMARQUEE (WM_USER + 10)
#endif
#ifndef PBM_SETSTATE
#define PBM_SETSTATE (WM_USER + 16)
#endif
#ifndef PBST_ERROR
#define PBST_ERROR 0x0002
#endif
#ifndef PBST_NORMAL
#define PBST_NORMAL 0x0001
#endif
#ifndef PBS_SMOOTH
#define PBS_SMOOTH 0x01
#endif
#ifndef PBM_SETPOS
#define PBM_SETPOS (WM_USER + 2)
#endif

/* comctl32 объявляем вручную — CRT-независимая сборка не подключает commctrl.h */
typedef struct { DWORD dwSize; DWORD dwICC; } ZI_ICC;
__declspec(dllimport) BOOL WINAPI InitCommonControlsEx(const ZI_ICC* picc);

#define ID_BTN_DIAG       1001
#define ID_BTN_QUICK      1002
#define ID_BTN_FULL       1003
#define ID_BTN_START      1004
#define ID_BTN_SEARCH     1005
#define ID_BTN_EXPL       1006
#define ID_BTN_SFC        1007
#define ID_BTN_DISM       1008
#define ID_BTN_APPS       1009
#define ID_BTN_ROLLBACK   1010
#define ID_BTN_REPORTS    1011
#define ID_BTN_HELP       1012
#define ID_BTN_STOP       1013
#define ID_BTN_EXIT       1014
#define ID_BTN_REBOOT     1015
#define ID_BTN_LOGFILE    1016
#define ID_CHK_RESTORE    1020
#define ID_CHK_LAYOUT     1021
#define ID_CHK_EXPL       1022
#define ID_CHK_REBOOT     1023
#define ID_CHK_VERBOSE    1024

#define ID_LOG            1100
#define ID_STATUS         1101
#define ID_PROGRESS       1102
#define ID_INFO           1103
#define ID_TITLE          1104
#define ID_STATE          1105

static HWND g_hwnd, g_hLog, g_hStatus, g_hProgress, g_hInfo, g_hTitle, g_hState;
static HWND g_btns[32];
static int  g_btns_count;
static HWND g_checks[8];
static HINSTANCE g_hInst;
static HFONT g_font_ui, g_font_bold, g_font_mono, g_font_head;
static int g_dpi = 96;
static int g_running_ui = 0;
static int g_last_plan = -1;

/* ---- масштабирование под DPI ------------------------------------------- */
static int S(int px) { return MulDiv(px, g_dpi, 96); }

/* ---- шрифты ------------------------------------------------------------- */
static HFONT make_font(int pt, int bold, const wchar_t* face)
{
    return CreateFontW(-MulDiv(pt, g_dpi, 72), 0, 0, 0, bold ? FW_SEMIBOLD : FW_NORMAL,
                       0, 0, 0, DEFAULT_CHARSET, OUT_TT_PRECIS, CLIP_DEFAULT_PRECIS,
                       CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, face);
}

static void apply_font(HWND h, HFONT f)
{
    if (h && f) SendMessageW(h, WM_SETFONT, (WPARAM)f, TRUE);
}

/* ---- вывод -------------------------------------------------------------- */
void gui_ui_puts(const wchar_t* text)
{
    wchar_t* copy;
    if (!g_hwnd) return;
    copy = rt_wdup(text ? text : L"");
    if (!copy) return;
    if (!PostMessageW(g_hwnd, ZI_WM_LOG, 0, (LPARAM)copy)) rt_free(copy);
}

void gui_ui_puts_owned(wchar_t* text)
{
    gui_ui_puts(text);
    rt_free(text);
}

void gui_ui_status(const wchar_t* text)
{
    wchar_t* copy;
    if (!g_hwnd || !text) return;
    copy = rt_wdup(text);
    if (!copy) return;
    if (!PostMessageW(g_hwnd, ZI_WM_STATUS, 0, (LPARAM)copy)) rt_free(copy);
}

void gui_ui_progress(int step, int total)
{
    if (!g_hwnd) return;
    PostMessageW(g_hwnd, ZI_WM_PROGRESS, (WPARAM)step, (LPARAM)total);
}

void gui_ui_done(int plan_index, int exit_code, int cancelled)
{
    if (!g_hwnd) return;
    PostMessageW(g_hwnd, ZI_WM_DONE, (WPARAM)exit_code, (LPARAM)(cancelled ? 1 : 0));
    (void)plan_index;
}

void gui_ui_ready(void) { }

/* ---- вспомогательные ---------------------------------------------------- */
static void log_append(const wchar_t* text)
{
    int len;
    if (!g_hLog) return;
    len = GetWindowTextLengthW(g_hLog);
    if (len > 1200000) {
        SetWindowTextW(g_hLog, L"(журнал усечён, полный текст — в файле журнала)\r\n");
        len = GetWindowTextLengthW(g_hLog);
    }
    SendMessageW(g_hLog, EM_SETSEL, (WPARAM)len, (LPARAM)len);
    SendMessageW(g_hLog, EM_REPLACESEL, FALSE, (LPARAM)text);
    SendMessageW(g_hLog, EM_REPLACESEL, FALSE, (LPARAM)L"\r\n");
    SendMessageW(g_hLog, EM_SCROLLCARET, 0, 0);
}

static void set_window_title_state(void)
{
    wchar_t title[256];
    wchar_t base[160];
    rt_fmt(base, 160, L"%s — %s", ZI_APP_NAME, ZI_APP_TITLE);
    if (g_running_ui) rt_fmt(title, 256, L"%s  [выполняется...]", base);
    else rt_fmt(title, 256, L"%s", base);
    SetWindowTextW(g_hwnd, title);
}

static void update_buttons(void)
{
    int i;
    for (i = 0; i < g_btns_count; i++) {
        if (GetDlgCtrlID(g_btns[i]) == ID_BTN_STOP) continue;
        EnableWindow(g_btns[i], !g_running_ui);
    }
    for (i = 0; i < 8; i++) if (g_checks[i]) EnableWindow(g_checks[i], !g_running_ui);
}

static void read_options(void)
{
    g_app.options = 0;
    if (g_checks[0] && SendMessageW(g_checks[0], BM_GETCHECK, 0, 0) == BST_CHECKED)
        g_app.options |= ZI_OPT_RESTORE_POINT;
    if (g_checks[1] && SendMessageW(g_checks[1], BM_GETCHECK, 0, 0) == BST_CHECKED)
        g_app.options |= ZI_OPT_RESET_LAYOUT;
    if (g_checks[2] && SendMessageW(g_checks[2], BM_GETCHECK, 0, 0) == BST_CHECKED)
        g_app.options |= ZI_OPT_RESTART_EXPL;
    if (g_checks[3] && SendMessageW(g_checks[3], BM_GETCHECK, 0, 0) == BST_CHECKED)
        g_app.options |= ZI_OPT_REBOOT_AFTER;
    if (g_checks[4] && SendMessageW(g_checks[4], BM_GETCHECK, 0, 0) == BST_CHECKED)
        g_app.options |= ZI_OPT_VERBOSE;
}

static void show_help(void)
{
    const wchar_t* text =
        L"Что делает утилита\r\n"
        L"-------------------\r\n"
        L"Восстанавливает работу кнопки и меню «Пуск», поиска и панели задач\r\n"
        L"в Windows 10/11 и Windows Server 2012-2025.\r\n\r\n"
        L"Что делать, если меню «Пуск» не открывается:\r\n"
        L"1. Нажмите «ДИАГНОСТИКА» — будут найдены причины (ничего не меняется).\r\n"
        L"2. Нажмите «ПОЛНЫЙ РЕМОНТ» и дождитесь завершения.\r\n"
        L"3. Перезагрузите компьютер (или «Выйти и войти заново»).\r\n"
        L"4. Если не помогло — выполните «SFC» и «DISM», затем повторите шаг 2.\r\n\r\n"
        L"Важно:\r\n"
        L"• Все изменения реестра перед правкой сохраняются в папку Backup.\r\n"
        L"• Кнопка «Откат изменений» возвращает состояние из последней копии.\r\n"
        L"• Для работы нужны права администратора.\r\n"
        L"• Команды можно выполнять и без окна:\r\n"
        L"    StartFix-x64.exe /cli /diag\r\n"
        L"    StartFix-x64.exe /cli /full\r\n"
        L"    StartFix-x64.exe /cli /help\r\n\r\n"
        L"Папка с журналами и отчётами открывается кнопкой «Отчёты и журналы».";
    MessageBoxW(g_hwnd, text, L"Справка — ZI Office StartFix", MB_OK | MB_ICONINFORMATION);
}

static void start_plan_ui(const wchar_t* plan_id, const wchar_t* confirm_text)
{
    int idx = zi_find_plan(plan_id);
    const zi_plan* plan;
    if (idx < 0) return;
    plan = zi_get_plan(idx);
    if (g_running_ui) {
        MessageBoxW(g_hwnd, L"Операция уже выполняется. Нажмите «Стоп» и дождитесь остановки.",
                    L"Занято", MB_OK | MB_ICONINFORMATION);
        return;
    }
    if (!g_app.is_admin) {
        if (MessageBoxW(g_hwnd,
                L"Утилита запущена без прав администратора.\r\n"
                L"Часть операций будет недоступна.\r\n\r\n"
                L"Продолжить?",
                L"Нет прав администратора", MB_YESNO | MB_ICONWARNING) != IDYES)
            return;
    }
    if (plan->confirm && confirm_text) {
        if (MessageBoxW(g_hwnd, confirm_text, plan->title,
                        MB_YESNO | MB_ICONQUESTION) != IDYES) return;
    }
    if (rt_wequal(plan_id, L"rollback")) {
        const wchar_t* dir = zi_latest_backup_dir();
        if (!dir || !dir[0]) {
            MessageBoxW(g_hwnd, L"Резервные копии не найдены — откатывать нечего.",
                        L"Откат изменений", MB_OK | MB_ICONWARNING);
            return;
        }
        {
            wchar_t msg[MAX_PATH * 3];
            rt_fmt(msg, MAX_PATH * 3,
                   L"Будет выполнено восстановление из копии:\r\n%s\r\n\r\nПродолжить?", dir);
            if (MessageBoxW(g_hwnd, msg, L"Откат изменений", MB_YESNO | MB_ICONQUESTION) != IDYES)
                return;
        }
    }
    read_options();
    g_last_plan = idx;
    if (zi_start_plan(idx)) {
        g_running_ui = 1;
        update_buttons();
        set_window_title_state();
        if (GetDlgItem(g_hwnd, ID_PROGRESS)) {
            SendMessageW(g_hProgress, PBM_SETMARQUEE, TRUE, 0);
            SendMessageW(g_hProgress, PBM_SETSTATE, PBST_NORMAL, 0);
        }
        SetWindowTextW(g_hStatus, L"Выполняется...");
    } else {
        MessageBoxW(g_hwnd, L"Не удалось запустить операцию.", L"Ошибка", MB_OK | MB_ICONERROR);
    }
}

static void do_reboot(void)
{
    if (MessageBoxW(g_hwnd,
            L"Перезагрузить компьютер сейчас?\r\n\r\nСохраните несохранённые документы.",
            L"Перезагрузка", MB_YESNO | MB_ICONWARNING) != IDYES)
        return;
    rt_run_and_wait(L"\"%SystemRoot%\\System32\\shutdown.exe\" /r /t 15 /c "
                    L"\"Перезагрузка по требованию ZI Office StartFix\"",
                    g_app.work_dir, 1);
    MessageBoxW(g_hwnd, L"Перезагрузка запланирована через 15 секунд.\r\n"
                        L"Отменить: shutdown /a",
                L"Перезагрузка", MB_OK | MB_ICONINFORMATION);
}

/* ---- разметка ----------------------------------------------------------- */
static void layout_controls(HWND hwnd)
{
    RECT rc;
    int w, h, y, i;
    int pad = S(12);
    int logH, btnW1, btnW2;
    int button_rows[4];
    GetClientRect(hwnd, &rc);
    w = rc.right;
    h = rc.bottom;

    /* заголовок */
    MoveWindow(g_hTitle, pad, S(8), w - 2 * pad, S(24), TRUE);
    MoveWindow(g_hInfo, pad, S(32), w - 2 * pad, S(20), TRUE);
    y = S(58);

    /* кнопки: 4 строки по 3 кнопки + кнопки действий */
    btnW1 = (w - 2 * pad - 3 * S(8)) / 4;
    btnW2 = (w - 2 * pad - 3 * S(8)) / 4;
    button_rows[0] = y;
    MoveWindow(g_btns[0], pad + 0 * (btnW1 + S(8)), y, btnW1, S(34), TRUE);   /* полный ремонт */
    MoveWindow(g_btns[1], pad + 1 * (btnW1 + S(8)), y, btnW1, S(34), TRUE);   /* быстрый */
    MoveWindow(g_btns[2], pad + 2 * (btnW1 + S(8)), y, btnW1, S(34), TRUE);   /* диагностика */
    MoveWindow(g_btns[3], pad + 3 * (btnW1 + S(8)), y, btnW2, S(34), TRUE);   /* стоп */
    y += S(40);

    MoveWindow(g_btns[4], pad + 0 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);   /* меню Пуск */
    MoveWindow(g_btns[5], pad + 1 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);   /* поиск */
    MoveWindow(g_btns[6], pad + 2 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);   /* проводник */
    MoveWindow(g_btns[7], pad + 3 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);   /* sfc */
    y += S(34);

    MoveWindow(g_btns[8],  pad + 0 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);  /* dism */
    MoveWindow(g_btns[9],  pad + 1 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);  /* apps */
    MoveWindow(g_btns[10], pad + 2 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);  /* откат */
    MoveWindow(g_btns[11], pad + 3 * (btnW1 + S(8)), y, btnW1, S(28), TRUE);  /* отчёты */
    y += S(34);

    MoveWindow(g_btns[12], pad + 0 * (btnW1 + S(8)), y, btnW1, S(26), TRUE);  /* справка */
    MoveWindow(g_btns[13], pad + 1 * (btnW1 + S(8)), y, btnW1, S(26), TRUE);  /* журнал */
    MoveWindow(g_btns[14], pad + 2 * (btnW1 + S(8)), y, btnW1, S(26), TRUE);  /* перезагрузка */
    MoveWindow(g_btns[15], pad + 3 * (btnW1 + S(8)), y, btnW1, S(26), TRUE);  /* выход */
    y += S(32);

    /* опции */
    for (i = 0; i < 5; i++) {
        int cw = (w - 2 * pad) / 5;
        MoveWindow(g_checks[i], pad + i * cw, y, cw - S(4), S(22), TRUE);
    }
    y += S(28);

    /* нижняя панель */
    MoveWindow(g_hStatus, pad, h - S(52), w - 2 * pad, S(18), TRUE);
    MoveWindow(g_hProgress, pad, h - S(30), w - 2 * pad - S(120), S(18), TRUE);
    logH = h - y - S(60);
    if (logH < S(80)) logH = S(80);
    MoveWindow(g_hLog, pad, y, w - 2 * pad, logH, TRUE);
}

/* ---- оконная процедура -------------------------------------------------- */
static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp)
{
    switch (msg) {
    case WM_CREATE: {
        int i;
        struct { const wchar_t* text; int id; } mainBtns[] = {
            { L"ПОЛНЫЙ РЕМОНТ  (рекомендуется)", ID_BTN_FULL },
            { L"Быстрый ремонт", ID_BTN_QUICK },
            { L"Диагностика", ID_BTN_DIAG },
            { L"Стоп", ID_BTN_STOP },
            { L"Только меню «Пуск»", ID_BTN_START },
            { L"Поиск / Кортана", ID_BTN_SEARCH },
            { L"Перезапустить Проводник", ID_BTN_EXPL },
            { L"SFC (проверка файлов)", ID_BTN_SFC },
            { L"DISM (восстановление образа)", ID_BTN_DISM },
            { L"Все приложения (AppX)", ID_BTN_APPS },
            { L"Откат изменений", ID_BTN_ROLLBACK },
            { L"Отчёты и журналы", ID_BTN_REPORTS },
            { L"Справка", ID_BTN_HELP },
            { L"Открыть журнал", ID_BTN_LOGFILE },
            { L"Перезагрузить ПК", ID_BTN_REBOOT },
            { L"Выход", ID_BTN_EXIT }
        };
        struct { const wchar_t* text; int id; int checked; } checks[] = {
            { L"Точка восстановления", ID_CHK_RESTORE, 1 },
            { L"Сбросить раскладку меню «Пуск»", ID_CHK_LAYOUT, 1 },
            { L"Перезапустить Проводник", ID_CHK_EXPL, 1 },
            { L"Перезагрузить после ремонта", ID_CHK_REBOOT, 0 },
            { L"Подробный журнал команд", ID_CHK_VERBOSE, 0 }
        };

        g_hTitle = CreateWindowExW(0, L"STATIC", L"ZI Office StartFix — восстановление кнопки «Пуск»",
                                   WS_CHILD | WS_VISIBLE, 0, 0, 10, 10, hwnd, (HMENU)ID_TITLE, g_hInst, 0);
        g_hInfo = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE | SS_PATHELLIPSIS,
                                  0, 0, 10, 10, hwnd, (HMENU)ID_INFO, g_hInst, 0);
        g_btns_count = 0;
        for (i = 0; i < (int)(sizeof(mainBtns) / sizeof(mainBtns[0])); i++) {
            DWORD style = WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_PUSHBUTTON;
            if (mainBtns[i].id == ID_BTN_FULL) style |= BS_DEFPUSHBUTTON;
            g_btns[g_btns_count] = CreateWindowExW(0, L"BUTTON", mainBtns[i].text, style,
                                                   0, 0, 10, 10, hwnd,
                                                   (HMENU)(INT_PTR)mainBtns[i].id, g_hInst, 0);
            g_btns_count++;
        }
        for (i = 0; i < (int)(sizeof(checks) / sizeof(checks[0])); i++) {
            g_checks[i] = CreateWindowExW(0, L"BUTTON", checks[i].text,
                                          WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_AUTOCHECKBOX,
                                          0, 0, 10, 10, hwnd, (HMENU)(INT_PTR)checks[i].id, g_hInst, 0);
            SendMessageW(g_checks[i], BM_SETCHECK, checks[i].checked ? BST_CHECKED : BST_UNCHECKED, 0);
        }
        g_hLog = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
                                 WS_CHILD | WS_VISIBLE | WS_VSCROLL | WS_HSCROLL |
                                 ES_MULTILINE | ES_READONLY | ES_AUTOVSCROLL | ES_AUTOHSCROLL,
                                 0, 0, 10, 10, hwnd, (HMENU)ID_LOG, g_hInst, 0);
        g_hStatus = CreateWindowExW(0, L"STATIC", L"Готово к работе", WS_CHILD | WS_VISIBLE,
                                    0, 0, 10, 10, hwnd, (HMENU)ID_STATUS, g_hInst, 0);
        g_hState = g_hStatus;
        g_hProgress = CreateWindowExW(0, L"msctls_progress32", L"",
                                      WS_CHILD | WS_VISIBLE | PBS_SMOOTH,
                                      0, 0, 10, 10, hwnd, (HMENU)ID_PROGRESS, g_hInst, 0);

        /* шрифты */
        apply_font(g_hTitle, g_font_head);
        apply_font(g_hInfo, g_font_ui);
        apply_font(g_hLog, g_font_mono);
        apply_font(g_hStatus, g_font_ui);
        apply_font(g_hProgress, g_font_ui);
        for (i = 0; i < g_btns_count; i++) {
            int id = GetDlgCtrlID(g_btns[i]);
            apply_font(g_btns[i], id == ID_BTN_FULL ? g_font_bold : g_font_ui);
        }
        for (i = 0; i < 5; i++) apply_font(g_checks[i], g_font_ui);

        {
            wchar_t info[MAX_PATH * 4];
            rt_fmt(info, MAX_PATH * 4,
                   L"%s   •   %s   •   %s",
                   g_app.os_name,
                   g_app.is_admin ? L"права администратора" : L"БЕЗ прав администратора",
                   ZI_APP_NAME);
            SetWindowTextW(g_hInfo, info);
        }

        layout_controls(hwnd);
        /* иконка окна */
        {
            HICON ic = LoadIconW(g_hInst, (const wchar_t*)1);
            if (ic) {
                SendMessageW(hwnd, WM_SETICON, ICON_BIG, (LPARAM)ic);
                SendMessageW(hwnd, WM_SETICON, ICON_SMALL, (LPARAM)ic);
            }
        }
        SetWindowTextW(g_hLog, L"");
        return 0;
    }

    case WM_SIZE:
        layout_controls(hwnd);
        return 0;

    case WM_GETMINMAXINFO: {
        MINMAXINFO* mmi = (MINMAXINFO*)lp;
        mmi->ptMinTrackSize.x = S(820);
        mmi->ptMinTrackSize.y = S(600);
        return 0;
    }

    case WM_COMMAND: {
        int id = LOWORD(wp);
        switch (id) {
        case ID_BTN_DIAG:   start_plan_ui(L"diag", 0); break;
        case ID_BTN_QUICK:  start_plan_ui(L"quick", 0); break;
        case ID_BTN_FULL:
            start_plan_ui(L"full",
                L"Выполнить полный ремонт меню «Пуск»?\r\n\r\n"
                L"Будет создана точка восстановления, резервные копии параметров,\r\n"
                L"перерегистрированы компоненты меню «Пуск» и сброшена раскладка.\r\n"
                L"Операция может занять несколько минут.");
            break;
        case ID_BTN_START:  start_plan_ui(L"start", 0); break;
        case ID_BTN_SEARCH: start_plan_ui(L"search", 0); break;
        case ID_BTN_EXPL:   start_plan_ui(L"explorer", 0); break;
        case ID_BTN_APPS:
            start_plan_ui(L"apps",
                L"Будет выполнена перерегистрация ВСЕХ приложений Windows для текущего\r\n"
                L"пользователя. Операция может занять 10 и более минут и сопровождается\r\n"
                L"сообщениями об ошибках для отдельных приложений — это нормально.\r\n\r\n"
                L"Продолжить?");
            break;
        case ID_BTN_SFC:
            start_plan_ui(L"sfc",
                L"Запустить проверку целостности системных файлов (sfc /scannow)?\r\n\r\n"
                L"Операция может занять от 10 до 40 минут. Прерывать её не рекомендуется.");
            break;
        case ID_BTN_DISM:
            start_plan_ui(L"dism",
                L"Запустить восстановление образа системы (DISM /RestoreHealth)?\r\n\r\n"
                L"Требуется подключение к Интернету или источник установки.\r\n"
                L"Операция может занять 10-30 минут.");
            break;
        case ID_BTN_ROLLBACK: start_plan_ui(L"rollback", 0); break;
        case ID_BTN_REPORTS: zi_open_reports_folder(); break;
        case ID_BTN_LOGFILE:
            if (g_app.log_path[0]) rt_gui_open_path(g_app.log_path);
            break;
        case ID_BTN_HELP: show_help(); break;
        case ID_BTN_REBOOT: do_reboot(); break;
        case ID_BTN_STOP:
            if (g_running_ui) {
                InterlockedExchange(&g_app.cancel, 1);
                SetWindowTextW(g_hStatus, L"Останавливаю... подождите");
            }
            break;
        case ID_BTN_EXIT:
        case IDCANCEL:
            SendMessageW(hwnd, WM_CLOSE, 0, 0);
            break;
        default: break;
        }
        return 0;
    }

    case ZI_WM_LOG: {
        wchar_t* text = (wchar_t*)lp;
        if (text) {
            log_append(text);
            rt_free(text);
        }
        return 0;
    }

    case ZI_WM_STATUS: {
        wchar_t* text = (wchar_t*)lp;
        if (text) {
            wchar_t title[320];
            SetWindowTextW(g_hStatus, text);
            rt_fmt(title, 320, L"%s — %s", ZI_APP_NAME, text);
            SetWindowTextW(g_hwnd, title);
            rt_free(text);
        }
        return 0;
    }

    case ZI_WM_PROGRESS: {
        int step = (int)wp, total = (int)lp;
        if (total > 0 && step > 0) {
            int pos = MulDiv(step - 1, 100, total);
            SendMessageW(g_hProgress, PBM_SETMARQUEE, FALSE, 0);
            SendMessageW(g_hProgress, PBM_SETPOS, (WPARAM)pos, 0);
        } else {
            SendMessageW(g_hProgress, PBM_SETMARQUEE, FALSE, 0);
            SendMessageW(g_hProgress, PBM_SETPOS, 0, 0);
        }
        return 0;
    }

    case ZI_WM_DONE: {
        int code = (int)wp;
        g_running_ui = 0;
        update_buttons();
        set_window_title_state();
        SendMessageW(g_hProgress, PBM_SETMARQUEE, FALSE, 0);
        SendMessageW(g_hProgress, PBM_SETPOS, (WPARAM)(code == 0 ? 100 : 0), 0);
        SendMessageW(g_hProgress, PBM_SETSTATE, code == 0 ? PBST_NORMAL : PBST_NORMAL, 0);
        SetWindowTextW(g_hStatus, code == 0 ? L"Готово" : L"Завершено с замечаниями (см. журнал)");
        if (g_app.options & ZI_OPT_REBOOT_AFTER) {
            const zi_plan* plan = zi_get_plan(g_last_plan);
            if (plan && rt_wequal(plan->id, L"full")) {
                if (MessageBoxW(g_hwnd, L"Ремонт завершён.\r\n\r\nПерезагрузить компьютер сейчас?",
                                L"Полный ремонт завершён", MB_YESNO | MB_ICONQUESTION) == IDYES)
                    do_reboot();
            }
        }
        return 0;
    }

    case WM_CLOSE:
        if (g_running_ui) {
            if (MessageBoxW(hwnd,
                    L"Операция ещё выполняется. Прервать и закрыть утилиту?",
                    L"Подтверждение", MB_YESNO | MB_ICONWARNING) != IDYES)
                return 0;
            InterlockedExchange(&g_app.cancel, 1);
            rt_sleep_ms(600);
        }
        DestroyWindow(hwnd);
        return 0;

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;

    default:
        break;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

/* ---- точка входа интерфейса --------------------------------------------- */
int zi_gui_run(HINSTANCE hInst, int start_plan)
{
    WNDCLASSEXW wc;
    MSG msg;
    HDC hdc;
    ZI_ICC icc;

    g_hInst = hInst;

    hdc = GetDC(0);
    if (hdc) {
        g_dpi = GetDeviceCaps(hdc, LOGPIXELSX);
        ReleaseDC(0, hdc);
    }
    if (g_dpi < 96) g_dpi = 96;

    g_font_ui   = make_font(9, 0, L"Segoe UI");
    g_font_bold = make_font(9, 1, L"Segoe UI");
    g_font_head = make_font(12, 1, L"Segoe UI");
    g_font_mono = make_font(9, 0, L"Consolas");

    icc.dwSize = sizeof(icc);
    icc.dwICC = 0x00000004;      /* ICC_PROGRESS_CLASS */
    InitCommonControlsEx(&icc);

    memset(&wc, 0, sizeof(wc));
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = g_hInst;
    wc.hIcon = LoadIconW(g_hInst, (const wchar_t*)1);
    wc.hCursor = LoadCursorW(0, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    wc.lpszClassName = L"ZIStartFixWnd";
    wc.hIconSm = wc.hIcon;
    if (!RegisterClassExW(&wc)) return 1;

    g_hwnd = CreateWindowExW(0, L"ZIStartFixWnd",
                             ZI_APP_NAME L" — " ZI_APP_TITLE,
                             WS_OVERLAPPEDWINDOW,
                             CW_USEDEFAULT, CW_USEDEFAULT, S(980), S(720),
                             0, 0, g_hInst, 0);
    if (!g_hwnd) return 1;

    ShowWindow(g_hwnd, SW_SHOW);
    UpdateWindow(g_hwnd);

    gui_ui_ready();
    if (start_plan >= 0) {
        const zi_plan* p = zi_get_plan(start_plan);
        if (p) start_plan_ui(p->id, p->confirm ?
                L"Запустить выбранную операцию?" : 0);
    }

    while (GetMessageW(&msg, 0, 0, 0) > 0) {
        if (IsDialogMessageW(g_hwnd, &msg)) continue;
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return 0;
}
