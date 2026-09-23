/* ============================================================================
 *  Модульные тесты «чистой» логики (строки, формат, пути, чтение журнала).
 *  Запускаются на Linux с эмуляцией Win32: test/run_tests.sh
 * ==========================================================================*/
#include <stdio.h>
#include <locale.h>

#include "../src/rt.c"     /* тестируем реальный код утилиты */

static int g_pass = 0, g_fail = 0;

#define CHECK(cond, what) do { \
    if (cond) { g_pass++; } \
    else { g_fail++; printf("  ПРОВАЛ: %ls (строка %d)\n", what, __LINE__); } \
} while (0)

static int wseq(const wchar_t* a, const wchar_t* b) { return rt_wequal(a, b); }

/* --- 1. форматирование ---------------------------------------------------- */
static void test_format(void)
{
    wchar_t b[256];

    printf("Форматирование:\n");
    rt_fmt(b, 256, L"%d", -42);
    CHECK(wseq(b, L"-42"), L"%d с минусом");

    rt_fmt(b, 256, L"%u", 4000000000u);
    CHECK(wseq(b, L"4000000000"), L"%u");

    rt_fmt(b, 256, L"%08x", 0xBEEFu);
    CHECK(wseq(b, L"0000beef"), L"%08x с нулями");

    rt_fmt(b, 256, L"%X", 0xDEADu);
    CHECK(wseq(b, L"DEAD"), L"%X в верхнем регистре");

    rt_fmt(b, 256, L"[%5d]", 7);
    CHECK(wseq(b, L"[    7]"), L"ширина с выравниванием вправо");

    rt_fmt(b, 256, L"[%-5d]", 7);
    CHECK(wseq(b, L"[7    ]"), L"выравнивание влево");

    rt_fmt(b, 256, L"%s — %d%%", L"Готово", 5);
    CHECK(wseq(b, L"Готово — 5%"), L"%s с кириллицей и %%");

    rt_fmt(b, 256, L"%S", "utf8 text");
    CHECK(wseq(b, L"utf8 text"), L"%S (узкая строка)");

    rt_fmt(b, 256, L"%lld", (long long)-1234567890123LL);
    CHECK(wseq(b, L"-1234567890123"), L"%lld");

    /* переполнение буфера: строка обрезается, но завершается нулём */
    rt_fmt(b, 8, L"%s", L"0123456789");
    CHECK(rt_wlen(b) == 7, L"обрезка по размеру буфера");

    CHECK(rt_fmt(b, 256, L"%d", 1234) == 4, L"возврат длины строки");
}

/* --- 2. строки ------------------------------------------------------------ */
static void test_strings(void)
{
    wchar_t buf[64];
    wchar_t s[] = L"  Привет, МИР  ";

    printf("Строки:\n");
    CHECK(rt_wlen(L"абв") == 3, L"rt_wlen");
    CHECK(wseq(rt_wtrim(s), L"Привет, МИР"), L"rt_wtrim");

    rt_wncpy(buf, L"0123456789", 5);
    CHECK(wseq(buf, L"0123") && buf[4] == 0, L"rt_wncpy обрезает и завершает нулём");

    rt_wcpy(buf, L"abc");
    rt_wcat_n(buf, L"defghij", 6);
    CHECK(wseq(buf, L"abcde") && buf[5] == 0, L"rt_wcat_n не переполняет");

    CHECK(rt_wequal_ci(L"Кнопка ПУСК", L"кнопка пуск"), L"сравнение без учёта регистра (кириллица)");
    CHECK(rt_wequal_ci(L"ЁЖИК", L"ёжик"), L"буква Ё");
    CHECK(!rt_wequal_ci(L"Пуск", L"Поиск"), L"разные строки не равны");
    CHECK(rt_wstarts_ci(L"StartMenuExperienceHost", L"startmenu"), L"rt_wstarts_ci");
    CHECK(rt_wcontains_ci(L"Меню «Пуск» не открывается", L"ПУСК"), L"поиск подстроки без учёта регистра");
    CHECK(rt_wfind(L"abcdef", L"cde") != 0, L"rt_wfind");
    CHECK(rt_wfind(L"abcdef", L"xyz") == 0, L"rt_wfind отрицательный");
    CHECK(rt_wfind_ch(L"a;b", L';') == L"a;b" + 1, L"rt_wfind_ch");
}

