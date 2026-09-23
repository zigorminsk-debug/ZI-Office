/* ============================================================================
 *  Реализация заглушек Windows API для модульных тестов на Linux.
 *  Тестируется только «чистая» логика: строки, формат, пути, чтение журнала.
 * ==========================================================================*/
#include "windows.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <time.h>
#include <errno.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>

/* --- куча ----------------------------------------------------------------- */
HANDLE GetProcessHeap(void) { return (HANDLE)1; }
void* HeapAlloc(HANDLE heap, DWORD flags, size_t bytes) { (void)heap; (void)flags; return malloc(bytes); }
void* HeapReAlloc(HANDLE heap, DWORD flags, void* p, size_t bytes) { (void)heap; (void)flags; return realloc(p, bytes); }
BOOL HeapFree(HANDLE heap, DWORD flags, void* p) { (void)heap; (void)flags; free(p); return 1; }

HMODULE GetModuleHandleW(const wchar_t* name) { (void)name; return (HMODULE)1; }
void* GetProcAddress(HMODULE mod, const char* name) { (void)mod; (void)name; return NULL; }
DWORD GetModuleFileNameW(HMODULE mod, wchar_t* out, DWORD cap)
{
    (void)mod;
    const char* p = "/test/StartFix-x64.exe";
    size_t i = 0;
    for (; p[i] && (DWORD)i + 1 < cap; i++) out[i] = (wchar_t)(unsigned char)p[i];
    out[i] = 0;
    return (DWORD)i;
}

/* --- кодировки ------------------------------------------------------------ */
int MultiByteToWideChar(UINT cp, DWORD flags, const char* src, int srcLen, wchar_t* dst, int dstCap)
{
    (void)flags;
    if (srcLen < 0) srcLen = (int)strlen(src);
    if (dst == NULL || dstCap <= 0) return srcLen * 2 + 1;
    if (cp == CP_UTF8) {
        /* честный разбор UTF-8 — именно это проверяют тесты */
        int i = 0, n = 0;
        while (i < srcLen) {
            unsigned char c = (unsigned char)src[i];
            unsigned long cpv = 0;
            int need = 0, k;
            if (c < 0x80) { cpv = c; need = 0; }
            else if ((c & 0xE0) == 0xC0) { cpv = c & 0x1F; need = 1; }
            else if ((c & 0xF0) == 0xE0) { cpv = c & 0x0F; need = 2; }
            else if ((c & 0xF8) == 0xF0) { cpv = c & 0x07; need = 3; }
            else { cpv = '?'; need = 0; }
            if (i + need >= srcLen) break;
            for (k = 1; k <= need; k++) cpv = (cpv << 6) | ((unsigned char)src[i + k] & 0x3F);
            i += need + 1;
            if (n + 2 > dstCap) break;
#if WCHAR_MAX <= 0xFFFF
            if (cpv < 0x10000) dst[n++] = (wchar_t)cpv;
            else { cpv -= 0x10000; dst[n++] = (wchar_t)(0xD800 + (cpv >> 10)); dst[n++] = (wchar_t)(0xDC00 + (cpv & 0x3FF)); }
#else
            dst[n++] = (wchar_t)cpv;      /* Linux: wchar_t 32-битный (UTF-32) */
#endif
        }
        return n;
    }
    /* прочие кодовые страницы: побайтово (для тестов достаточно latin-1/ASCII) */
    for (int i = 0; i < srcLen && i < dstCap; i++) dst[i] = (wchar_t)(unsigned char)src[i];
    return srcLen;
}

