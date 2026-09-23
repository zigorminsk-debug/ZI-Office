/* ============================================================================
 *  ZI Office StartFix — реализация автономной среды (без CRT)
 * ==========================================================================*/
#include "rt.h"

/* --------------------------------------------------------------------------
 * Для компоновки без CRT компилятору нужны символы, которые он генерирует сам
 * (копирование структур и т. п.). Реализуем их здесь.
 * ------------------------------------------------------------------------*/
void* memcpy(void* dst, const void* src, size_t n)
{
    unsigned char* d = (unsigned char*)dst;
    const unsigned char* s = (const unsigned char*)src;
    while (n--) *d++ = *s++;
    return dst;
}

void* memset(void* dst, int c, size_t n)
{
    unsigned char* d = (unsigned char*)dst;
    while (n--) *d++ = (unsigned char)c;
    return dst;
}

void* memmove(void* dst, const void* src, size_t n)
{
    unsigned char* d = (unsigned char*)dst;
    const unsigned char* s = (const unsigned char*)src;
    if (d == s || n == 0) return dst;
    if (d < s) { while (n--) *d++ = *s++; }
    else { d += n; s += n; while (n--) *--d = *--s; }
    return dst;
}

int memcmp(const void* a, const void* b, size_t n)
{
    const unsigned char* x = (const unsigned char*)a;
    const unsigned char* y = (const unsigned char*)b;
    while (n--) { if (*x != *y) return (*x < *y) ? -1 : 1; x++; y++; }
    return 0;
}

void* memchr(const void* p, int c, size_t n)
{
    const unsigned char* x = (const unsigned char*)p;
    while (n--) { if (*x == (unsigned char)c) return (void*)x; x++; }
    return 0;
}

/* --------------------------------------------------------------------------
 * Память
 * ------------------------------------------------------------------------*/
void* rt_alloc(size_t bytes)
{
    if (bytes == 0) bytes = 8;
    return HeapAlloc(GetProcessHeap(), 0, bytes);
}

void* rt_realloc(void* p, size_t bytes)
{
    if (p == 0) return rt_alloc(bytes);
    if (bytes == 0) bytes = 8;
    return HeapReAlloc(GetProcessHeap(), 0, p, bytes);
}

void* rt_calloc(size_t count, size_t bytes)
{
    size_t total = count * bytes;
    void* p = rt_alloc(total);
    if (p) memset(p, 0, total);
    return p;
}

void rt_free(void* p)
{
    if (p) HeapFree(GetProcessHeap(), 0, p);
}

/* --------------------------------------------------------------------------
 * Строки
 * ------------------------------------------------------------------------*/
size_t rt_wlen(const wchar_t* s)
{
    size_t n = 0;
    if (!s) return 0;
    while (s[n]) n++;
    return n;
}

wchar_t* rt_wcpy(wchar_t* dst, const wchar_t* src)
{
    wchar_t* d = dst;
    while ((*d++ = *src++) != 0) { }
    return dst;
}

wchar_t* rt_wncpy(wchar_t* dst, const wchar_t* src, size_t cap)
{
    size_t i = 0;
    if (cap == 0) return dst;
    for (; i + 1 < cap && src[i]; i++) dst[i] = src[i];
    dst[i] = 0;
    return dst;
}

wchar_t* rt_wcat(wchar_t* dst, const wchar_t* src)
{
    wchar_t* d = dst + rt_wlen(dst);
    while ((*d++ = *src++) != 0) { }
    return dst;
}

wchar_t* rt_wcat_n(wchar_t* dst, const wchar_t* src, size_t cap)
{
    size_t used = rt_wlen(dst);
    size_t i = 0;
    if (used + 1 >= cap) return dst;
    for (; used + i + 1 < cap && src[i]; i++) dst[used + i] = src[i];
    dst[used + i] = 0;
    return dst;
}

int rt_wcmp(const wchar_t* a, const wchar_t* b)
{
    while (*a && *a == *b) { a++; b++; }
    if (*a == *b) return 0;
    return (*a < *b) ? -1 : 1;
}

static wchar_t lower_w(wchar_t c)
{
    if (c >= L'A' && c <= L'Z') return (wchar_t)(c + 32);
    if (c >= 0x0410 && c <= 0x042F) return (wchar_t)(c + 32);   /* А-Я */
    if (c == 0x0401) return 0x0451;                             /* Ё */
    if (c >= 0x00C0 && c <= 0x00DE && c != 0x00D7) return (wchar_t)(c + 32);
    return c;
}

int rt_wcmp_ci(const wchar_t* a, const wchar_t* b)
{
    while (*a && lower_w(*a) == lower_w(*b)) { a++; b++; }
    {
        wchar_t x = lower_w(*a), y = lower_w(*b);
        if (x == y) return 0;
        return (x < y) ? -1 : 1;
    }
}

int rt_wequal(const wchar_t* a, const wchar_t* b) { return rt_wcmp(a, b) == 0; }
int rt_wequal_ci(const wchar_t* a, const wchar_t* b) { return rt_wcmp_ci(a, b) == 0; }

