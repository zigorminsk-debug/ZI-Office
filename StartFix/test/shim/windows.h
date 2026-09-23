/* ============================================================================
 *  Минимальная эмуляция Windows API для модульных тестов на Linux.
 *  Нужна только для src/rt.c — сам EXE этой эмуляцией не пользуется.
 * ==========================================================================*/
#ifndef ZI_SHIM_WINDOWS_H
#define ZI_SHIM_WINDOWS_H

#include <stddef.h>
#include <stdarg.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* строковые операции объявляем сами: на Windows их приносит string.h */
void* memcpy(void* dst, const void* src, size_t n);
void* memmove(void* dst, const void* src, size_t n);
void* memset(void* dst, int c, size_t n);
int   memcmp(const void* a, const void* b, size_t n);
void* memchr(const void* p, int c, size_t n);

#define WINAPI
#define CALLBACK
#define CONST const
#define VOID void

typedef unsigned char      BYTE;
typedef unsigned short     WORD;
typedef unsigned int       UINT;
typedef unsigned long      DWORD;
typedef unsigned long      ULONG;
typedef long               LONG;
typedef unsigned short     USHORT;
typedef int                BOOL;
typedef long long          LONGLONG;
typedef unsigned long long ULONGLONG;
typedef void*              HANDLE;
typedef void*              HINSTANCE;
typedef void*              HWND;
typedef void*              HMENU;
typedef void*              HBRUSH;
typedef void*              HICON;
typedef void*              HDC;
typedef intptr_t           INT_PTR;
typedef uintptr_t          UINT_PTR;
typedef intptr_t           LONG_PTR;
typedef UINT_PTR           WPARAM;
typedef LONG_PTR           LPARAM;
typedef void*              HMODULE;
typedef void*              HKEY;
typedef void*              LPVOID;
typedef const void*        LPCVOID;

typedef wchar_t      WCHAR;
typedef WCHAR*       LPWSTR;
typedef const WCHAR* LPCWSTR;
typedef char*        LPSTR;
typedef const char*  LPCSTR;

typedef struct { DWORD LowPart; LONG HighPart; long long QuadPart; } LARGE_INTEGER;
typedef struct { DWORD dwLowDateTime; DWORD dwHighDateTime; } FILETIME;
typedef struct { WORD wYear, wMonth, wDayOfWeek, wDay, wHour, wMinute, wSecond, wMilliseconds; } SYSTEMTIME;
typedef struct { DWORD dwFileAttributes; FILETIME ftCreationTime, ftLastAccessTime, ftLastWriteTime;
                 DWORD nFileSizeHigh, nFileSizeLow, dwReserved0, dwReserved1;
                 WCHAR cFileName[260]; WCHAR cAlternateFileName[14]; } WIN32_FIND_DATAW;
typedef struct { DWORD dwFileAttributes; FILETIME ftCreationTime, ftLastAccessTime, ftLastWriteTime;
                 DWORD nFileSizeHigh, nFileSizeLow; } WIN32_FILE_ATTRIBUTE_DATA;
typedef struct { DWORD nLength; LPVOID lpSecurityDescriptor; BOOL bInheritHandle; } SECURITY_ATTRIBUTES;
typedef struct { DWORD dwOSVersionInfoSize, dwMajorVersion, dwMinorVersion, dwBuildNumber, dwPlatformId;
                 WCHAR szCSDVersion[128]; } OSVERSIONINFOW;
typedef struct { DWORD dwOSVersionInfoSize, dwMajorVersion, dwMinorVersion, dwBuildNumber, dwPlatformId;
                 WCHAR szCSDVersion[128]; USHORT wServicePackMajor, wServicePackMinor, wSuiteMask;
                 BYTE wProductType, wReserved; } RTL_OSVERSIONINFOEXW;
