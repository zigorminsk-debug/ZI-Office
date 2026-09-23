/* ============================================================================
 *  ZI Office StartFix — автономная среда выполнения (без CRT)
 *  Все функции реализованы через Win32 API: exe не требует ни UCRT,
 *  ни Visual C++ Redistributable, ни .NET — работает на Windows 7/2008 R2,
 *  8/2012, 8.1/2012 R2, 10, 11, Server 2016-2025.
 * ==========================================================================*/
#ifndef ZI_RT_H
#define ZI_RT_H

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif

#include <windows.h>
#include <shellapi.h>
#include <stdarg.h>
#include <stddef.h>

typedef unsigned char  u8;
typedef unsigned short u16;
typedef unsigned int   u32;
typedef unsigned long long u64;
typedef signed char    i8;
typedef short          i16;
typedef int            i32;
typedef long long      i64;

/* ---- память -------------------------------------------------------------- */
void* rt_alloc(size_t bytes);
void* rt_realloc(void* p, size_t bytes);
void* rt_calloc(size_t count, size_t bytes);
void  rt_free(void* p);

/* ---- строки (UTF-16) ----------------------------------------------------- */
size_t       rt_wlen(const wchar_t* s);
wchar_t*     rt_wcpy(wchar_t* dst, const wchar_t* src);
wchar_t*     rt_wncpy(wchar_t* dst, const wchar_t* src, size_t cap);
wchar_t*     rt_wcat(wchar_t* dst, const wchar_t* src);
wchar_t*     rt_wcat_n(wchar_t* dst, const wchar_t* src, size_t cap);
int          rt_wcmp(const wchar_t* a, const wchar_t* b);
int          rt_wcmp_ci(const wchar_t* a, const wchar_t* b);   /* ASCII+кириллица */
int          rt_wequal(const wchar_t* a, const wchar_t* b);
int          rt_wequal_ci(const wchar_t* a, const wchar_t* b);
int          rt_wstarts_ci(const wchar_t* s, const wchar_t* prefix);
const wchar_t* rt_wfind(const wchar_t* hay, const wchar_t* needle);
int          rt_wcontains_ci(const wchar_t* hay, const wchar_t* needle);
const wchar_t* rt_wfind_ch(const wchar_t* s, wchar_t ch);
wchar_t*     rt_wtrim(wchar_t* s);
wchar_t*     rt_wdup(const wchar_t* s);
void         rt_wlwr_ascii(wchar_t* s);
int          rt_starts_num(const wchar_t* s);

/* ---- форматирование ------------------------------------------------------ */
int  rt_fmt(wchar_t* buf, size_t cap, const wchar_t* fmt, ...);
int  rt_fmtv(wchar_t* buf, size_t cap, const wchar_t* fmt, va_list ap);

/* ---- динамический буфер строк ------------------------------------------- */
typedef struct {
    wchar_t* p;
    size_t   len;
    size_t   cap;
    int      oom;
} rt_sb;

void rt_sb_init(rt_sb* b, size_t initial_cap);
void rt_sb_free(rt_sb* b);
void rt_sb_reset(rt_sb* b);
void rt_sb_need(rt_sb* b, size_t extra);
void rt_sb_puts(rt_sb* b, const wchar_t* s);
void rt_sb_putn(rt_sb* b, const wchar_t* s, size_t n);
void rt_sb_putc(rt_sb* b, wchar_t c);
void rt_sb_printf(rt_sb* b, const wchar_t* fmt, ...);
void rt_sb_put_utf8(rt_sb* b, const char* s, int len);   /* len<0 => до NUL */

/* ---- кодировки ----------------------------------------------------------- */
int   rt_utf8_to_wide(const char* s, int len, wchar_t* out, int cap);
int   rt_wide_to_utf8(const wchar_t* s, int len, char* out, int cap);
int   rt_mb_to_wide(UINT cp, const char* s, int len, wchar_t* out, int cap);
int   rt_wide_to_mb(UINT cp, const wchar_t* s, int len, char* out, int cap);
UINT  rt_oem_cp(void);
UINT  rt_ansi_cp(void);

/* ---- файлы --------------------------------------------------------------- */
typedef struct { HANDLE h; int utf8_text; } rt_file;

