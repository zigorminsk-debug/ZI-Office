/* ============================================================================
 *  Тесты ядра утилиты: инициализация, распаковка скриптов, сборка команд
 *  PowerShell и формирование HTML-отчёта.
 *
 *  src/repair.c компилируется с -Dstatic= , поэтому его внутренние функции
 *  доступны как обычные — это позволяет проверить именно то, что выполняется
 *  на Windows.
 * ==========================================================================*/
#include <stdio.h>
#include <locale.h>
#include <string.h>
#include <stdlib.h>

#include "../src/rt.h"
#include "../src/app.h"

#ifndef ZI_TESTING
#error "тесты должны собираться с -DZI_TESTING"
#endif

/* --- заглушки интерфейса (в тесте окна и консоли нет) --------------------- */
int  g_ui_mode = 1;
void zi_ui_puts(const wchar_t* t) { (void)t; }
void zi_ui_puts_owned(wchar_t* t) { rt_free(t); }
void zi_ui_status(const wchar_t* t) { (void)t; }
void zi_ui_progress(int a, int b) { (void)a; (void)b; }
void zi_ui_done(int a, int b, int c) { (void)a; (void)b; (void)c; }
void zi_ui_ready(void) { }

/* --- внутренние функции repair.c (доступны через тестовые «крючки») ------- */
void zi_test_build_cmd(rt_sb* sb, const wchar_t* mode, const wchar_t* step_log,
                       int use_backup, int restore_point, int reset_layout, int no_explorer);
void zi_test_build_cmd_iex(rt_sb* sb, const wchar_t* mode, const wchar_t* step_log,
                           int use_backup, int restore_point, int reset_layout, int no_explorer);
void zi_test_refresh_findings(void);

#define build_ps_command       zi_test_build_cmd
#define build_ps_command_iex   zi_test_build_cmd_iex
#define refresh_findings       zi_test_refresh_findings

static int g_pass = 0, g_fail = 0;

#define CHECK(cond, what) do { \
    if (cond) { g_pass++; } \
    else { g_fail++; printf("  ПРОВАЛ: %ls (строка %d)\n", what, __LINE__); } \
} while (0)

static int contains(const wchar_t* hay, const wchar_t* needle)
{
    return rt_wfind(hay, needle) != 0;
}

/* --- 1. инициализация и распаковка встроенных скриптов ------------------- */
static void test_init_and_extract(void)
{
    wchar_t path[1024];
    rt_file f;
    static char buf[200000];
    DWORD got = 0;
    int ok;

    printf("Инициализация утилиты:\n");
    ok = zi_app_init();
    CHECK(ok, L"zi_app_init выполнилась успешно");
    CHECK(rt_wlen(g_app.work_dir) > 0, L"рабочий каталог задан");
    CHECK(rt_dir_exists(g_app.work_dir), L"рабочий каталог создан на диске");
    CHECK(rt_dir_exists(g_app.script_dir), L"каталог скриптов создан");
    CHECK(rt_dir_exists(g_app.backup_dir), L"каталог резервных копий создан");
    CHECK(rt_dir_exists(g_app.report_dir), L"каталог отчётов создан");
    CHECK(g_app.session_log_open, L"журнал сессии открыт");
    CHECK(rt_file_size(g_app.log_path) > 0, L"журнал сессии не пуст");
    CHECK(rt_file_exists(g_app.findings_path), L"файл заключений подготовлен");

    /* оба PowerShell-скрипта должны быть на диске с BOM и полным содержимым */
    rt_path_join(path, 1024, g_app.script_dir, L"zi-repair.ps1");
    CHECK(rt_file_exists(path), L"zi-repair.ps1 распакован из EXE");
    CHECK(rt_file_size(path) > 40000, L"zi-repair.ps1 не пуст (более 40 КБ)");

    rt_file_open_read(&f, path);
    ReadFile(f.h, buf, sizeof(buf) - 1, &got, 0);
    rt_file_close(&f);
    buf[got] = 0;
    CHECK(got > 3 && (unsigned char)buf[0] == 0xEF && (unsigned char)buf[1] == 0xBB,
          L"скрипт записан в UTF-8 с BOM (требование PowerShell 5.1)");
    CHECK(strstr(buf, "function Invoke-ZiFixStart") != NULL, L"в скрипте есть режим fix-start");
    CHECK(strstr(buf, "Get-AppxPackage") != NULL, L"в скрипте есть работа с AppX");

    rt_path_join(path, 1024, g_app.script_dir, L"zi-common.ps1");
    CHECK(rt_file_exists(path), L"zi-common.ps1 распакован из EXE");

    /* внутри встроенного скрипта не должно быть двойного BOM */
    rt_file_open_read(&f, path);
    got = 0;
    ReadFile(f.h, buf, sizeof(buf) - 1, &got, 0);
    rt_file_close(&f);
    CHECK(!(got > 6 && (unsigned char)buf[0] == 0xEF && (unsigned char)buf[3] == 0xEF),
          L"нет двойного BOM");
}