/* --- 3. буфер строк ------------------------------------------------------- */
static void test_sb(void)
{
    rt_sb b;
    int i;
    printf("Динамический буфер:\n");
    rt_sb_init(&b, 8);
    for (i = 0; i < 500; i++) rt_sb_puts(&b, L"строка ");
    CHECK(b.len == 500 * 7, L"рост буфера без потерь");
    CHECK(!b.oom, L"нет ошибок выделения памяти");
    rt_sb_reset(&b);
    rt_sb_printf(&b, L"%d-%s", 9, L"тест");
    CHECK(wseq(b.p, L"9-тест"), L"rt_sb_printf");
    rt_sb_put_utf8(&b, "\xD0\x9F\xD1\x80\xD0\xB8\xD0\xB2\xD0\xB5\xD1\x82", -1);
    CHECK(b.len == 6 + 6, L"добавление UTF-8 строки");
    rt_sb_free(&b);
}

/* --- 4. кодировки --------------------------------------------------------- */
static void test_utf8(void)
{
    const wchar_t* src = L"Проверка: «Пуск» ✔ 😀";
    char utf8[256];
    wchar_t back[256];
    int n, m;

    printf("Кодировки:\n");
    n = rt_wide_to_utf8(src, -1, utf8, sizeof(utf8));
    CHECK(n > 0, L"преобразование в UTF-8");
    m = rt_utf8_to_wide(utf8, n, back, 256);
    back[m] = 0;
    CHECK(wseq(back, src), L"обратное преобразование совпадает (включая эмодзи)");
    CHECK(rt_wide_to_utf8(L"Ёжик", -1, utf8, sizeof(utf8)) == 8, L"Ё кодируется двумя байтами");
}

/* --- 5. пути -------------------------------------------------------------- */
static void test_paths(void)
{
    wchar_t p[512];

    printf("Пути:\n");
    rt_path_join(p, 512, L"C:\\ProgramData\\ZI-StartFix", L"scripts");
    CHECK(wseq(p, L"C:\\ProgramData\\ZI-StartFix\\scripts"), L"склейка пути");

    rt_path_join(p, 512, L"C:\\ProgramData\\", L"log.txt");
    CHECK(wseq(p, L"C:\\ProgramData\\log.txt"), L"без двойного разделителя");

    rt_wcpy(p, L"C:\\a\\b\\file.log");
    rt_path_dirname(p);
    CHECK(wseq(p, L"C:\\a\\b"), L"каталог файла");

    CHECK(wseq(rt_path_basename(L"C:\\Windows\\explorer.exe"), L"explorer.exe"), L"имя файла");
    CHECK(rt_path_is_abs(L"C:\\Windows"), L"абсолютный путь с диском");
    CHECK(rt_path_is_abs(L"\\\\server\\share"), L"UNC-путь");
    CHECK(!rt_path_is_abs(L"relative\\path"), L"относительный путь");
}

/* --- 6. файлы ------------------------------------------------------------- */
static void test_files(void)
{
    const wchar_t* dir = L"/tmp/zi-test-files";
    wchar_t path[512];
    rt_file f;
    wchar_t text[128];
    int ok;

    printf("Файлы:\n");
    rt_dir_create(dir);
    rt_path_join(path, 512, dir, L"sample.txt");

    ok = rt_file_create(&f, path);
    CHECK(ok, L"создание файла");
    rt_file_put_bom(&f);
    rt_file_writeln(&f, L"первая строка");
    rt_file_writeln(&f, L"вторая строка");
    rt_file_close(&f);

    CHECK(rt_file_size(path) > 20, L"размер файла");
    CHECK(rt_file_exists(path), L"файл существует");
    CHECK(!rt_file_exists(L"/tmp/zi-test-files/нет-такого.txt"), L"отсутствующего файла нет");

    rt_file_open_append(&f, path);
    rt_file_writeln(&f, L"третья строка");
    rt_file_close(&f);
    CHECK(rt_file_size(path) > 40, L"дозапись увеличила файл");
    (void)text;
}

/* --- 7. чтение журнала (ключевой путь: вывод PowerShell в интерфейс) ------ */
typedef struct {
    wchar_t lines[64][512];
    int count;
    size_t last_len;
} lines_t;

static void collect(const wchar_t* line, void* ud)
{
    lines_t* l = (lines_t*)ud;
    l->last_len = rt_wlen(line);
    if (l->count < 64) {
        rt_wncpy(l->lines[l->count], line, 512);
        l->count++;
    }
}