int rt_wstarts_ci(const wchar_t* s, const wchar_t* prefix)
{
    while (*prefix) {
        if (lower_w(*s) != lower_w(*prefix)) return 0;
        s++; prefix++;
    }
    return 1;
}

const wchar_t* rt_wfind(const wchar_t* hay, const wchar_t* needle)
{
    size_t n = rt_wlen(needle);
    if (n == 0) return hay;
    for (; *hay; hay++) {
        if (*hay == *needle && memcmp(hay, needle, n * sizeof(wchar_t)) == 0) return hay;
    }
    return 0;
}

int rt_wcontains_ci(const wchar_t* hay, const wchar_t* needle)
{
    size_t n = rt_wlen(needle);
    if (n == 0) return 1;
    for (; *hay; hay++) {
        size_t i = 0;
        while (i < n && lower_w(hay[i]) == lower_w(needle[i])) i++;
        if (i == n) return 1;
    }
    return 0;
}

const wchar_t* rt_wfind_ch(const wchar_t* s, wchar_t ch)
{
    for (; *s; s++) if (*s == ch) return s;
    return 0;
}

wchar_t* rt_wtrim(wchar_t* s)
{
    wchar_t* end;
    while (*s == L' ' || *s == L'\t' || *s == L'\r' || *s == L'\n') s++;
    end = s + rt_wlen(s);
    while (end > s && (end[-1] == L' ' || end[-1] == L'\t' || end[-1] == L'\r' || end[-1] == L'\n')) end--;
    *end = 0;
    return s;
}

wchar_t* rt_wdup(const wchar_t* s)
{
    size_t n = rt_wlen(s) + 1;
    wchar_t* p = (wchar_t*)rt_alloc(n * sizeof(wchar_t));
    if (p) memcpy(p, s, n * sizeof(wchar_t));
    return p;
}

void rt_wlwr_ascii(wchar_t* s)
{
    for (; *s; s++) *s = lower_w(*s);
}

int rt_starts_num(const wchar_t* s)
{
    if (!s) return 0;
    while (*s == L' ' || *s == L'\t') s++;
    return (*s >= L'0' && *s <= L'9');
}

/* --------------------------------------------------------------------------
 * Форматирование
 * ------------------------------------------------------------------------*/
typedef struct {
    wchar_t* buf;
    size_t   cap;
    size_t   len;
} fmt_out;

static void fo_putc(fmt_out* o, wchar_t c)
{
    if (o->len + 1 < o->cap) o->buf[o->len] = c;
    o->len++;
}

static void fo_pad(fmt_out* o, int width, int left, wchar_t fill)
{
    int i;
    if (width > 0) for (i = 0; i < width; i++) fo_putc(o, fill);
}

static void fo_number(fmt_out* o, u64 value, int base, int upper, int neg,
                      int width, int zero_pad, int left)
{
    wchar_t tmp[70];
    int n = 0, i;
    const wchar_t* digits = upper ? L"0123456789ABCDEF" : L"0123456789abcdef";
    if (value == 0) tmp[n++] = L'0';
    while (value) { tmp[n++] = digits[value % (u64)base]; value /= (u64)base; }
    if (neg) tmp[n++] = L'-';
    if (!left && !zero_pad) fo_pad(o, width - n, 0, L' ');
    if (!left && zero_pad) {
        if (neg) { fo_putc(o, L'-'); }
        fo_pad(o, width - n, 0, L'0');
    }
    for (i = n - 1; i >= 0; i--) {
        if (zero_pad && !left && neg && i == n - 1) continue;   /* знак уже выведен */
        fo_putc(o, tmp[i]);
    }
    if (left) fo_pad(o, width - n, 0, L' ');
}

static void fo_string(fmt_out* o, const wchar_t* s, int width, int left)
{
    size_t n;
    if (!s) s = L"(null)";
    n = rt_wlen(s);
    if (left) {
        size_t i;
        for (i = 0; i < n; i++) fo_putc(o, s[i]);
        fo_pad(o, width - (int)n, 0, L' ');
    } else {
        size_t i;
        fo_pad(o, width - (int)n, 0, L' ');
        for (i = 0; i < n; i++) fo_putc(o, s[i]);
    }
}