/* --- 2. планы ремонта ----------------------------------------------------- */
static void test_plans(void)
{
    int n = zi_plan_count();
    int i;

    printf("Планы ремонта:\n");
    CHECK(n == 10, L"десять режимов работы");
    CHECK(zi_find_plan(L"diag") >= 0, L"режим diag найден");
    CHECK(zi_find_plan(L"full") >= 0, L"режим full найден");
    CHECK(zi_find_plan(L"rollback") >= 0, L"режим rollback найден");
    CHECK(zi_find_plan(L"DIAG") == zi_find_plan(L"diag"), L"поиск режима без учёта регистра");
    CHECK(zi_find_plan(L"нет-такого") == -1, L"неизвестный режим не найден");
    for (i = 0; i < n; i++) {
        const zi_plan* p = zi_get_plan(i);
        CHECK(p && p->id && p->title && p->count > 0,
              L"у каждого режима есть id, заголовок и шаги");
    }
    {
        const zi_plan* full = zi_get_plan(zi_find_plan(L"full"));
        CHECK(full->confirm == 1, L"полный ремонт требует подтверждения");
        CHECK(full->steps[0].mode && rt_wequal(full->steps[0].mode, L"diag"),
              L"полный ремонт начинается с диагностики");
    }
    {
        const zi_plan* diag = zi_get_plan(zi_find_plan(L"diag"));
        CHECK(diag->diag == 1 && diag->count == 1, L"диагностика — один шаг без изменений");
    }
}