int WideCharToMultiByte(UINT cp, DWORD flags, const wchar_t* src, int srcLen, char* dst, int dstCap,
                        const char* defChar, BOOL* usedDefault)
{
    (void)flags; (void)defChar;
    if (usedDefault) *usedDefault = 0;
    if (srcLen < 0) srcLen = (int)wcslen(src);
    if (cp == CP_UTF8) {
        int n = 0;
        for (int i = 0; i < srcLen; i++) {
            unsigned long c = (unsigned long)src[i];
            if (dst == NULL) { n += (c < 0x80) ? 1 : (c < 0x800 ? 2 : 3); continue; }
            if (c < 0x80) { if (n + 1 > dstCap) break; dst[n++] = (char)c; }
            else if (c < 0x800) { if (n + 2 > dstCap) break; dst[n++] = (char)(0xC0 | (c >> 6)); dst[n++] = (char)(0x80 | (c & 0x3F)); }
            else if (c < 0x10000) { if (n + 3 > dstCap) break;
                dst[n++] = (char)(0xE0 | (c >> 12)); dst[n++] = (char)(0x80 | ((c >> 6) & 0x3F)); dst[n++] = (char)(0x80 | (c & 0x3F)); }
            else { if (n + 4 > dstCap) break;
                dst[n++] = (char)(0xF0 | (c >> 18)); dst[n++] = (char)(0x80 | ((c >> 12) & 0x3F));
                dst[n++] = (char)(0x80 | ((c >> 6) & 0x3F)); dst[n++] = (char)(0x80 | (c & 0x3F)); }
        }
        return n;
    }
    int n = 0;
    for (int i = 0; i < srcLen && n < dstCap; i++) dst[n++] = (char)(src[i] & 0xFF);
    return n;
}

UINT GetOEMCP(void) { return 866; }
UINT GetACP(void)  { return 1251; }

/* --- файлы ---------------------------------------------------------------- */
static void path_to_u8(const wchar_t* w, char* out, size_t cap)
{
    wcstombs(out, w, cap);
}

HANDLE CreateFileW(const wchar_t* path, DWORD access, DWORD share, void* sa, DWORD disposition,
                   DWORD flags, HANDLE tmpl)
{
    char p[2048];
    const char* mode = "rb";
    (void)share; (void)sa; (void)flags; (void)tmpl;
    path_to_u8(path, p, sizeof(p));
    if (access & GENERIC_WRITE) {
        FILE* probe = fopen(p, "rb");
        int exists = probe != NULL;
        if (probe) fclose(probe);
        if (disposition == CREATE_ALWAYS) mode = "wb";
        else if (disposition == OPEN_ALWAYS) mode = "ab";
        else if (disposition == OPEN_EXISTING) mode = exists ? "r+b" : "wb";
    }
    FILE* f = fopen(p, mode);
    if (!f) return INVALID_HANDLE_VALUE;
    return (HANDLE)f;
}

BOOL ReadFile(HANDLE h, void* buf, DWORD toRead, DWORD* read, void* ov)
{
    (void)ov;
    if (h == INVALID_HANDLE_VALUE || !h) { if (read) *read = 0; return 0; }
    size_t n = fread(buf, 1, toRead, (FILE*)h);
    if (read) *read = (DWORD)n;
    return n > 0 || feof((FILE*)h) == 0;
}

BOOL WriteFile(HANDLE h, const void* buf, DWORD toWrite, DWORD* written, void* ov)
{
    (void)ov;
    if (h == INVALID_HANDLE_VALUE || !h) { if (written) *written = 0; return 0; }
    size_t n = fwrite(buf, 1, toWrite, (FILE*)h);
    fflush((FILE*)h);
    if (written) *written = (DWORD)n;
    return n == toWrite;
}

DWORD SetFilePointer(HANDLE h, LONG dist, LONG* distHigh, DWORD method)
{
    int whence = SEEK_SET;
    (void)distHigh;
    if (method == FILE_END) whence = SEEK_END;
    else if (method == 2) whence = SEEK_CUR;
    fseek((FILE*)h, dist, whence);
    return (DWORD)ftell((FILE*)h);
}

BOOL GetFileSizeEx(HANDLE h, LARGE_INTEGER* size)
{
    long cur = ftell((FILE*)h), end;
    fseek((FILE*)h, 0, SEEK_END);
    end = ftell((FILE*)h);
    fseek((FILE*)h, cur, SEEK_SET);
    if (size) { size->LowPart = (DWORD)end; size->HighPart = 0; }
    return 1;
}

BOOL CloseHandle(HANDLE h)
{
    if (h && h != INVALID_HANDLE_VALUE) fclose((FILE*)h);
    return 1;
}