int rt_fmtv(wchar_t* buf, size_t cap, const wchar_t* fmt, va_list ap)
{
    fmt_out o;
    const wchar_t* p;

    o.buf = buf;
    o.cap = cap;
    o.len = 0;

    for (p = fmt; *p; p++) {
        int left = 0, zero = 0, width = 0, longlong = 0, half = 0;
        if (*p != L'%') { fo_putc(&o, *p); continue; }
        p++;
        if (*p == L'-') { left = 1; p++; }
        if (*p == L'0') { zero = 1; p++; }
        while (*p >= L'0' && *p <= L'9') { width = width * 10 + (int)(*p - L'0'); p++; }
        if (*p == L'l') { p++; if (*p == L'l') { longlong = 1; p++; } else longlong = 1; }
        else if (*p == L'h') { half = 1; p++; }
        else if (*p == L'I') { p++; if (*p == L'6' && p[1] == L'4') { p += 2; longlong = 1; } }
        switch (*p) {
        case L'd': case L'i': {
            i64 v = longlong ? va_arg(ap, i64) : (i64)va_arg(ap, int);
            if (half) v = (i16)v;
            fo_number(&o, (u64)(v < 0 ? -v : v), 10, 0, v < 0, width, zero, left);
            break;
        }
        case L'u': {
            u64 v = longlong ? va_arg(ap, u64) : (u64)va_arg(ap, unsigned int);
            if (half) v = (u16)v;
            fo_number(&o, v, 10, 0, 0, width, zero, left);
            break;
        }
        case L'x': case L'X': {
            u64 v = longlong ? va_arg(ap, u64) : (u64)va_arg(ap, unsigned int);
            fo_number(&o, v, 16, *p == L'X', 0, width, zero, left);
            break;
        }
        case L'p': {
            u64 v = (u64)(size_t)va_arg(ap, void*);
            fo_putc(&o, L'0'); fo_putc(&o, L'x');
            fo_number(&o, v, 16, 0, 0, 0, 0, 0);
            break;
        }
        case L'c': {
            wchar_t c = (wchar_t)va_arg(ap, int);
            fo_putc(&o, c);
            break;
        }
        case L's': {
            const wchar_t* s = va_arg(ap, const wchar_t*);
            fo_string(&o, s, width, left);
            break;
        }
        case L'S': {
            const char* s = va_arg(ap, const char*);
            wchar_t tmp[1024];
            if (!s) s = "(null)";
            rt_utf8_to_wide(s, -1, tmp, 1024);
            fo_string(&o, tmp, width, left);
            break;
        }
        case L'%':
            fo_putc(&o, L'%');
            break;
        case 0:
            goto done;
        default:
            fo_putc(&o, L'%');
            fo_putc(&o, *p);
            break;
        }
    }
done:
    if (cap) {
        size_t at = (o.len < cap) ? o.len : cap - 1;
        buf[at] = 0;
    }
    return (int)o.len;
}

int rt_fmt(wchar_t* buf, size_t cap, const wchar_t* fmt, ...)
{
    int r;
    va_list ap;
    va_start(ap, fmt);
    r = rt_fmtv(buf, cap, fmt, ap);
    va_end(ap);
    return r;
}

/* --------------------------------------------------------------------------
 * Буфер строк
 * ------------------------------------------------------------------------*/
void rt_sb_init(rt_sb* b, size_t initial_cap)
{
    if (initial_cap < 256) initial_cap = 256;
    b->p = (wchar_t*)rt_alloc(initial_cap * sizeof(wchar_t));
    b->cap = b->p ? initial_cap : 0;
    b->len = 0;
    b->oom = b->p ? 0 : 1;
    if (b->p) b->p[0] = 0;
}

void rt_sb_free(rt_sb* b)
{
    if (b->p) rt_free(b->p);
    b->p = 0;
    b->len = b->cap = 0;
}

void rt_sb_reset(rt_sb* b)
{
    b->len = 0;
    if (b->p) b->p[0] = 0;
}

void rt_sb_need(rt_sb* b, size_t extra)
{
    size_t want;
    if (b->oom) return;
    if (b->len + extra + 1 <= b->cap) return;
    want = b->cap ? b->cap : 256;
    while (want < b->len + extra + 1) want *= 2;
    {
        wchar_t* np = (wchar_t*)rt_realloc(b->p, want * sizeof(wchar_t));
        if (!np) { b->oom = 1; return; }
        b->p = np;
        b->cap = want;
    }
}

void rt_sb_putn(rt_sb* b, const wchar_t* s, size_t n)
{
    rt_sb_need(b, n);
    if (b->oom) return;
    memcpy(b->p + b->len, s, n * sizeof(wchar_t));
    b->len += n;
    b->p[b->len] = 0;
}

void rt_sb_puts(rt_sb* b, const wchar_t* s)
{
    if (!s) return;
    rt_sb_putn(b, s, rt_wlen(s));
}

void rt_sb_putc(rt_sb* b, wchar_t c)
{
    rt_sb_need(b, 1);
    if (b->oom) return;
    b->p[b->len++] = c;
    b->p[b->len] = 0;
}

void rt_sb_printf(rt_sb* b, const wchar_t* fmt, ...)
{
    wchar_t tmp[4096];
    va_list ap;
    int n;
    va_start(ap, fmt);
    n = rt_fmtv(tmp, 4096, fmt, ap);
    va_end(ap);
    if (n > 4095) n = 4095;
    rt_sb_putn(b, tmp, (size_t)n);
}

void rt_sb_put_utf8(rt_sb* b, const char* s, int len)
{
    wchar_t tmp[2048];
    int n;
    if (!s) return;
    if (len < 0) { len = 0; while (s[len]) len++; }
    n = rt_utf8_to_wide(s, len, tmp, 2048);
    if (n > 0) rt_sb_putn(b, tmp, (size_t)n);
}

/* --------------------------------------------------------------------------
 * Кодировки
 * ------------------------------------------------------------------------*/