typedef struct { DWORD dwSize; DWORD TokenIsElevated; } TOKEN_ELEVATION;
typedef struct { DWORD cb; LPWSTR lpReserved, lpDesktop, lpTitle;
                 DWORD dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars, dwFillAttribute, dwFlags;
                 WORD wShowWindow, cbReserved2; BYTE* lpReserved2;
                 HANDLE hStdInput, hStdOutput, hStdError; } STARTUPINFOW;
typedef struct { HANDLE hProcess, hThread; DWORD dwProcessId, dwThreadId; } PROCESS_INFORMATION;

#define TRUE 1
#define FALSE 0
#define INVALID_HANDLE_VALUE ((HANDLE)(intptr_t)-1)
#define INVALID_FILE_ATTRIBUTES ((DWORD)-1)
#define MAX_PATH 260

#define GENERIC_READ  0x80000000
#define GENERIC_WRITE 0x40000000
#define FILE_SHARE_READ  0x00000001
#define FILE_SHARE_WRITE 0x00000002
#define CREATE_ALWAYS 2
#define OPEN_EXISTING 3
#define OPEN_ALWAYS   4
#define FILE_ATTRIBUTE_NORMAL 0x00000080
#define FILE_ATTRIBUTE_DIRECTORY 0x00000010
#define FILE_END 2
#define FILE_BEGIN 0
#define HANDLE_FLAG_INHERIT 1
#define STARTF_USESTDHANDLES 0x00000100
#define STARTF_USESHOWWINDOW 0x00000001
#define SW_HIDE 0
#define SW_SHOWNORMAL 1
#define CREATE_NO_WINDOW 0x08000000
#define WAIT_TIMEOUT 258
#define INFINITE 0xFFFFFFFFu
#define GetFileExInfoStandard 0
#define WAIT_OBJECT_0 0
#define CP_UTF8 65001
#define CP_ACP 0
#define ERROR_SUCCESS 0
#define HKEY_LOCAL_MACHINE ((HKEY)(intptr_t)-2147483646)
#define HKEY_CURRENT_USER  ((HKEY)(intptr_t)-2147483647)
#define KEY_READ 0x20019
#define TOKEN_QUERY 0x0008
#define TokenElevation 20
#define RRF_RT_REG_SZ        0x00000002
#define RRF_RT_REG_EXPAND_SZ 0x00000004
#define RRF_RT_REG_DWORD     0x00000010
#define RRF_RT_REG_MULTI_SZ  0x00000020

HMODULE GetModuleHandleW(const wchar_t* name);
void*   GetProcAddress(HMODULE mod, const char* name);
DWORD   GetModuleFileNameW(HMODULE mod, wchar_t* out, DWORD cap);

HANDLE GetProcessHeap(void);
void*  HeapAlloc(HANDLE heap, DWORD flags, size_t bytes);
void*  HeapReAlloc(HANDLE heap, DWORD flags, void* p, size_t bytes);
BOOL   HeapFree(HANDLE heap, DWORD flags, void* p);

int  MultiByteToWideChar(UINT cp, DWORD flags, const char* src, int srcLen, wchar_t* dst, int dstCap);
int  WideCharToMultiByte(UINT cp, DWORD flags, const wchar_t* src, int srcLen, char* dst, int dstCap,
                         const char* defChar, BOOL* usedDefault);
UINT GetOEMCP(void);
UINT GetACP(void);

HANDLE CreateFileW(const wchar_t* path, DWORD access, DWORD share, void* sa, DWORD disposition,
                   DWORD flags, HANDLE tmpl);