BOOL DeleteFileW(const wchar_t* path)
{
    char p[2048];
    path_to_u8(path, p, sizeof(p));
    return remove(p) == 0;
}

DWORD GetFileAttributesW(const wchar_t* path)
{
    char p[2048];
    struct stat st;
    path_to_u8(path, p, sizeof(p));
    if (stat(p, &st) != 0) return INVALID_FILE_ATTRIBUTES;
    return S_ISDIR(st.st_mode) ? FILE_ATTRIBUTE_DIRECTORY : FILE_ATTRIBUTE_NORMAL;
}

typedef struct { DWORD dwFileAttributes; FILETIME ftCreationTime, ftLastAccessTime, ftLastWriteTime;
                 DWORD nFileSizeHigh, nFileSizeLow; } FILE_ATTRIBUTE_DATA_IMPL;

BOOL GetFileAttributesExW(const wchar_t* path, int level, void* data)
{
    char p[2048];
    struct stat st;
    FILE_ATTRIBUTE_DATA_IMPL* d = (FILE_ATTRIBUTE_DATA_IMPL*)data;
    (void)level;
    path_to_u8(path, p, sizeof(p));
    if (stat(p, &st) != 0) return 0;
    memset(d, 0, sizeof(*d));
    d->dwFileAttributes = S_ISDIR(st.st_mode) ? FILE_ATTRIBUTE_DIRECTORY : FILE_ATTRIBUTE_NORMAL;
    d->nFileSizeLow = (DWORD)(st.st_size & 0xFFFFFFFFu);
    d->nFileSizeHigh = (DWORD)((unsigned long long)st.st_size >> 32);
    return 1;
}

BOOL CreateDirectoryW(const wchar_t* path, void* sa)
{
    char p[2048];
    (void)sa;
    path_to_u8(path, p, sizeof(p));
    return mkdir(p, 0777) == 0;
}

/* --- каналы и процессы (заглушки: тесты их не используют) ----------------- */
BOOL CreatePipe(HANDLE* rd, HANDLE* wr, SECURITY_ATTRIBUTES* sa, DWORD size)
{
    (void)sa; (void)size; (void)rd; (void)wr;
    return 0;
}
BOOL SetHandleInformation(HANDLE h, DWORD mask, DWORD flags) { (void)h; (void)mask; (void)flags; return 0; }
BOOL PeekNamedPipe(HANDLE h, void* b, DWORD bs, DWORD* r, DWORD* a, DWORD* l)
{
    (void)h; (void)b; (void)bs; (void)r; (void)a; (void)l;
    return 0;
}
BOOL CreateProcessW(const wchar_t* app, wchar_t* cmd, void* pa, void* ta, BOOL inherit, DWORD flags,
                    void* env, const wchar_t* cwd, STARTUPINFOW* si, PROCESS_INFORMATION* pi)
{
    (void)app; (void)cmd; (void)pa; (void)ta; (void)inherit; (void)flags; (void)env; (void)cwd; (void)si;
    if (pi) memset(pi, 0, sizeof(*pi));
    return 0;
}
BOOL GetExitCodeProcess(HANDLE h, DWORD* code) { (void)h; if (code) *code = 0; return 1; }
DWORD WaitForSingleObject(HANDLE h, DWORD ms) { (void)h; (void)ms; return WAIT_OBJECT_0; }
BOOL TerminateProcess(HANDLE h, UINT code) { (void)h; (void)code; return 1; }
void ExitProcess(UINT code) { exit((int)code); }
HANDLE GetCurrentProcess(void) { return (HANDLE)1; }
DWORD GetCurrentProcessId(void) { return 42; }
BOOL OpenProcessToken(HANDLE p, DWORD a, HANDLE* t) { (void)p; (void)a; if (t) *t = (HANDLE)1; return 1; }
BOOL GetTokenInformation(HANDLE t, int cls, void* info, DWORD size, DWORD* ret)
{
    (void)t; (void)size;
    if (cls == TokenElevation && info) { ((TOKEN_ELEVATION*)info)->TokenIsElevated = 1; if (ret) *ret = 4; return 1; }
    return 0;
}
BOOL ProcessIdToSessionId(DWORD pid, DWORD* session) { (void)pid; if (session) *session = 1; return 1; }