int rt_utf8_to_wide(const char* s, int len, wchar_t* out, int cap)
{
    if (len < 0) {
        len = 0;
        while (s[len]) len++;
    }
    return MultiByteToWideChar(CP_UTF8, 0, s, len, out, cap);
}

int rt_wide_to_utf8(const wchar_t* s, int len, char* out, int cap)
{
    if (len < 0) len = (int)rt_wlen(s);
    return WideCharToMultiByte(CP_UTF8, 0, s, len, out, cap, 0, 0);
}

int rt_mb_to_wide(UINT cp, const char* s, int len, wchar_t* out, int cap)
{
    if (len < 0) { len = 0; while (s[len]) len++; }
    return MultiByteToWideChar(cp, 0, s, len, out, cap);
}

int rt_wide_to_mb(UINT cp, const wchar_t* s, int len, char* out, int cap)
{
    if (len < 0) len = (int)rt_wlen(s);
    if (cp == 0) cp = CP_ACP;
    return WideCharToMultiByte(cp, 0, s, len, out, cap, 0, 0);
}

UINT rt_oem_cp(void)  { return GetOEMCP(); }
UINT rt_ansi_cp(void) { return GetACP(); }

/* --------------------------------------------------------------------------
 * Файлы
 * ------------------------------------------------------------------------*/
int rt_file_create(rt_file* f, const wchar_t* path)
{
    f->utf8_text = 1;
    f->h = CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ, 0, CREATE_ALWAYS,
                       FILE_ATTRIBUTE_NORMAL, 0);
    return f->h != INVALID_HANDLE_VALUE;
}

int rt_file_open_append(rt_file* f, const wchar_t* path)
{
    f->utf8_text = 1;
    f->h = CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ, 0, OPEN_ALWAYS,
                       FILE_ATTRIBUTE_NORMAL, 0);
    if (f->h == INVALID_HANDLE_VALUE) return 0;
    SetFilePointer(f->h, 0, 0, FILE_END);
    return 1;
}

int rt_file_open_read(rt_file* f, const wchar_t* path)
{
    f->utf8_text = 0;
    f->h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, 0,
                       OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
    return f->h != INVALID_HANDLE_VALUE;
}

void rt_file_close(rt_file* f)
{
    if (f->h && f->h != INVALID_HANDLE_VALUE) CloseHandle(f->h);
    f->h = 0;
}

int rt_file_write_raw(rt_file* f, const void* data, DWORD bytes)
{
    const char* p = (const char*)data;
    if (!f->h || f->h == INVALID_HANDLE_VALUE) return 0;
    while (bytes > 0) {
        DWORD written = 0;
        if (!WriteFile(f->h, p, bytes, &written, 0)) return 0;
        if (written == 0) return 0;
        p += written;
        bytes -= written;
    }
    return 1;
}

int rt_file_write(rt_file* f, const wchar_t* text)
{
    char buf[8192];
    int len, n, off = 0;
    if (!text) return 0;
    len = (int)rt_wlen(text);
    while (off < len) {
        int chunk = len - off;
        if (chunk > 2000) chunk = 2000;
        n = rt_wide_to_utf8(text + off, chunk, buf, sizeof(buf));
        if (n <= 0) return 0;
        if (!rt_file_write_raw(f, buf, (DWORD)n)) return 0;
        off += chunk;
    }
    return 1;
}

int rt_file_writeln(rt_file* f, const wchar_t* text)
{
    if (!rt_file_write(f, text)) return 0;
    return rt_file_write_raw(f, "\r\n", 2);
}

int rt_file_put_bom(rt_file* f)
{
    return rt_file_write_raw(f, "\xEF\xBB\xBF", 3);
}

u64 rt_file_size_handle(HANDLE h)
{
    LARGE_INTEGER li;
    li.QuadPart = 0;
    if (!h || h == INVALID_HANDLE_VALUE) return 0;
    GetFileSizeEx(h, &li);
    return (u64)li.QuadPart;
}

u64 rt_file_size(const wchar_t* path)
{
    WIN32_FILE_ATTRIBUTE_DATA fad;
    if (!GetFileAttributesExW(path, GetFileExInfoStandard, &fad)) return 0;
    return ((u64)fad.nFileSizeHigh << 32) | fad.nFileSizeLow;
}