BOOL   ReadFile(HANDLE h, void* buf, DWORD toRead, DWORD* read, void* overlapped);
BOOL   WriteFile(HANDLE h, const void* buf, DWORD toWrite, DWORD* written, void* overlapped);
DWORD  SetFilePointer(HANDLE h, LONG dist, LONG* distHigh, DWORD method);
BOOL   GetFileSizeEx(HANDLE h, LARGE_INTEGER* size);
BOOL   CloseHandle(HANDLE h);
BOOL   DeleteFileW(const wchar_t* path);
DWORD  GetFileAttributesW(const wchar_t* path);
BOOL   GetFileAttributesExW(const wchar_t* path, int level, void* data);
BOOL   CreateDirectoryW(const wchar_t* path, void* sa);
typedef struct { int placeholder; } CRITICAL_SECTION;
void InitializeCriticalSection(CRITICAL_SECTION* cs);
void EnterCriticalSection(CRITICAL_SECTION* cs);
void LeaveCriticalSection(CRITICAL_SECTION* cs);
void DeleteCriticalSection(CRITICAL_SECTION* cs);
LONG InterlockedExchange(volatile LONG* target, LONG value);
LONG InterlockedCompareExchange(volatile LONG* target, LONG exchange, LONG comparand);
HANDLE CreateThread(void* sa, size_t stack, DWORD (WINAPI *fn)(void*), void* param, DWORD flags, DWORD* id);

HANDLE FindFirstFileW(const wchar_t* mask, WIN32_FIND_DATAW* data);
BOOL   FindNextFileW(HANDLE h, WIN32_FIND_DATAW* data);
BOOL   FindClose(HANDLE h);
BOOL   CreatePipe(HANDLE* rd, HANDLE* wr, SECURITY_ATTRIBUTES* sa, DWORD size);
BOOL   SetHandleInformation(HANDLE h, DWORD mask, DWORD flags);
BOOL   PeekNamedPipe(HANDLE h, void* buf, DWORD bufSize, DWORD* read, DWORD* avail, DWORD* left);

BOOL   CreateProcessW(const wchar_t* app, wchar_t* cmd, void* pa, void* ta, BOOL inherit,
                      DWORD flags, void* env, const wchar_t* cwd, STARTUPINFOW* si, PROCESS_INFORMATION* pi);
BOOL   GetExitCodeProcess(HANDLE h, DWORD* code);
DWORD  WaitForSingleObject(HANDLE h, DWORD ms);
BOOL   TerminateProcess(HANDLE h, UINT code);
void   ExitProcess(UINT code);
HANDLE GetCurrentProcess(void);
DWORD  GetCurrentProcessId(void);
BOOL   OpenProcessToken(HANDLE proc, DWORD access, HANDLE* token);
BOOL   GetTokenInformation(HANDLE token, int cls, void* info, DWORD size, DWORD* ret);
BOOL   ProcessIdToSessionId(DWORD pid, DWORD* session);

BOOL   RegOpenKeyExW(HKEY root, const wchar_t* sub, DWORD opt, DWORD access, HKEY* out);
BOOL   RegQueryValueExW(HKEY key, const wchar_t* name, DWORD* res, DWORD* type, BYTE* data, DWORD* size);
LONG   RegCloseKey(HKEY key);
LONG   RegGetValueW(HKEY root, const wchar_t* sub, const wchar_t* name, DWORD flags,
                    DWORD* type, void* data, DWORD* size);

DWORD  GetEnvironmentVariableW(const wchar_t* name, wchar_t* out, DWORD cap);
DWORD  ExpandEnvironmentStringsW(const wchar_t* in, wchar_t* out, DWORD cap);
BOOL   GetComputerNameW(wchar_t* out, DWORD* cap);
BOOL   GetUserNameW(wchar_t* out, DWORD* cap);
void   GetLocalTime(SYSTEMTIME* st);
void   Sleep(DWORD ms);
BOOL   GetVersionExW(OSVERSIONINFOW* v);
HANDLE ShellExecuteW(HANDLE wnd, const wchar_t* op, const wchar_t* file, const wchar_t* params,
                     const wchar_t* dir, int show);

#define LOWORD(v) ((WORD)((UINT_PTR)(v) & 0xFFFF))
#define HIWORD(v) ((WORD)(((UINT_PTR)(v) >> 16) & 0xFFFF))

#ifdef __cplusplus
}
#endif
#endif /* ZI_SHIM_WINDOWS_H */