/* --- реестр (заглушки) ----------------------------------------------------- */
BOOL RegOpenKeyExW(HKEY root, const wchar_t* sub, DWORD opt, DWORD access, HKEY* out)
{
    (void)root; (void)sub; (void)opt; (void)access;
    if (out) *out = (HKEY)1;
    return 1;
}
BOOL RegQueryValueExW(HKEY k, const wchar_t* n, DWORD* res, DWORD* type, BYTE* data, DWORD* size)
{
    (void)k; (void)n; (void)res; (void)type; (void)data; (void)size;
    return 2;   /* ERROR_FILE_NOT_FOUND */
}
LONG RegCloseKey(HKEY k) { (void)k; return 0; }
LONG RegGetValueW(HKEY root, const wchar_t* sub, const wchar_t* name, DWORD flags,
                  DWORD* type, void* data, DWORD* size)
{
    (void)root; (void)sub; (void)name; (void)flags; (void)data; (void)size;
    if (type) *type = 0;
    return 2;
}

/* --- среда ----------------------------------------------------------------- */
DWORD GetEnvironmentVariableW(const wchar_t* name, wchar_t* out, DWORD cap)
{
    const char* v = getenv(name && name[0] == L'T' ? "TMPDIR" : "HOME");
    size_t i = 0;
    if (!v) { if (cap) out[0] = 0; return 0; }
    for (; v[i] && (DWORD)i + 1 < cap; i++) out[i] = (wchar_t)(unsigned char)v[i];
    out[i] = 0;
    return (DWORD)i;
}

DWORD ExpandEnvironmentStringsW(const wchar_t* in, wchar_t* out, DWORD cap)
{
    size_t i = 0;
    for (; in[i] && (DWORD)i + 1 < cap; i++) out[i] = in[i];
    out[i] = 0;
    return (DWORD)i + 1;
}

BOOL GetComputerNameW(wchar_t* out, DWORD* cap)
{
    const wchar_t* n = L"TEST-PC";
    size_t i = 0;
    for (; n[i] && (DWORD)i + 1 < *cap; i++) out[i] = n[i];
    out[i] = 0;
    *cap = (DWORD)i;
    return 1;
}

BOOL GetUserNameW(wchar_t* out, DWORD* cap)
{
    const wchar_t* n = L"tester";
    size_t i = 0;
    for (; n[i] && (DWORD)i + 1 < *cap; i++) out[i] = n[i];
    out[i] = 0;
    *cap = (DWORD)i;
    return 1;
}

void GetLocalTime(SYSTEMTIME* st)
{
    time_t t = time(NULL);
    struct tm tmv;
    localtime_r(&t, &tmv);
    st->wYear = (WORD)(tmv.tm_year + 1900);
    st->wMonth = (WORD)(tmv.tm_mon + 1);
    st->wDay = (WORD)tmv.tm_mday;
    st->wHour = (WORD)tmv.tm_hour;
    st->wMinute = (WORD)tmv.tm_min;
    st->wSecond = (WORD)tmv.tm_sec;
    st->wMilliseconds = 0;
}

void Sleep(DWORD ms) { usleep((useconds_t)ms * 1000); }
BOOL GetVersionExW(OSVERSIONINFOW* v) { (void)v; return 0; }
HANDLE ShellExecuteW(HANDLE w, const wchar_t* op, const wchar_t* f, const wchar_t* p, const wchar_t* d, int s)
{
    (void)w; (void)op; (void)f; (void)p; (void)d; (void)s;
    return (HANDLE)1;
}

HANDLE FindFirstFileW(const wchar_t* mask, WIN32_FIND_DATAW* data)
{
    (void)mask; (void)data;
    return INVALID_HANDLE_VALUE;
}
BOOL FindNextFileW(HANDLE h, WIN32_FIND_DATAW* data) { (void)h; (void)data; return 0; }
BOOL FindClose(HANDLE h) { (void)h; return 1; }

void* memcpy(void* d, const void* s, size_t n);