int rt_file_exists(const wchar_t* path)
{
    DWORD a = GetFileAttributesW(path);
    return (a != INVALID_FILE_ATTRIBUTES) && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

int rt_dir_exists(const wchar_t* path)
{
    DWORD a = GetFileAttributesW(path);
    return (a != INVALID_FILE_ATTRIBUTES) && (a & FILE_ATTRIBUTE_DIRECTORY);
}

int rt_dir_create(const wchar_t* path)
{
    wchar_t tmp[MAX_PATH * 2];
    size_t i, n;
    if (!path || !*path) return 0;
    if (rt_dir_exists(path)) return 1;
    rt_wncpy(tmp, path, MAX_PATH * 2);
    n = rt_wlen(tmp);
    for (i = 0; i < n; i++) {
        if (tmp[i] == L'\\' && i > 2) {
            tmp[i] = 0;
            CreateDirectoryW(tmp, 0);
            tmp[i] = L'\\';
        }
    }
    return CreateDirectoryW(tmp, 0) || rt_dir_exists(tmp);
}

int rt_dir_create_for_file(const wchar_t* path)
{
    wchar_t tmp[MAX_PATH * 2];
    rt_wncpy(tmp, path, MAX_PATH * 2);
    rt_path_dirname(tmp);
    return rt_dir_create(tmp);
}

int rt_file_nocase_append(const wchar_t* path, const wchar_t* text)
{
    rt_file f;
    int ok;
    if (!rt_file_open_append(&f, path)) return 0;
    ok = rt_file_writeln(&f, text);
    rt_file_close(&f);
    return ok;
}

/* --------------------------------------------------------------------------
 * Пути и переменные среды
 * ------------------------------------------------------------------------*/
void rt_path_join(wchar_t* dst, size_t cap, const wchar_t* dir, const wchar_t* name)
{
    rt_wncpy(dst, dir, cap);
    if (dst[0] && dst[rt_wlen(dst) - 1] != L'\\') rt_wcat_n(dst, L"\\", cap);
    rt_wcat_n(dst, name, cap);
}

void rt_path_dirname(wchar_t* path)
{
    wchar_t* p = (wchar_t*)path + rt_wlen(path);
    while (p > path && p[-1] != L'\\' && p[-1] != L'/') p--;
    if (p > path) p[-1] = 0;
}

const wchar_t* rt_path_basename(const wchar_t* path)
{
    const wchar_t* p = path + rt_wlen(path);
    while (p > path && p[-1] != L'\\' && p[-1] != L'/') p--;
    return p;
}

int rt_path_is_abs(const wchar_t* path)
{
    if (!path || !*path) return 0;
    if (path[0] == L'\\' && path[1] == L'\\') return 1;
    if (((path[0] >= L'A' && path[0] <= L'Z') || (path[0] >= L'a' && path[0] <= L'z')) && path[1] == L':')
        return 1;
    return 0;
}

void rt_module_path(wchar_t* out, size_t cap)
{
    GetModuleFileNameW(0, out, (DWORD)cap);
}

void rt_module_dir(wchar_t* out, size_t cap)
{
    rt_module_path(out, cap);
    rt_path_dirname(out);
}

int rt_env(const wchar_t* name, wchar_t* out, size_t cap)
{
    DWORD n = GetEnvironmentVariableW(name, out, (DWORD)cap);
    if (n == 0 || n >= (DWORD)cap) { if (cap) out[0] = 0; return 0; }
    return 1;
}

int rt_env_expand(const wchar_t* in, wchar_t* out, size_t cap)
{
    DWORD n = ExpandEnvironmentStringsW(in, out, (DWORD)cap);
    if (n == 0 || n > (DWORD)cap) { if (cap) out[0] = 0; return 0; }
    return 1;
}

/* --------------------------------------------------------------------------
 * Реестр
 * ------------------------------------------------------------------------*/
int rt_reg_get_str(HKEY root, const wchar_t* sub, const wchar_t* name, wchar_t* out, DWORD cch)
{
    DWORD size = cch * sizeof(wchar_t);
    DWORD type = 0;
    if (!out || !cch) return 0;
    out[0] = 0;
    if (RegGetValueW(root, sub, name, RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ, &type, out, &size) != ERROR_SUCCESS)
        return 0;
    return 1;
}

int rt_reg_get_multi_str_first(HKEY root, const wchar_t* sub, const wchar_t* name, wchar_t* out, DWORD cch)
{
    DWORD size = cch * sizeof(wchar_t);
    DWORD type = 0;
    if (!out || !cch) return 0;
    out[0] = 0;
    if (RegGetValueW(root, sub, name, RRF_RT_REG_MULTI_SZ, &type, out, &size) != ERROR_SUCCESS)
        return 0;
    return 1;
}

int rt_reg_get_dword(HKEY root, const wchar_t* sub, const wchar_t* name, DWORD* out)
{
    DWORD size = sizeof(DWORD);
    DWORD type = 0;
    DWORD value = 0;
    if (RegGetValueW(root, sub, name, RRF_RT_REG_DWORD, &type, &value, &size) != ERROR_SUCCESS)
        return 0;
    if (out) *out = value;
    return 1;
}

int rt_reg_key_exists(HKEY root, const wchar_t* sub)
{
    HKEY h = 0;
    if (RegOpenKeyExW(root, sub, 0, KEY_READ, &h) != ERROR_SUCCESS) return 0;
    RegCloseKey(h);
    return 1;
}

int rt_reg_value_exists(HKEY root, const wchar_t* sub, const wchar_t* name)
{
    HKEY h = 0;
    DWORD type = 0, size = 0;
    int ok = 0;
    if (RegOpenKeyExW(root, sub, 0, KEY_READ, &h) != ERROR_SUCCESS) return 0;
    if (RegQueryValueExW(h, name, 0, &type, 0, &size) == ERROR_SUCCESS) ok = 1;
    RegCloseKey(h);
    return ok;
}

/* --------------------------------------------------------------------------
 * Процессы
 * ------------------------------------------------------------------------*/
int rt_proc_start(rt_proc* p, const wchar_t* cmdline, const wchar_t* cwd)
{
    SECURITY_ATTRIBUTES sa;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    wchar_t* cmd;

    memset(p, 0, sizeof(*p));
    p->hProcess = p->hThread = p->hOutRead = p->hErrRead = p->hInWrite = 0;

    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = 0;
    sa.bInheritHandle = TRUE;

    if (!CreatePipe(&p->hOutRead, &p->hInWrite, &sa, 0)) return 0;
    SetHandleInformation(p->hOutRead, HANDLE_FLAG_INHERIT, 0);

    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = p->hInWrite;
    si.hStdError = p->hInWrite;
    si.hStdInput = INVALID_HANDLE_VALUE;

    memset(&pi, 0, sizeof(pi));
    cmd = rt_wdup(cmdline);
    if (!cmd) { rt_proc_close(p); return 0; }

    if (!CreateProcessW(0, cmd, 0, 0, TRUE, CREATE_NO_WINDOW, 0, cwd, &si, &pi)) {
        rt_free(cmd);
        rt_proc_close(p);
        return 0;
    }
    rt_free(cmd);
    CloseHandle(p->hInWrite);           /* нужен только потомку */
    p->hInWrite = 0;
    p->hProcess = pi.hProcess;
    p->hThread = pi.hThread;
    p->pid = pi.dwProcessId;
    return 1;
}

int rt_proc_running(rt_proc* p)
{
    if (!p->hProcess) return 0;
    return WaitForSingleObject(p->hProcess, 0) == WAIT_TIMEOUT;
}

DWORD rt_proc_exit_code(rt_proc* p)
{
    DWORD code = 0;
    if (!p->hProcess) return 0;
    GetExitCodeProcess(p->hProcess, &code);
    return code;
}

void rt_proc_kill(rt_proc* p)
{
    if (p->hProcess) TerminateProcess(p->hProcess, 0xDEAD);
}

int rt_proc_wait(rt_proc* p, DWORD timeout_ms)
{
    if (!p->hProcess) return 1;
    return WaitForSingleObject(p->hProcess, timeout_ms) == WAIT_OBJECT_0;
}

void rt_proc_close(rt_proc* p)
{
    if (p->hOutRead) CloseHandle(p->hOutRead);
    if (p->hErrRead) CloseHandle(p->hErrRead);
    if (p->hInWrite) CloseHandle(p->hInWrite);
    if (p->hProcess) CloseHandle(p->hProcess);
    if (p->hThread) CloseHandle(p->hThread);
    p->hOutRead = p->hErrRead = p->hInWrite = p->hProcess = p->hThread = 0;
}

int rt_pipe_read(HANDLE h, char* buf, DWORD cap, DWORD* got)
{
    DWORD avail = 0, read = 0;
    *got = 0;
    if (!h) return 0;
    if (!PeekNamedPipe(h, 0, 0, 0, &avail, 0)) return -1;   /* канал закрыт */
    if (avail == 0) return 0;
    if (avail > cap) avail = cap;
    if (!ReadFile(h, buf, avail, &read, 0)) return -1;
    *got = read;
    return 1;
}


/* --------------------------------------------------------------------------
 * Инкрементальное построчное чтение журнала.
 * Поддерживается UTF-8 (с BOM и без), UTF-16LE отбрасывается,
 * иначе предполагается OEM-кодировка консоли.
 * ------------------------------------------------------------------------*/
typedef void (*rt_line_fn)(const wchar_t* line, void* ud);

static void rt_tail_emit_line(rt_logtail* t, rt_line_fn on_line, void* ud)
{
    t->line[t->line_len] = 0;
    while (t->line_len > 0 &&
           (t->line[t->line_len - 1] == L'\r' || t->line[t->line_len - 1] == L' '))
        t->line[--t->line_len] = 0;
    if (on_line && t->line_len > 0) on_line(t->line, ud);
    t->line_len = 0;
}

static void rt_tail_push_char(rt_logtail* t, wchar_t c, rt_line_fn on_line, void* ud)
{
    if (c == L'\n') { rt_tail_emit_line(t, on_line, ud); return; }
    if (t->line_len < 8000) t->line[t->line_len++] = c;
}

static void rt_tail_decode_utf8(rt_logtail* t, const char* data, int len, rt_line_fn on_line, void* ud)
{
    int i = 0;
    while (i < len) {
        unsigned char c = (unsigned char)data[i];
        int need = 0, k;
        unsigned long cp = 0;
        if (c < 0x80) { rt_tail_push_char(t, (wchar_t)c, on_line, ud); i++; continue; }
        else if ((c & 0xE0) == 0xC0) { need = 1; cp = c & 0x1F; }
        else if ((c & 0xF0) == 0xE0) { need = 2; cp = c & 0x0F; }
        else if ((c & 0xF8) == 0xF0) { need = 3; cp = c & 0x07; }
        else { rt_tail_push_char(t, L'?', on_line, ud); i++; continue; }
        if (i + need >= len) break;                    /* неполная последовательность */
        for (k = 1; k <= need; k++) {
            unsigned char cc = (unsigned char)data[i + k];
            if ((cc & 0xC0) != 0x80) { cp = 0; need = -1; break; }
            cp = (cp << 6) | (cc & 0x3F);
        }
        if (need < 0) { rt_tail_push_char(t, L'?', on_line, ud); i++; continue; }
        i += need + 1;
        if (cp < 0x10000) rt_tail_push_char(t, (wchar_t)cp, on_line, ud);
        else {
            cp -= 0x10000;
            rt_tail_push_char(t, (wchar_t)(0xD800 + (cp >> 10)), on_line, ud);
            rt_tail_push_char(t, (wchar_t)(0xDC00 + (cp & 0x3FF)), on_line, ud);
        }
    }
    t->pend_len = 0;
    if (i < len && (len - i) < (int)sizeof(t->pend)) {
        memcpy(t->pend, data + i, (size_t)(len - i));
        t->pend_len = len - i;
    }
}

static void rt_tail_decode_oem(rt_logtail* t, const char* data, int len, rt_line_fn on_line, void* ud)
{
    wchar_t tmp[8192];
    int n, p;
    n = rt_mb_to_wide(rt_oem_cp(), data, len, tmp, 8192);
    for (p = 0; p < n; p++) rt_tail_push_char(t, tmp[p], on_line, ud);
}

void rt_logtail_open(rt_logtail* t, const wchar_t* path)
{
    HANDLE h;
    memset(t, 0, sizeof(*t));
    t->utf8 = 1;
    rt_wncpy(t->path, path, 520);
    h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, 0,
                    OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
    if (h != INVALID_HANDLE_VALUE) {
        t->hFile = (void*)h;
        t->opened = 1;
    }
}

void rt_logtail_poll(rt_logtail* t, void (*on_line)(const wchar_t* line, void* ud), void* ud)
{
    HANDLE h;
    rt_line_fn fn = on_line;

    if (!t->opened) {
        h = CreateFileW(t->path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, 0,
                        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
        if (h == INVALID_HANDLE_VALUE) return;      /* файл ещё не создан */
        t->hFile = (void*)h;
        t->opened = 1;
    }
    h = (HANDLE)t->hFile;

    for (;;) {
        DWORD got = 0;
        if (!ReadFile(h, t->raw, (DWORD)sizeof(t->raw), &got, 0) || got == 0) break;
        if (!t->checked_bom) {
            t->checked_bom = 1;
            if (got >= 3 && t->raw[0] == 0xEF && t->raw[1] == 0xBB && t->raw[2] == 0xBF) {
                t->utf8 = 1;
                memmove(t->raw, t->raw + 3, got - 3);
                got -= 3;
            } else if (got >= 2 && t->raw[0] == 0xFF && t->raw[1] == 0xFE) {
                t->utf8 = 0;                        /* UTF-16LE построчно не разбираем */
                memmove(t->raw, t->raw + 2, got - 2);
                got -= 2;
            } else {
                t->utf8 = 0;                        /* без BOM — читаем как OEM */
            }
        }
        if (got == 0) break;
        if (t->utf8) {
            char joined[8200];
            int jn = 0;
            if (t->pend_len > 0) {
                memcpy(joined, t->pend, (size_t)t->pend_len);
                jn = t->pend_len;
                t->pend_len = 0;
            }
            memcpy(joined + jn, t->raw, got);
            rt_tail_decode_utf8(t, joined, jn + (int)got, fn, ud);
        } else {
            rt_tail_decode_oem(t, (const char*)t->raw, (int)got, fn, ud);
        }
        t->offset += got;
        if (got < sizeof(t->raw)) break;
    }
}

void rt_logtail_flush(rt_logtail* t, void (*on_line)(const wchar_t* line, void* ud), void* ud)
{
    if (t->line_len > 0) {
        t->line[t->line_len] = 0;
        if (on_line) on_line(t->line, ud);
        t->line_len = 0;
    }
}

void rt_logtail_close(rt_logtail* t)
{
    if (t->opened && t->hFile) CloseHandle((HANDLE)t->hFile);
    t->opened = 0;
    t->hFile = 0;
}

/* --------------------------------------------------------------------------
 * Система
 * ------------------------------------------------------------------------*/
typedef struct {
    ULONG  dwOSVersionInfoSize;
    ULONG  dwMajorVersion;
    ULONG  dwMinorVersion;
    ULONG  dwBuildNumber;
    ULONG  dwPlatformId;
    WCHAR  szCSDVersion[128];
    USHORT wServicePackMajor;
    USHORT wServicePackMinor;
    USHORT wSuiteMask;
    BYTE   wProductType;
    BYTE   wReserved;
} rt_osvi;

typedef LONG (WINAPI *fnRtlGetVersion)(rt_osvi*);

int rt_os_version(rt_osver* v)
{
    HMODULE nt = GetModuleHandleW(L"ntdll.dll");
    memset(v, 0, sizeof(*v));
    if (nt) {
        fnRtlGetVersion fn = (fnRtlGetVersion)(void*)GetProcAddress(nt, "RtlGetVersion");
        if (fn) {
            rt_osvi vi;
            memset(&vi, 0, sizeof(vi));
            vi.dwOSVersionInfoSize = sizeof(vi);
            if (fn(&vi) == 0) {
                v->major = vi.dwMajorVersion;
                v->minor = vi.dwMinorVersion;
                v->build = vi.dwBuildNumber;
                v->product_type = vi.wProductType;
                rt_wncpy(v->csd, vi.szCSDVersion, 64);
                return 1;
            }
        }
    }
    {
        OSVERSIONINFOW oi;
        memset(&oi, 0, sizeof(oi));
        oi.dwOSVersionInfoSize = sizeof(oi);
        /* на новых системах без манифеста вернёт 6.2, поэтому только как резерв */
        if (GetVersionExW(&oi)) {
            v->major = oi.dwMajorVersion;
            v->minor = oi.dwMinorVersion;
            v->build = oi.dwBuildNumber;
            v->product_type = 1;
            return 1;
        }
    }
    return 0;
}

int rt_is_admin(void)
{
    HANDLE token = 0;
    TOKEN_ELEVATION elev;
    DWORD size = sizeof(elev);
    int is_admin = 0;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) return 0;
    if (GetTokenInformation(token, TokenElevation, &elev, size, &size))
        is_admin = elev.TokenIsElevated ? 1 : 0;
    CloseHandle(token);
    return is_admin;
}

int rt_console_session(void)
{
    DWORD sid = 0;
    if (ProcessIdToSessionId(GetCurrentProcessId(), &sid)) return (int)sid;
    return -1;
}

void rt_computer_name(wchar_t* out, size_t cap)
{
    DWORD n = (DWORD)cap;
    out[0] = 0;
    GetComputerNameW(out, &n);
}

void rt_user_name(wchar_t* out, size_t cap)
{
    DWORD n = (DWORD)cap;
    out[0] = 0;
    GetUserNameW(out, &n);
}

void rt_now_str(wchar_t* out, size_t cap)
{
    SYSTEMTIME st;
    GetLocalTime(&st);
    rt_fmt(out, cap, L"%04u-%02u-%02u %02u:%02u:%02u",
           st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
}

void rt_now_stamp(wchar_t* out, size_t cap)
{
    SYSTEMTIME st;
    GetLocalTime(&st);
    rt_fmt(out, cap, L"%04u%02u%02u-%02u%02u%02u",
           st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
}

void rt_sleep_ms(DWORD ms)
{
    Sleep(ms);
}

int rt_is_wow64(void)
{
    typedef BOOL (WINAPI *fnIsWow64)(HANDLE, BOOL*);
    HMODULE k = GetModuleHandleW(L"kernel32.dll");
    BOOL wow = FALSE;
    if (k) {
        fnIsWow64 fn = (fnIsWow64)(void*)GetProcAddress(k, "IsWow64Process");
        if (fn && fn(GetCurrentProcess(), &wow)) return wow ? 1 : 0;
    }
    /* резерв: наличие каталога Sysnative означает WOW64-процесс */
    {
        wchar_t root[520], probe[520];
        if (rt_env(L"SystemRoot", root, 520)) {
            rt_path_join(probe, 520, root, L"Sysnative");
            return rt_dir_exists(probe) ? 1 : 0;
        }
    }
    return 0;
}

int rt_powershell_path(wchar_t* out, size_t cap)
{
    wchar_t root[520], cand[520];
    static const wchar_t* rel = L"WindowsPowerShell\\v1.0\\powershell.exe";

    out[0] = 0;
    if (!rt_env(L"SystemRoot", root, 520)) rt_wncpy(root, L"C:\\Windows", 520);

    if (rt_is_wow64()) {
        rt_path_join(cand, 520, root, L"Sysnative");
        rt_path_join(cand, 520, cand, rel);
        if (rt_file_exists(cand)) { rt_wncpy(out, cand, cap); return 1; }
    }
    rt_path_join(cand, 520, root, L"System32");
    rt_path_join(cand, 520, cand, rel);
    if (rt_file_exists(cand)) { rt_wncpy(out, cand, cap); return 1; }
    rt_wncpy(out, cand, cap);
    return 0;
}

void rt_gui_open_path(const wchar_t* path)
{
    ShellExecuteW(0, L"open", path, 0, 0, SW_SHOWNORMAL);
}

int rt_run_and_wait(const wchar_t* cmdline, const wchar_t* cwd, int hidden)
{
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    wchar_t* cmd = rt_wdup(cmdline);
    DWORD flags = hidden ? CREATE_NO_WINDOW : 0;
    int code = -1;
    if (!cmd) return -1;
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    memset(&pi, 0, sizeof(pi));
    if (CreateProcessW(0, cmd, 0, 0, FALSE, flags, 0, cwd, &si, &pi)) {
        WaitForSingleObject(pi.hProcess, INFINITE);
        GetExitCodeProcess(pi.hProcess, (DWORD*)&code);
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    }
    rt_free(cmd);
    return code;
}