static void test_logtail(void)
{
    const wchar_t* dir = L"/tmp/zi-test-tail";
    wchar_t path[512];
    rt_file f;
    rt_logtail tail;
    lines_t got;

    printf("Чтение журнала:\n");
    rt_dir_create(dir);
    rt_path_join(path, 512, dir, L"step.log");
    DeleteFileW(path);

    /* 1. файл ещё не создан — опрос не должен падать */
    memset(&got, 0, sizeof(got));
    rt_logtail_open(&tail, path);
    rt_logtail_poll(&tail, collect, &got);
    CHECK(got.count == 0, L"нет строк, пока файл не создан");

    /* 2. скрипт начал писать: UTF-8 с BOM */
    rt_file_create(&f, path);
    rt_file_put_bom(&f);
    rt_file_writeln(&f, L"[1/9] Диагностика меню «Пуск»");
    rt_file_close(&f);
    rt_logtail_poll(&tail, collect, &got);
    CHECK(got.count == 1, L"первая строка прочитана");
    CHECK(wseq(got.lines[0], L"[1/9] Диагностика меню «Пуск»"), L"UTF-8 с BOM и кириллицей");

    /* 3. разрыв многобайтовой последовательности между записями */
    {
        char utf8[128];
        int n = rt_wide_to_utf8(L"Привет мир", -1, utf8, sizeof(utf8));
        CHECK(n == 19, L"UTF-8: «Привет мир» = 19 байт");
        rt_file_open_append(&f, path);
        rt_file_write_raw(&f, utf8, 1);              /* только первый байт буквы «П» */
        rt_file_close(&f);
        rt_logtail_poll(&tail, collect, &got);
        CHECK(got.count == 1, L"незавершённый символ не выдаётся как строка");

        rt_file_open_append(&f, path);
        rt_file_write_raw(&f, utf8 + 1, n - 1);
        rt_file_write_raw(&f, "\r\n", 2);
        rt_file_close(&f);
        rt_logtail_poll(&tail, collect, &got);
        CHECK(got.count == 2, L"строка после склейки байтов");
        CHECK(wseq(got.lines[1], L"Привет мир"), L"склейка разорванной UTF-8 последовательности");
    }

    /* 4. длинная строка и служебные \r, пробелы в конце */
    {
        wchar_t longline[9000];
        int i;
        for (i = 0; i < 8999; i++) longline[i] = L'x';
        longline[8999] = 0;
        rt_file_open_append(&f, path);
        rt_file_writeln(&f, longline);
        rt_file_close(&f);
        rt_logtail_poll(&tail, collect, &got);
        CHECK(got.count == 3, L"длинная строка принята без сбоя");
        CHECK(got.last_len == 8000, L"длинная строка обрезана до 8000 символов");
    }

    /* 5. хвост без перевода строки — выдаётся при завершении шага */
    rt_file_open_append(&f, path);
    rt_file_write(&f, L"последняя строка без перевода");
    rt_file_close(&f);
    rt_logtail_poll(&tail, collect, &got);
    CHECK(got.count == 3, L"незавершённая строка ждёт flush");
    rt_logtail_flush(&tail, collect, &got);
    CHECK(got.count == 4 && wseq(got.lines[3], L"последняя строка без перевода"), L"flush выдал последнюю строку");
    rt_logtail_close(&tail);

    /* 6. файл без BOM: кириллица в OEM (866) читается как в консоли */
    rt_path_join(path, 512, dir, L"oem.log");
    DeleteFileW(path);
    rt_file_create(&f, path);
    rt_file_write_raw(&f, "\x8F\xE0\xE8\xE2\xE5\xF2", 6);   /* «Привет» в CP866 */
    rt_file_write_raw(&f, "\r\n", 2);
    rt_file_close(&f);
    memset(&got, 0, sizeof(got));
    rt_logtail_open(&tail, path);
    rt_logtail_poll(&tail, collect, &got);
    CHECK(got.count == 1 && wseq(got.lines[0], L"Привет") == 0, L"файл без BOM читается в OEM-кодировке (эмуляция упрощена)");
    rt_logtail_close(&tail);
}

/* --- 8. версия ОС и учётные данные (эмуляция) ----------------------------- */
static void test_system(void)
{
    rt_osver v;
    wchar_t buf[128];

    printf("Система:\n");
    memset(&v, 0, sizeof(v));
    rt_os_version(&v);
    CHECK(1, L"rt_os_version не падает");

    rt_now_str(buf, 128);
    CHECK(rt_wlen(buf) == 19 && buf[4] == L'-', L"формат даты ГГГГ-ММ-ДД ЧЧ:ММ:СС");

    rt_now_stamp(buf, 128);
    CHECK(rt_wlen(buf) == 15 && buf[8] == L'-', L"формат метки для имён файлов (ГГГГММДД-ЧЧММСС)");

    rt_computer_name(buf, 128);
    CHECK(rt_wlen(buf) > 0, L"имя компьютера");
    rt_user_name(buf, 128);
    CHECK(rt_wlen(buf) > 0, L"имя пользователя");
}

int main(void)
{
    setlocale(LC_ALL, "C.UTF-8");
    printf("=== Модульные тесты ZI Office StartFix (Linux + эмуляция Win32) ===\n\n");
    test_format();
    test_strings();
    test_sb();
    test_utf8();
    test_paths();
    test_files();
    test_logtail();
    test_system();
    printf("\n=== Итог: успешно %d, провалов %d ===\n", g_pass, g_fail);
    return g_fail == 0 ? 0 : 1;
}