int   rt_file_create(rt_file* f, const wchar_t* path);          /* создать/перезаписать */
int   rt_file_open_append(rt_file* f, const wchar_t* path);     /* дозапись */
int   rt_file_open_read(rt_file* f, const wchar_t* path);
void  rt_file_close(rt_file* f);
int   rt_file_write_raw(rt_file* f, const void* data, DWORD bytes);
int   rt_file_write(rt_file* f, const wchar_t* text);           /* UTF-8/UTF-16 по режиму */
int   rt_file_writeln(rt_file* f, const wchar_t* text);
int   rt_file_put_bom(rt_file* f);
u64   rt_file_size_handle(HANDLE h);
u64   rt_file_size(const wchar_t* path);
int   rt_file_exists(const wchar_t* path);
int   rt_dir_exists(const wchar_t* path);
int   rt_dir_create(const wchar_t* path);                       /* рекурсивно */
int   rt_dir_create_for_file(const wchar_t* path);
int   rt_file_nocase_append(const wchar_t* path, const wchar_t* text);

/* ---- пути ---------------------------------------------------------------- */
void  rt_path_join(wchar_t* dst, size_t cap, const wchar_t* dir, const wchar_t* name);
void  rt_path_dirname(wchar_t* path);
const wchar_t* rt_path_basename(const wchar_t* path);
int   rt_path_is_abs(const wchar_t* path);
void  rt_module_dir(wchar_t* out, size_t cap);
void  rt_module_path(wchar_t* out, size_t cap);
int   rt_env(const wchar_t* name, wchar_t* out, size_t cap);
int   rt_env_expand(const wchar_t* in, wchar_t* out, size_t cap);

/* ---- реестр -------------------------------------------------------------- */
int   rt_reg_get_str(HKEY root, const wchar_t* sub, const wchar_t* name, wchar_t* out, DWORD cch);
int   rt_reg_get_dword(HKEY root, const wchar_t* sub, const wchar_t* name, DWORD* out);
int   rt_reg_key_exists(HKEY root, const wchar_t* sub);
int   rt_reg_value_exists(HKEY root, const wchar_t* sub, const wchar_t* name);
int   rt_reg_get_multi_str_first(HKEY root, const wchar_t* sub, const wchar_t* name, wchar_t* out, DWORD cch);

/* ---- процессы и каналы --------------------------------------------------- */
typedef struct {
    HANDLE hProcess;
    HANDLE hThread;
    DWORD  pid;
    HANDLE hOutRead;
    HANDLE hErrRead;
    HANDLE hInWrite;
} rt_proc;

int   rt_proc_start(rt_proc* p, const wchar_t* cmdline, const wchar_t* cwd);
int   rt_proc_running(rt_proc* p);
DWORD rt_proc_exit_code(rt_proc* p);
void  rt_proc_kill(rt_proc* p);
int   rt_proc_wait(rt_proc* p, DWORD timeout_ms);   /* 1 — завершился */
void  rt_proc_close(rt_proc* p);
int   rt_pipe_read(HANDLE h, char* buf, DWORD cap, DWORD* got);  /* не блокирует */

/* ---- построчное чтение журнала (инкрементальное, UTF-8/UTF-16/OEM) ------ */
typedef struct {
    void*   hFile;                 /* HANDLE файла */
    wchar_t path[520];
    unsigned long long offset;
    int     opened;
    int     checked_bom;
    int     utf8;
    unsigned char  raw[8192];
    unsigned char  pend[8];
    int     pend_len;
    wchar_t line[8192];
    int     line_len;
} rt_logtail;

void  rt_logtail_open(rt_logtail* t, const wchar_t* path);
void  rt_logtail_poll(rt_logtail* t, void (*on_line)(const wchar_t* line, void* ud), void* ud);
void  rt_logtail_flush(rt_logtail* t, void (*on_line)(const wchar_t* line, void* ud), void* ud);
void  rt_logtail_close(rt_logtail* t);

/* ---- система ------------------------------------------------------------- */
typedef struct {
    DWORD  major, minor, build, revision;
    DWORD  product_type;     /* 1 — рабочая станция, 2 — контроллер домена, 3 — сервер */
    wchar_t csd[64];
} rt_osver;

int   rt_os_version(rt_osver* v);
int   rt_is_admin(void);
int   rt_console_session(void);
void  rt_computer_name(wchar_t* out, size_t cap);
void  rt_user_name(wchar_t* out, size_t cap);
void  rt_now_str(wchar_t* out, size_t cap);      /* 2026-09-23 07:45:12 */
void  rt_now_stamp(wchar_t* out, size_t cap);    /* 20260923-074512 */
void  rt_sleep_ms(DWORD ms);
/* Путь к 64-битному PowerShell: из 32-битного процесса берётся Sysnative,
   иначе команды AppX (Get-AppxPackage) недоступны. */
int   rt_powershell_path(wchar_t* out, size_t cap);
int   rt_is_wow64(void);
void  rt_gui_open_path(const wchar_t* path);     /* открыть файл/папку оболочкой */
int   rt_run_and_wait(const wchar_t* cmdline, const wchar_t* cwd, int hidden);

#endif /* ZI_RT_H */