/* --- 3. сборка команды PowerShell ---------------------------------------- */
static void test_command_building(void)
{
    rt_sb sb;
    wchar_t log[1024];

    printf("Сборка команды PowerShell:\n");
    rt_path_join(log, 1024, g_app.report_dir, L"01-diag.log");

    rt_sb_init(&sb, 4096);
    build_ps_command(&sb, L"diag", log, 0, 0, 0, 0);
    CHECK(contains(sb.p, L"powershell.exe"), L"вызывается powershell.exe");
    CHECK(contains(sb.p, L"-NoProfile"), L"есть -NoProfile");
    CHECK(contains(sb.p, L"-ExecutionPolicy Bypass"), L"политика выполнения обходится");
    CHECK(contains(sb.p, L"-File \""), L"скрипт передаётся через -File");
    CHECK(contains(sb.p, L"zi-repair.ps1"), L"указан файл скрипта");
    CHECK(contains(sb.p, L"-Mode diag"), L"режим передан");
    CHECK(contains(sb.p, L"-Log \""), L"файл журнала передан");
    CHECK(contains(sb.p, L"-Findings \""), L"файл заключений передан");
    CHECK(!contains(sb.p, L"-BackupDir"), L"без резервной копии параметр не добавляется");
    CHECK(!contains(sb.p, L"-RestorePoint"), L"точка восстановления не запрошена");
    /* кавычки должны быть сбалансированы — иначе команда развалится */
    {
        int q = 0;
        const wchar_t* p;
        for (p = sb.p; *p; p++) if (*p == L'"') q++;
        CHECK((q % 2) == 0, L"кавычки в команде сбалансированы");
    }
    rt_sb_reset(&sb);

    rt_sb_init(&sb, 4096);
    build_ps_command(&sb, L"backup", log, 1, 1, 1, 0);
    CHECK(contains(sb.p, L"-BackupDir \""), L"каталог резервных копий добавлен");
    CHECK(contains(sb.p, L"-RestorePoint"), L"запрошена точка восстановления");
    CHECK(contains(sb.p, L"-ResetLayout"), L"запрошен сброс раскладки");
    CHECK(contains(sb.p, L"-Mode backup"), L"режим резервного копирования");
    rt_sb_reset(&sb);

    /* запасной вариант без файла скрипта (обход политики выполнения) */
    rt_sb_init(&sb, 4096);
    build_ps_command_iex(&sb, L"fix-start", log, 0, 0, 0, 0);
    CHECK(contains(sb.p, L"-Command"), L"резервный запуск через -Command");
    CHECK(contains(sb.p, L"scriptblock"), L"содержимое скрипта выполняется из памяти");
    CHECK(contains(sb.p, L"-Mode fix-start"), L"режим передан и в резервном варианте");
    rt_sb_free(&sb);

    /* путь с пробелами должен быть в кавычках */
    rt_sb_init(&sb, 4096);
    build_ps_command(&sb, L"diag", L"C:\\Program Files\\ZI Office\\step 01.log", 0, 0, 0, 0);
    CHECK(contains(sb.p, L"-Log \"C:\\Program Files\\ZI Office\\step 01.log\""),
          L"путь с пробелами взят в кавычки");
    rt_sb_free(&sb);
}

/* --- 4. разбор заключений и HTML-отчёт ----------------------------------- */
static void test_findings_and_report(void)
{
    rt_file f;
    int rc;
    wchar_t report[1024];

    printf("Заключения и отчёт:\n");

    rt_file_create(&f, g_app.findings_path);
    rt_file_put_bom(&f);
    rt_file_writeln(&f, L"CRIT\tIFEO_DEBUGGER\tОбнаружены перехваты запуска процессов");
    rt_file_writeln(&f, L"WARN\tTHIRDPARTY_START\tУстановлены сторонние программы меню «Пуск»");
    rt_file_writeln(&f, L"INFO\tDIAG_NEXT\tРекомендуется выполнить полный ремонт");
    rt_file_writeln(&f, L"OK\tSVC_DONE\tВосстановлены типы запуска служб: 3");
    rt_file_writeln(&f, L"OK\tSTART_DONE\tКомпоненты меню «Пуск» перерегистрированы");
    rt_file_close(&f);

    refresh_findings();
    CHECK(g_app.n_crit == 1, L"найдено одно критичное заключение");
    CHECK(g_app.n_warn == 1, L"найдено одно предупреждение");
    CHECK(g_app.n_info == 1, L"найдено одно справочное замечание");
    CHECK(g_app.n_fixed == 2, L"найдено два исправления");

    rc = zi_write_report(zi_find_plan(L"diag"), 0);
    CHECK(rc == 1, L"отчёт сформирован");

    {
        static char html[400000];
        DWORD got = 0;
        rt_path_join(report, 1024, g_app.report_dir, L"");
        /* ищем файл отчёта в каталоге отчётов */
        wchar_t mask[1024];
        rt_path_join(mask, 1024, g_app.report_dir, L"report-*.html");
        {
            WIN32_FIND_DATAW fd;
            HANDLE h = FindFirstFileW(mask, &fd);
            CHECK(h != INVALID_HANDLE_VALUE, L"файл отчёта найден в каталоге");
            if (h != INVALID_HANDLE_VALUE) {
                rt_path_join(report, 1024, g_app.report_dir, fd.cFileName);
                rt_file_open_read(&f, report);
                ReadFile(f.h, html, sizeof(html) - 1, &got, 0);
                rt_file_close(&f);
                html[got] = 0;
            }
        }
        CHECK(got > 2000, L"отчёт содержит данные");
        CHECK(strstr(html, "<!DOCTYPE html>") != NULL, L"это HTML-документ");
        CHECK(strstr(html, "Cyrillic") == NULL, L"без служебного мусора");
        CHECK(strstr(html, "критичных проблем") != NULL, L"есть сводка по критичным проблемам");
        CHECK(strstr(html, ">КРИТИЧНО</td>") != NULL, L"уровень выводится отдельной колонкой");
        CHECK(strstr(html, "Обнаружены перехваты запуска процессов") != NULL, L"текст заключения без уровня");
        CHECK(strstr(html, "[IFEO_DEBUGGER]</span>") != NULL, L"код заключения выводится отдельно");
        CHECK(strstr(html, "CRIT") == NULL, L"служебный уровень CRIT не попадает в текст");
        CHECK(strstr(html, "критичных проблем</span></div>\r\n<div class=\"card\"><b>1") != NULL,
              L"в сводке одна критичная проблема");
        {
            /* в сводке «исправлено» должно быть ровно 2, а не накопленная сумма */
            const char* p = strstr(html, "<span>исправлено</span>");
            CHECK(p != NULL, L"карточка «исправлено» присутствует");
            if (p) {
                const char* digit = p;
                while (digit > html && !(digit[-1] == '>' && digit[-2] == 'b' && digit[-3] == '<')) digit--;
                CHECK(*digit >= '0' && *digit <= '9',
                      L"в карточке «исправлено» есть число");
                CHECK(atoi(digit) == 2,
                      L"в карточке «исправлено» ровно 2 (счётчик не накапливается)");
            }
        }
        CHECK(strstr(html, "Обнаружены перехваты запуска процессов") != NULL, L"заключение попало в отчёт");
        CHECK(strstr(html, "РАСШИРЕННЫЙ") == NULL, L"нет посторонних маркеров");
    }
}

