/* ============================================================================
 *  ZI Office StartFix — общие объявления приложения
 *  Утилита восстановления меню «Пуск» (Windows 10/11, Server 2012-2025)
 * ==========================================================================*/
#ifndef ZI_APP_H
#define ZI_APP_H

#include "rt.h"

#define ZI_APP_NAME     L"ZI Office StartFix"
#define ZI_APP_SHORT    L"StartFix"
#define ZI_APP_TITLE    L"Восстановление кнопки «Пуск»"
#define ZI_APP_VERSION  L"1.0.0"
#define ZI_APP_BUILD    L"2026-09-23"

/* Сообщения рабочего потока в интерфейс */
#define ZI_WM_LOG       (WM_APP + 1)   /* LPARAM — wchar_t* (получатель освобождает) */
#define ZI_WM_STATUS    (WM_APP + 2)   /* LPARAM — wchar_t* */
#define ZI_WM_PROGRESS  (WM_APP + 3)   /* wParam — шаг, lParam — всего */
#define ZI_WM_DONE      (WM_APP + 4)   /* wParam — код возврата, lParam — 1 если отменено */

/* Опции выполнения (битовая маска) */
#define ZI_OPT_RESTORE_POINT  0x0001
#define ZI_OPT_RESET_LAYOUT   0x0002
#define ZI_OPT_RESTART_EXPL   0x0004
#define ZI_OPT_REBOOT_AFTER   0x0008
#define ZI_OPT_VERBOSE        0x0010
#define ZI_OPT_NO_EXPLORER    0x0020

/* Один шаг плана = один запуск PowerShell-скрипта */
typedef struct {
    const wchar_t* mode;    /* режим скрипта zi-repair.ps1 */
    const wchar_t* title;   /* описание для журнала и интерфейса */
    int needs_backup;       /* подготовить/использовать папку резервных копий */
    int heavy;              /* длительная операция (SFC/DISM) */
    int layout_option;      /* выполнять только при включённой опции сброса раскладки */
    int expl_option;        /* выполнять только при включённой опции перезапуска Проводника */
} zi_step;

/* План = последовательность шагов */
typedef struct {
    const wchar_t* id;
    const wchar_t* title;
    const wchar_t* hint;
    const zi_step* steps;
    int   count;
    int   confirm;          /* требуется подтверждение пользователя */
    int   diag;             /* это диагностика */
} zi_plan;

/* Состояние приложения */
typedef struct {
    wchar_t work_dir[MAX_PATH * 2];      /* %ProgramData%\ZI-StartFix */
    wchar_t script_dir[MAX_PATH * 2];
    wchar_t backup_root[MAX_PATH * 2];
    wchar_t backup_dir[MAX_PATH * 2];
    wchar_t report_dir[MAX_PATH * 2];
    wchar_t log_path[MAX_PATH * 2];      /* журнал сессии */
    wchar_t findings_path[MAX_PATH * 2];
    wchar_t raw_path[MAX_PATH * 2];      /* «сырой» вывод консоли */
    wchar_t stamp[32];

    rt_osver os;
    int   is_admin;
    int   is_server;
    int   is_win11;
    int   is_win10;
    int   supported;
    wchar_t os_name[160];
    wchar_t pc_name[64];
    wchar_t user_name[80];

    rt_sb log;                           /* весь журнал сессии */
    rt_sb findings;                      /* строки: важность<TAB>код<TAB>текст */
    int   n_crit, n_warn, n_info, n_fixed;

    volatile LONG cancel;
    volatile LONG running;
    int   options;
    int   cur_plan;
    int   cur_step;
    int   total_steps;
    int   last_exit;

    rt_file session_log;                 /* файл журнала сессии */
    int     session_log_open;
} zi_app;

extern zi_app g_app;

/* --- журнал (repair.c) ---------------------------------------------------- */
void zi_log(const wchar_t* line);
void zi_logf(const wchar_t* fmt, ...);
void zi_log_blank(void);

/* --- интерфейс ------------------------------------------------------------- */
extern int g_ui_mode;                          /* 0 — окно, 1 — консоль */

void zi_ui_puts(const wchar_t* text);          /* вывод строки в окно/консоль */
void zi_ui_puts_owned(wchar_t* text_owned);    /* то же, но владение передаётся */
void zi_ui_status(const wchar_t* text);
void zi_ui_progress(int step, int total);
void zi_ui_done(int plan_index, int exit_code, int cancelled);
void zi_ui_ready(void);                        /* интерфейс готов к работе */

/* реализация в gui.c */
void gui_ui_puts(const wchar_t* text);
void gui_ui_puts_owned(wchar_t* text_owned);
void gui_ui_status(const wchar_t* text);
void gui_ui_progress(int step, int total);
void gui_ui_done(int plan_index, int exit_code, int cancelled);
void gui_ui_ready(void);

/* реализация в cli.c */
void cli_ui_puts(const wchar_t* text);
void cli_ui_puts_owned(wchar_t* text_owned);
void cli_ui_status(const wchar_t* text);
void cli_ui_progress(int step, int total);
void cli_ui_done(int plan_index, int exit_code, int cancelled);
void cli_ui_ready(void);

/* --- приложение (repair.c) ------------------------------------------------ */
int   zi_app_init(void);
void  zi_app_shutdown(void);
int   zi_start_plan(int plan_index);
int   zi_worker_run(int plan_index);           /* выполняется в рабочем потоке */
int   zi_write_report(int plan_index, int rc);
int   zi_plan_count(void);
const zi_plan* zi_get_plan(int index);
int   zi_find_plan(const wchar_t* id);
int   zi_rollback_available(void);
const wchar_t* zi_latest_backup_dir(void);
void  zi_open_reports_folder(void);

/* --- интерфейсы ----------------------------------------------------------- */
int   zi_gui_run(HINSTANCE hInst, int start_plan);
int   zi_cli_run(int argc, wchar_t** argv);

#endif /* ZI_APP_H */