/* --- 5. ход выполнения и журнал ------------------------------------------- */
static void test_log_and_options(void)
{
    rt_sb before;
    int rc;

    printf("Выполнение и журнал:\n");
    rt_sb_init(&before, 1024);
    rt_sb_puts(&before, g_app.log.p);

    g_app.options = ZI_OPT_RESET_LAYOUT;
    rc = zi_worker_run(zi_find_plan(L"diag"));      /* диагностика безопасна: только чтение */
    CHECK(rc == 0 || rc == 1, L"диагностический проход завершается без сбоя ядра");
    CHECK(g_app.log.len > before.len, L"журнал пополнился");
    CHECK(rt_wcontains_ci(g_app.log.p, L"ДИАГНОСТИКА"), L"в журнале отмечен запуск диагностики");
    CHECK(rt_wcontains_ci(g_app.log.p, L"Отчёт сохранён"), L"записана строка о сохранении отчёта");
    CHECK(g_app.n_fixed >= 0 && g_app.n_fixed <= 5, L"счётчики не накапливаются между проходами");
    CHECK(g_app.cur_plan == zi_find_plan(L"diag"), L"текущий план зафиксирован");
    rt_sb_free(&before);
}

int main(void)
{
    setlocale(LC_ALL, "C.UTF-8");
    /* все рабочие файлы теста — во временном каталоге, а не рядом с исходниками */
    setenv("HOME", "/tmp/zi-tests-home", 1);
    setenv("TMPDIR", "/tmp/zi-tests-tmp", 1);
    printf("=== Тесты ядра ZI Office StartFix ===\n\n");
    test_init_and_extract();
    test_plans();
    test_command_building();
    test_findings_and_report();
    test_log_and_options();
    zi_app_shutdown();
    printf("\n=== Итог: успешно %d, провалов %d ===\n", g_pass, g_fail);
    return g_fail == 0 ? 0 : 1;
}
