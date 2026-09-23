# =============================================================================
#  ZI Office StartFix — восстановление меню «Пуск»
#  Поддержка: Windows 10, Windows 11, Windows Server 2012 / 2012 R2 / 2016 /
#             2019 / 2022 / 2025
#  Запускается утилитой ZI Office StartFix; может запускаться и вручную:
#     powershell -ExecutionPolicy Bypass -File zi-repair.ps1 -Mode diag
# =============================================================================
[CmdletBinding()]
param(
    [string]$Mode = 'diag',
    [string]$Log = '',
    [string]$Findings = '',
    [string]$BackupDir = '',
    [switch]$RestorePoint,
    [switch]$ResetLayout,
    [switch]$NoExplorer
)

$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'SilentlyContinue'

. (Join-Path $PSScriptRoot 'zi-common.ps1')

Initialize-ZiContext -Log $Log -Findings $Findings -BackupDir $BackupDir

$ZiAdmin = Test-ZiAdmin
$ZiOs    = Get-ZiOSInfo

# ----------------------------------------------------------------------------
# Вспомогательные проверки состояния меню «Пуск»
# ----------------------------------------------------------------------------
function Test-ZiStartMenuBlockers {
    Write-ZiStep 'Проверка известных «блокировщиков» меню «Пуск»'

    # 1. Оболочка в Winlogon
    $shell = Get-ZiRegValue -Path 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon' -Name 'Shell'
    if ($shell -and ($shell -notmatch 'explorer\.exe')) {
        Add-ZiFinding -Level 'CRIT' -Code 'WINLOGON_SHELL' -Text ("Оболочка системы подменена: Shell = '$shell' — меню «Пуск» и панель задач не работают")
    } else {
        Write-ZiInfo 'оболочка Winlogon: explorer.exe (норма)'
    }
    $userinit = Get-ZiRegValue -Path 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon' -Name 'Userinit'
    if ($userinit -and ($userinit -notmatch 'userinit\.exe')) {
        Add-ZiFinding -Level 'WARN' -Code 'WINLOGON_USERINIT' -Text ("Нестандартное значение Userinit: '$userinit'")
    }

    # 2. Отладчики процессов (IFEO) — классический способ блокировки оболочки
    $ifeoRoot = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options'
    $watched = @('explorer.exe', 'sihost.exe', 'StartMenuExperienceHost.exe',
                 'ShellExperienceHost.exe', 'SearchHost.exe', 'SearchApp.exe', 'Cortana.exe')
    $ifeoBad = @()
    foreach ($exe in $watched) {
        $key = Join-Path $ifeoRoot $exe
        $dbg = Get-ZiRegValue -Path $key -Name 'Debugger'
        if ($dbg) { $ifeoBad += ($exe + ' -> ' + $dbg) }
    }
    if ($ifeoBad.Count -gt 0) {
        Add-ZiFinding -Level 'CRIT' -Code 'IFEO_DEBUGGER' -Text ('Обнаружены перехваты запуска процессов (IFEO Debugger): ' + ($ifeoBad -join '; '))
    } else {
        Write-ZiInfo 'перехваты запуска процессов (IFEO): нет'
    }

    # 3. Внедряемые библиотеки
    foreach ($p in @('HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Windows',
                     'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows NT\CurrentVersion\Windows')) {
        $appinit = Get-ZiRegValue -Path $p -Name 'AppInit_DLLs'
        if ($appinit -and $appinit.Trim() -ne '') {
            Add-ZiFinding -Level 'WARN' -Code 'APPINIT_DLLS' -Text ("Внедряемые в оболочку библиотеки AppInit_DLLs = '$appinit'")
        }
    }

    # 4. Устаревшие параметры меню «Пуск» (ломают меню в Windows 10)
    $adv = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced'
    $xaml = Get-ZiRegValue -Path $adv -Name 'EnableXAMLStartMenu'
    if ($null -ne $xaml) {
        Add-ZiFinding -Level 'CRIT' -Code 'XAML_STARTMENU' -Text 'Параметр EnableXAMLStartMenu принудительно включает устаревшее меню «Пуск» — меню открываться не будет'
    }
    $classic = Get-ZiRegValue -Path $adv -Name 'Start_ShowClassicMode'
    if ($null -ne $classic -and [int]$classic -ne 0) {
        Add-ZiFinding -Level 'CRIT' -Code 'CLASSIC_START' -Text 'Параметр Start_ShowClassicMode включает классическое меню «Пуск» — в Windows 10/11 это ломает кнопку «Пуск»'
    }

    # 5. Политики оболочки
    foreach ($root in @('HKCU:', 'HKLM:')) {
        $pol = ($root + '\Software\Microsoft\Windows\CurrentVersion\Policies\Explorer')
        $nsm = Get-ZiRegValue -Path $pol -Name 'NoSimpleStartMenu'
        if ($null -ne $nsm) {
            Add-ZiFinding -Level 'INFO' -Code 'POLICY_NOSIMPLE' -Text ('Задан устаревший политики-параметр NoSimpleStartMenu в ' + $root + '\...\Policies\Explorer')
        }
        $nst = Get-ZiRegValue -Path $pol -Name 'NoStartMenuTiles'
        if ($null -ne $nst) {
            Add-ZiFinding -Level 'INFO' -Code 'POLICY_NOTILES' -Text 'Политика скрывает плитки меню «Пуск» (NoStartMenuTiles)'
        }
    }

    # 6. Контроль учётных записей
    $lua = Get-ZiRegValue -Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System' -Name 'EnableLUA'
    if ($null -ne $lua -and [int]$lua -eq 0) {
        Add-ZiFinding -Level 'WARN' -Code 'UAC_OFF' -Text 'Контроль учётных записей (UAC) отключён — это нарушает работу современных приложений, включая меню «Пуск»'
    }

    # 7. Сторонние средства замены меню «Пуск»
    $third = Get-ZiThirdPartyStartTools
    if ($third.Count -gt 0) {
        Add-ZiFinding -Level 'WARN' -Code 'THIRDPARTY_START' -Text ('Установлены сторонние программы, изменяющие меню «Пуск»: ' + ($third -join ', ') + ' — они часто и являются причиной неработающей кнопки «Пуск»')
    }
}

function Test-ZiStartMenuPackages {
    param([switch]$Quiet)
    Write-ZiStep 'Проверка системных приложений меню «Пуск»'

    $expected = @()
    if ($ZiOs.IsWin11) {
        $expected += 'Microsoft.Windows.StartMenuExperienceHost'
        $expected += 'Microsoft.Windows.Search'
    } elseif ($ZiOs.IsWin10) {
        $expected += 'Microsoft.Windows.ShellExperienceHost'
        $expected += 'Microsoft.Windows.StartMenuExperienceHost'
        $expected += 'Microsoft.Windows.Cortana'
        $expected += 'Microsoft.Windows.Search'
    } else {
        Write-ZiInfo 'для этой версии Windows проверка пакетов меню «Пуск» неприменима'
        return
    }

    foreach ($name in $expected) {
        $all = @(Get-ZiAppxPackage -Name $name -AllUsers)
        $me  = @(Get-ZiAppxPackage -Name $name)
        if ($all.Count -eq 0 -and $me.Count -eq 0) {
            if ($name -eq 'Microsoft.Windows.Search' -or $name -eq 'Microsoft.Windows.Cortana') {
                Add-ZiFinding -Level 'WARN' -Code 'PKG_MISSING' -Text ("Не найден пакет поиска: " + $name)
            } else {
                Add-ZiFinding -Level 'CRIT' -Code 'PKG_MISSING' -Text ("Отсутствует системный пакет меню «Пуск»: " + $name + " — переустановите Windows или используйте DISM")
            }
            continue
        }
        if ($me.Count -eq 0 -and $all.Count -gt 0) {
            Add-ZiFinding -Level 'CRIT' -Code 'PKG_NOT_REGISTERED' -Text ("Пакет " + $name + " не зарегистрирован для текущего пользователя — меню «Пуск» не запустится")
            continue
        }
        foreach ($p in $me) {
            $loc = $p.InstallLocation
            $st  = [string]$p.Status
            if (-not $loc -or -not (Test-Path -LiteralPath $loc)) {
                Add-ZiFinding -Level 'CRIT' -Code 'PKG_BROKEN' -Text ("Повреждена установка пакета " + $name + " (каталог недоступен: " + $loc + ")")
            } elseif ($st -and $st -ne 'Ok') {
                Add-ZiFinding -Level 'WARN' -Code 'PKG_STATUS' -Text ("Пакет " + $name + " в состоянии '" + $st + "' — требуется перерегистрация")
            } else {
                if (-not $Quiet) { Write-ZiInfo ("пакет " + $name + " (версия " + $p.Version + ") — в порядке") }
            }
        }
    }

    # Каталоги системных приложений
    $sysApps = Join-Path $env:WINDIR 'SystemApps'
    foreach ($d in @('Microsoft.Windows.ShellExperienceHost_cw5n1h2txyewy',
                     'Microsoft.Windows.StartMenuExperienceHost_cw5n1h2txyewy',
                     'Microsoft.Windows.Search_cw5n1h2txyewy')) {
        $path = Join-Path $sysApps $d
        if (Test-Path -LiteralPath $path) {
            if (-not (Test-Path -LiteralPath (Join-Path $path 'AppxManifest.xml'))) {
                Add-ZiFinding -Level 'CRIT' -Code 'SYSAPP_MANIFEST' -Text ("Повреждён каталог системного приложения: " + $d + " (нет AppxManifest.xml)")
            }
        }
    }
}

function Test-ZiShellProcesses {
    Write-ZiStep 'Проверка процессов оболочки'
    $n = Get-ZiProcessInfo -Name 'explorer'
    if ($n -eq 0) {
        Add-ZiFinding -Level 'CRIT' -Code 'NO_EXPLORER' -Text 'Процесс explorer.exe не запущен — оболочка Windows не работает'
    } else {
        Write-ZiInfo ("explorer.exe: процессов " + $n)
    }
    $start = Get-ZiProcessInfo -Name 'StartMenuExperienceHost'
    $shell = Get-ZiProcessInfo -Name 'ShellExperienceHost'
    $shost = Get-ZiProcessInfo -Name 'SearchHost'
    $sapp  = Get-ZiProcessInfo -Name 'SearchApp'
    Write-ZiInfo ("StartMenuExperienceHost: " + $start + ", ShellExperienceHost: " + $shell + ", SearchHost: " + $shost + ", SearchApp: " + $sapp)
    if (($start + $shell) -eq 0 -and $n -gt 0) {
        Add-ZiFinding -Level 'WARN' -Code 'NO_START_HOST' -Text 'Компоненты меню «Пуск» не запущены — они запустятся после перезапуска Проводника или входа в систему'
    }
    $sihost = Get-ZiProcessInfo -Name 'sihost'
    if ($sihost -eq 0 -and $n -gt 0) {
        Add-ZiFinding -Level 'WARN' -Code 'NO_SIHOST' -Text 'Процесс sihost.exe (Shell Infrastructure Host) не запущен — панель задач и меню «Пуск» могут не отображаться'
    }
}

# ----------------------------------------------------------------------------
# Режим: диагностика
# ----------------------------------------------------------------------------
function Invoke-ZiDiag {
    Write-ZiHdr 'ДИАГНОСТИКА МЕНЮ «ПУСК»'
    Write-ZiInfo ("система: " + $ZiOs.ProductName + " " + $ZiOs.Version + ", сборка " + $ZiOs.Build + "." + $ZiOs.UBR)
    Write-ZiInfo ("архитектура: " + $env:PROCESSOR_ARCHITECTURE + ", PowerShell " + $PSVersionTable.PSVersion.ToString())
    Write-ZiInfo ("права администратора: " + $(if ($ZiAdmin) { 'да' } else { 'НЕТ' }))
    Write-ZiInfo ("пользователь: " + $env:USERDOMAIN + "\" + $env:USERNAME)
    Write-ZiInfo ("рабочая станция: " + $env:COMPUTERNAME)
    if (-not $ZiAdmin) {
        Add-ZiFinding -Level 'CRIT' -Code 'NO_ADMIN' -Text 'Утилита запущена без прав администратора — ремонт будет неполным'
    }

    $free = Get-ZiFreeSpaceGB
    if ($free -ge 0) {
        Write-ZiInfo ("свободно на системном диске: " + $free + " ГБ")
        if ($free -lt 2) {
            Add-ZiFinding -Level 'CRIT' -Code 'LOW_DISK' -Text ("На системном диске свободно менее 2 ГБ (" + $free + " ГБ) — приложения и меню «Пуск» могут не запускаться")
        } elseif ($free -lt 5) {
            Add-ZiFinding -Level 'WARN' -Code 'LOW_DISK' -Text ("Мало свободного места на системном диске: " + $free + " ГБ")
        }
    }

    $skew = Get-ZiClockSkew
    if ($skew) {
        Add-ZiFinding -Level 'CRIT' -Code 'CLOCK_SKEW' -Text ("Обнаружен перекос системных часов (файлы с датой " + $skew + " в будущем). Неверное время ломает запуск приложений меню «Пуск» — синхронизируйте время")
    } else {
        Write-ZiInfo 'перекос системных часов: не обнаружен'
    }

    $pending = Get-ZiPendingReboot
    if ($pending.Count -gt 0) {
        Add-ZiFinding -Level 'WARN' -Code 'PENDING_REBOOT' -Text ('Требуется перезагрузка: ' + ($pending -join ', '))
    }

    Test-ZiShellProcesses
    Test-ZiStartMenuPackages
    Test-ZiStartMenuBlockers

    Write-ZiStep 'Проверка служб, от которых зависит меню «Пуск»'
    $bad = @()
    foreach ($s in $script:ZiServiceMap) {
        $st = Get-ZiServiceState -Name $s.Name
        if (-not $st.Exists) { continue }
        if ($null -ne $st.Start -and [int]$st.Start -eq 4) {
            $bad += ($s.Name + ' (отключена)')
        }
    }
    if ($bad.Count -gt 0) {
        Add-ZiFinding -Level 'CRIT' -Code 'SERVICES_DISABLED' -Text ('Отключены службы, нужные для работы меню «Пуск»: ' + ($bad -join ', '))
    } else {
        Write-ZiInfo 'критичные службы включены'
    }
    foreach ($s in @('AppXSvc', 'StateRepository', 'CoreMessagingRegistrar', 'WSearch')) {
        $st = Get-ZiServiceState -Name $s
        if ($st.Exists -and $st.Status -ne 'Running') { Write-ZiInfo ("служба " + $s + ": " + $st.Status) }
    }

    Write-ZiStep 'Проверка журнала сбоев приложений (последние 14 дней)'
    $crashes = Get-ZiCrashEvents -Days 14
    if ($crashes.Count -gt 0) {
        Add-ZiFinding -Level 'WARN' -Code 'APP_CRASH' -Text ('Зафиксированы падения компонентов оболочки: ' + $crashes.Count + ' событий, последнее — ' + $crashes[0].Time)
        $i = 0
        foreach ($c in $crashes) {
            if ($i -ge 5) { break }
            Write-ZiInfo ('  ' + $c.Time + ' — ' + $c.Text)
            $i++
        }
    } else {
        Write-ZiInfo 'сбоев компонентов оболочки не найдено'
    }

    Write-ZiStep 'Итог диагностики'
    Write-ZiInfo ("критичных проблем: " + $script:ZiCritCount + ", предупреждений: " + $script:ZiWarnCount)
    if ($script:ZiCritCount -eq 0 -and $script:ZiWarnCount -eq 0) {
        Add-ZiFinding -Level 'OK' -Code 'DIAG_CLEAN' -Text 'Явных причин неработоспособности меню «Пуск» не найдено. Рекомендуется выполнить «Полный ремонт» и проверить систему в новой учётной записи.'
    } else {
        Add-ZiFinding -Level 'INFO' -Code 'DIAG_NEXT' -Text 'Рекомендуется выполнить «Полный ремонт»: он устранит найденные проблемы автоматически.'
    }
}

# ----------------------------------------------------------------------------
# Режим: резервные копии
# ----------------------------------------------------------------------------
function Invoke-ZiBackup {
    Write-ZiHdr 'РЕЗЕРВНОЕ КОПИРОВАНИЕ ПАРАМЕТРОВ'
    if (-not $script:ZiBackupDir) {
        Add-ZiFinding -Level 'WARN' -Code 'BACKUP_NODIR' -Text 'Не задан каталог резервных копий — резервное копирование пропущено'
        return
    }
    $items = @(
        @{ P = 'HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced'; N = 'hkcu_explorer_advanced' },
        @{ P = 'HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Taskband'; N = 'hkcu_taskband' },
        @{ P = 'HKCU\Software\Microsoft\Windows\CurrentVersion\CloudStore';       N = 'hkcu_cloudstore' },
        @{ P = 'HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\Explorer';N = 'hkcu_policies_explorer' },
        @{ P = 'HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\Explorer';N = 'hklm_policies_explorer' }
    )
    $done = 0
    foreach ($it in $items) {
        if (Backup-ZiRegKey -Path $it.P -Name $it.N) {
            $done++
            $manifest = Join-Path $script:ZiBackupDir 'actions.txt'
            Add-Content -LiteralPath $manifest -Value ('REGIMPORT`t' + (Join-Path $script:ZiBackupDir ($it.N + '.reg'))) -Encoding UTF8 -ErrorAction SilentlyContinue
        }
    }
    # отдельные значения Winlogon сохраняем текстом (без секретов)
    try {
        $wl = @()
        $wl += 'Shell = ' + [string](Get-ZiRegValue -Path 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon' -Name 'Shell')
        $wl += 'Userinit = ' + [string](Get-ZiRegValue -Path 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon' -Name 'Userinit')
        $wl | Out-File -LiteralPath (Join-Path $script:ZiBackupDir 'winlogon-values.txt') -Encoding UTF8 -Force
    } catch { }
    Write-ZiOk ("создано резервных копий: " + $done + " (папка " + $script:ZiBackupDir + ")")
    Add-ZiFinding -Level 'OK' -Code 'BACKUP_DONE' -Text ('Резервные копии параметров сохранены: ' + $done + ' файлов в ' + $script:ZiBackupDir)
}

# ----------------------------------------------------------------------------
# Режим: точка восстановления
# ----------------------------------------------------------------------------
function Invoke-ZiRestorePoint {
    Write-ZiHdr 'ТОЧКА ВОССТАНОВЛЕНИЯ СИСТЕМЫ'
    if (-not $ZiAdmin) {
        Write-ZiWarn 'нужны права администратора — пропущено'
        return
    }
    try {
        Checkpoint-Computer -Description 'ZI Office StartFix — перед ремонтом меню «Пуск»' -RestorePointType 'MODIFY_SETTINGS' -ErrorAction Stop
        Write-ZiOk 'точка восстановления создана'
        Add-ZiFinding -Level 'OK' -Code 'RESTOREPOINT' -Text 'Точка восстановления системы создана'
    } catch {
        Write-ZiWarn ('точку восстановления создать не удалось: ' + $_.Exception.Message)
        Add-ZiFinding -Level 'WARN' -Code 'RESTOREPOINT_FAIL' -Text 'Точка восстановления не создана (защита системы отключена или сработало ограничение в 24 часа). Резервные копии реестра всё равно созданы.'
    }
}

# ----------------------------------------------------------------------------
# Режим: исправление реестра
# ----------------------------------------------------------------------------
function Invoke-ZiFixReg {
    Write-ZiHdr 'ИСПРАВЛЕНИЕ ПАРАМЕТРОВ РЕЕСТРА'
    if (-not $ZiAdmin) {
        Add-ZiFinding -Level 'WARN' -Code 'NO_ADMIN_REG' -Text 'Нет прав администратора: правка системных ветвей реестра недоступна'
    }

    # 1. Оболочка в Winlogon
    if ($ZiAdmin) {
        $wlPath = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon'
        $shell = Get-ZiRegValue -Path $wlPath -Name 'Shell'
        if ($shell -and ($shell -notmatch 'explorer\.exe')) {
            Write-ZiStep 'Восстановление оболочки explorer.exe'
            Set-ZiRegValue -Path $wlPath -Name 'Shell' -Value 'explorer.exe' -Type String
            Add-ZiFinding -Level 'OK' -Code 'WINLOGON_FIXED' -Text 'Оболочка системы возвращена на explorer.exe'
        }
        $userinit = Get-ZiRegValue -Path $wlPath -Name 'Userinit'
        if ($userinit -and ($userinit -notmatch 'userinit\.exe')) {
            Set-ZiRegValue -Path $wlPath -Name 'Userinit' -Value 'C:\Windows\system32\userinit.exe,' -Type String
            Add-ZiFinding -Level 'OK' -Code 'USERINIT_FIXED' -Text 'Значение Userinit восстановлено'
        }
    }

    # 2. Перехваты IFEO
    if ($ZiAdmin) {
        Write-ZiStep 'Проверка и удаление перехватов запуска процессов (IFEO)'
        $ifeoRoot = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options'
        $watched = @('explorer.exe', 'sihost.exe', 'StartMenuExperienceHost.exe',
                     'ShellExperienceHost.exe', 'SearchHost.exe', 'SearchApp.exe', 'Cortana.exe')
        $fixed = 0
        foreach ($exe in $watched) {
            $keyPath = Join-Path $ifeoRoot $exe
            $dbg = Get-ZiRegValue -Path $keyPath -Name 'Debugger'
            if ($dbg) {
                Backup-ZiRegKey -Path ('HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options\' + $exe) -Name ('ifeo_' + $exe)
                Remove-ZiRegValue -Path $keyPath -Name 'Debugger' -NoLog | Out-Null
                $fixed++
                Add-ZiFinding -Level 'OK' -Code 'IFEO_FIXED' -Text ('Удалён перехват запуска: ' + $exe + ' (был отладчик ' + $dbg + ')')
            }
        }
        if ($fixed -eq 0) { Write-ZiInfo 'перехватов запуска не найдено' }

        # 3. Внедряемые библиотеки
        foreach ($p in @('HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Windows',
                         'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows NT\CurrentVersion\Windows')) {
            $appinit = Get-ZiRegValue -Path $p -Name 'AppInit_DLLs'
            if ($appinit -and $appinit.Trim() -ne '') {
                Backup-ZiRegKey -Path ($p -replace 'HKLM:\\', 'HKLM\') -Name 'appinit_dlls'
                Set-ZiRegValue -Path $p -Name 'AppInit_DLLs' -Value '' -Type String
                Add-ZiFinding -Level 'OK' -Code 'APPINIT_FIXED' -Text 'Отключено внедрение сторонних библиотек в оболочку (AppInit_DLLs)'
            }
        }
    }

    # 4. Устаревшие параметры меню «Пуск»
    Write-ZiStep 'Очистка устаревших параметров меню «Пуск»'
    $adv = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced'
    if (Remove-ZiRegValue -Path $adv -Name 'EnableXAMLStartMenu' -Backup) {
        Add-ZiFinding -Level 'OK' -Code 'XAML_FIXED' -Text 'Удалён параметр EnableXAMLStartMenu (включал устаревшее меню «Пуск»)'
    }
    if (Remove-ZiRegValue -Path $adv -Name 'Start_ShowClassicMode' -Backup) {
        Add-ZiFinding -Level 'OK' -Code 'CLASSIC_FIXED' -Text 'Удалён параметр Start_ShowClassicMode (классическое меню «Пуск»)'
    }
    if (Remove-ZiRegValue -Path $adv -Name 'TaskbarSmallIcons' -Backup) {
        Add-ZiFinding -Level 'OK' -Code 'TASKBAR_SMALLICONS' -Text 'Удалён устаревший параметр TaskbarSmallIcons'
    }
    Write-ZiInfo 'устаревшие параметры очищены'

    # 5. Служебные пометки о состоянии пользовательского интерфейса
    Write-ZiStep 'Проверка состояния плиток и кэша меню «Пуск»'
    $cs = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\CloudStore\Store\Cache\DefaultAccount'
    if (Test-Path -LiteralPath $cs) {
        Write-ZiInfo 'кэш плиток (CloudStore) присутствует — при необходимости будет сброшен в режиме «Сброс раскладки»'
    }

    Add-ZiFinding -Level 'OK' -Code 'REG_DONE' -Text 'Основные параметры реестра проверены и приведены в порядок'
}

# ----------------------------------------------------------------------------
# Режим: службы
# ----------------------------------------------------------------------------
function Invoke-ZiFixServices {
    Write-ZiHdr 'ПРОВЕРКА И ВОССТАНОВЛЕНИЕ СЛУЖБ'
    if (-not $ZiAdmin) {
        Add-ZiFinding -Level 'WARN' -Code 'NO_ADMIN_SVC' -Text 'Нет прав администратора: изменение служб недоступно'
        return
    }
    $fixedStart = 0
    $started = 0
    foreach ($s in $script:ZiServiceMap) {
        $st = Get-ZiServiceState -Name $s.Name
        if (-not $st.Exists) { continue }
        if ($null -ne $st.Start -and [int]$st.Start -eq 4) {
            if (Set-ZiServiceStartType -Name $s.Name -StartType $s.Start) {
                $fixedStart++
                Add-ZiFinding -Level 'OK' -Code 'SVC_ENABLED' -Text ('Включена отключённая служба ' + $s.Name + ' (' + $s.Role + ')')
            } else {
                Add-ZiFinding -Level 'WARN' -Code 'SVC_FAIL' -Text ('Не удалось включить службу ' + $s.Name)
            }
        }
        if ($st.Status -ne 'Running' -and $s.Start -ne 'demand') {
            Write-ZiStep ('Запуск службы ' + $s.Name + ' (' + $s.Role + ')')
            if (Start-ZiServiceSafe -Name $s.Name) { $started++ }
            else { Write-ZiInfo ('служба ' + $s.Name + ' не запущена') }
        }
    }
    Write-ZiInfo ("запущено служб: " + $started + ", исправлено типов запуска: " + $fixedStart)
    if ($fixedStart -gt 0) {
        Add-ZiFinding -Level 'OK' -Code 'SVC_DONE' -Text ('Восстановлены типы запуска служб: ' + $fixedStart)
    } else {
        Add-ZiFinding -Level 'OK' -Code 'SVC_DONE' -Text 'Критичные службы настроены корректно'
    }
}

# ----------------------------------------------------------------------------
# Режим: меню «Пуск» (перерегистрация компонентов)
# ----------------------------------------------------------------------------
function Invoke-ZiFixStart {
    Write-ZiHdr 'ВОССТАНОВЛЕНИЕ КОМПОНЕНТОВ МЕНЮ «ПУСК»'
    if (-not $ZiAdmin) {
        Add-ZiFinding -Level 'WARN' -Code 'NO_ADMIN_START' -Text 'Нет прав администратора: перерегистрация системных приложений может быть неполной'
    }

    $targets = @()
    if ($ZiOs.IsWin11) {
        $targets += 'Microsoft.Windows.StartMenuExperienceHost'
        $targets += 'Microsoft.Windows.ShellExperienceHost'
    } elseif ($ZiOs.IsWin10) {
        $targets += 'Microsoft.Windows.ShellExperienceHost'
        $targets += 'Microsoft.Windows.StartMenuExperienceHost'
    } else {
        Write-ZiInfo 'система старше Windows 10 — классическое меню «Пуск»; проверяется Проводник и оболочка'
    }

    foreach ($name in $targets) {
        Write-ZiStep ('Перерегистрация приложения ' + $name)
        $reset = Reset-ZiAppxPackage -Name $name
        $reg = Repair-ZiAppxPackage -Name $name -AllUsers
        if ($reg) {
            Add-ZiFinding -Level 'OK' -Code 'PKG_REREG' -Text ('Компонент меню «Пуск» перерегистрирован: ' + $name)
        } elseif (-not $reset) {
            Add-ZiFinding -Level 'WARN' -Code 'PKG_REREG_FAIL' -Text ('Не удалось перерегистрировать ' + $name + ' штатными средствами')
        }
    }

    # повторная регистрация напрямую из каталога SystemApps
    $sysApps = Join-Path $env:WINDIR 'SystemApps'
    foreach ($d in @('Microsoft.Windows.ShellExperienceHost_cw5n1h2txyewy',
                     'Microsoft.Windows.StartMenuExperienceHost_cw5n1h2txyewy')) {
        $manifest = Join-Path (Join-Path $sysApps $d) 'AppxManifest.xml'
        if (Test-Path -LiteralPath $manifest) {
            if (Register-ZiPackagePath -ManifestPath $manifest) {
                Add-ZiFinding -Level 'OK' -Code 'PKG_SYSAPP' -Text ('Зарегистрирован системный компонент: ' + $d)
            }
        }
    }

    # дополнительно: оболочка пользователя и «Рабочий стол»
    foreach ($name in @('Microsoft.Windows.ShellExperienceHost', 'Microsoft.Windows.StartMenuExperienceHost')) {
        $pkgs = @(Get-ZiAppxPackage -Name $name)
        if ($pkgs.Count -eq 0) { continue }
        foreach ($p in $pkgs) {
            $ls = Join-Path $p.InstallLocation 'AppxManifest.xml'
            if (-not (Test-Path -LiteralPath $ls)) { continue }
            Write-ZiInfo ('проверен ' + $name + ': ' + $p.InstallLocation)
        }
    }

    Add-ZiFinding -Level 'OK' -Code 'START_DONE' -Text 'Компоненты меню «Пуск» перерегистрированы. Требуется перезапуск Проводника или перезагрузка.'
}

# ----------------------------------------------------------------------------
# Режим: сброс раскладки и кэша меню «Пуск»
# ----------------------------------------------------------------------------
function Invoke-ZiResetLayout {
    Write-ZiHdr 'СБРОС РАСКЛАДКИ И КЭША МЕНЮ «ПУСК»'
    Write-ZiWarn 'Внимание: закреплённые плитки и настройки меню «Пуск» вернутся к состоянию по умолчанию.'

    # остановить компоненты оболочки, чтобы освободить файлы
    foreach ($p in @('StartMenuExperienceHost', 'ShellExperienceHost', 'SearchHost', 'SearchApp', 'TextInputHost', 'sihost')) {
        try { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue } catch { }
    }
    Start-Sleep -Milliseconds 800

    $userLocal = $env:LOCALAPPDATA
    $targets = @(
        (Join-Path $userLocal 'Packages\Microsoft.Windows.StartMenuExperienceHost_cw5n1h2txyewy\LocalState'),
        (Join-Path $userLocal 'Packages\Microsoft.Windows.StartMenuExperienceHost_cw5n1h2txyewy\TempState'),
        (Join-Path $userLocal 'Packages\Microsoft.Windows.ShellExperienceHost_cw5n1h2txyewy\LocalState'),
        (Join-Path $userLocal 'Packages\Microsoft.Windows.ShellExperienceHost_cw5n1h2txyewy\TempState'),
        (Join-Path $userLocal 'Microsoft\Windows\AppsFolder.menu.itemdata-ms'),
        (Join-Path $userLocal 'Microsoft\Windows\AppsFolder.itemdata-ms')
    )
    $done = 0
    foreach ($t in $targets) {
        if (Move-ZiFolderAside -Path $t -Suffix 'reset') { $done++ }
    }

    # кэш плиток в реестре
    $cloudStore = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\CloudStore\Store\Cache\DefaultAccount'
    if (Test-Path -LiteralPath $cloudStore) {
        $regPath = 'HKCU\Software\Microsoft\Windows\CurrentVersion\CloudStore\Store\Cache\DefaultAccount'
        if (Backup-ZiRegKey -Path $regPath -Name 'cloudstore_defaultaccount') {
            $manifest = Join-Path $script:ZiBackupDir 'actions.txt'
            Add-Content -LiteralPath $manifest -Value ('REGIMPORT`t' + (Join-Path $script:ZiBackupDir 'cloudstore_defaultaccount.reg')) -Encoding UTF8 -ErrorAction SilentlyContinue
        }
        try {
            Remove-Item -LiteralPath $cloudStore -Recurse -Force -ErrorAction Stop
            $done++
            Write-ZiOk 'кэш плиток меню «Пуск» (CloudStore) сброшен'
        } catch {
            Write-ZiWarn ('не удалось сбросить кэш плиток: ' + $_.Exception.Message)
        }
    }

    # панель задач (закреплённые значки) — только при полном сбросе
    $taskband = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Taskband'
    if (Test-Path -LiteralPath $taskband) {
        if (Backup-ZiRegKey -Path 'HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Taskband' -Name 'taskband') {
            try {
                Remove-Item -LiteralPath $taskband -Recurse -Force -ErrorAction Stop
                $done++
                Write-ZiOk 'раскладка панели задач сброшена'
            } catch { Write-ZiWarn 'не удалось сбросить раскладку панели задач' }
        }
    }

    if ($done -gt 0) {
        Add-ZiFinding -Level 'OK' -Code 'LAYOUT_RESET' -Text ('Сброшено элементов раскладки меню «Пуск»: ' + $done + '. Настройки меню «Пуск» вернутся к состоянию по умолчанию.')
    } else {
        Add-ZiFinding -Level 'INFO' -Code 'LAYOUT_NONE' -Text 'Элементы раскладки меню «Пуск» не найдены — сбрасывать нечего'
    }
}

# ----------------------------------------------------------------------------
# Режим: поиск
# ----------------------------------------------------------------------------
function Invoke-ZiFixSearch {
    Write-ZiHdr 'ВОССТАНОВЛЕНИЕ ПОИСКА И КОМПОНЕНТОВ ОБОЛОЧКИ'

    Write-ZiStep 'Перезапуск службы Windows Search (WSearch)'
    if ($ZiAdmin) {
        $st = Get-ZiServiceState -Name 'WSearch'
        if ($st.Exists) {
            if ($null -ne $st.Start -and [int]$st.Start -eq 4) {
                if (Set-ZiServiceStartType -Name 'WSearch' -StartType 'auto') {
                    Add-ZiFinding -Level 'OK' -Code 'WSEARCH_ENABLED' -Text 'Служба Windows Search была отключена — включено автоматическое обновление'
                }
            }
            try { & sc.exe stop WSearch 2>&1 | Out-Null } catch { }
            Start-Sleep -Seconds 2
            if (Start-ZiServiceSafe -Name 'WSearch') {
                Write-ZiOk 'служба Windows Search перезапущена'
            } else {
                Write-ZiWarn 'служба Windows Search не запустилась (возможно, требуется перезагрузка)'
                Add-ZiFinding -Level 'WARN' -Code 'WSEARCH_FAIL' -Text 'Не удалось запустить службу Windows Search'
            }
        }
    }

    Write-ZiStep 'Перерегистрация компонентов поиска'
    foreach ($name in @('Microsoft.Windows.Search', 'Microsoft.Windows.Cortana')) {
        $pkgs = @(Get-ZiAppxPackage -Name $name)
        if ($pkgs.Count -eq 0) { continue }
        if (Reset-ZiAppxPackage -Name $name) { } 
        if (Repair-ZiAppxPackage -Name $name -AllUsers) {
            Add-ZiFinding -Level 'OK' -Code 'SEARCH_REREG' -Text ('Перерегистрирован компонент поиска: ' + $name)
        }
    }

    Write-ZiStep 'Сброс пользовательского состояния поиска'
    foreach ($p in @('SearchHost', 'SearchApp', 'SearchUI', 'Cortana')) {
        try { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue } catch { }
    }
    Start-Sleep -Milliseconds 800
    $userLocal = $env:LOCALAPPDATA
    $targets = @(
        (Join-Path $userLocal 'Packages\Microsoft.Windows.Search_cw5n1h2txyewy\LocalState'),
        (Join-Path $userLocal 'Packages\Microsoft.Windows.Cortana_cw5n1h2txyewy\LocalState')
    )
    $done = 0
    foreach ($t in $targets) {
        if (Move-ZiFolderAside -Path $t -Suffix 'reset') { $done++ }
    }
    if ($done -gt 0) {
        Add-ZiFinding -Level 'OK' -Code 'SEARCH_RESET' -Text ('Сброшено состояние поиска (' + $done + ' элементов)')
    } else {
        Write-ZiInfo 'состояние поиска сбрасывать не требуется'
    }
    Add-ZiFinding -Level 'OK' -Code 'SEARCH_DONE' -Text 'Компоненты поиска проверены и восстановлены'
}

# ----------------------------------------------------------------------------
# Режим: перезапуск Проводника
# ----------------------------------------------------------------------------
function Invoke-ZiFixExplorer {
    Write-ZiHdr 'ПЕРЕЗАПУСК ПРОВОДНИКА (ОБОЛОЧКИ)'
    Write-ZiStep 'Остановка процессов оболочки'
    foreach ($p in @('StartMenuExperienceHost', 'ShellExperienceHost', 'SearchHost', 'SearchApp', 'Cortana', 'sihost', 'TextInputHost')) {
        try { Stop-Process -Name $p -Force -ErrorAction SilentlyContinue } catch { }
    }
    try {
        $n = @(Get-Process -Name 'explorer' -ErrorAction SilentlyContinue).Count
        if ($n -gt 0) {
            Stop-Process -Name 'explorer' -Force -ErrorAction SilentlyContinue
            Write-ZiInfo 'проводник остановлен'
        }
    } catch { }
    Start-Sleep -Seconds 2

    Write-ZiStep 'Запуск Проводника'
    $explorer = Join-Path $env:WINDIR 'explorer.exe'
    if (-not (Test-Path -LiteralPath $explorer)) {
        Add-ZiFinding -Level 'CRIT' -Code 'NO_EXPLORER_FILE' -Text 'Файл explorer.exe не найден — система сильно повреждена (возможно, Server Core)'
        return
    }
    $started = $false
    if ($ZiAdmin) {
        # Проводник должен работать без повышения прав
        try {
            Start-Process -FilePath (Join-Path $env:WINDIR 'System32\runas.exe') -ArgumentList '/trustlevel:0x20000', $explorer -WindowStyle Hidden -ErrorAction Stop
            Start-Sleep -Seconds 2
            $started = (@(Get-Process -Name 'explorer' -ErrorAction SilentlyContinue).Count -gt 0)
        } catch { }
    }
    if (-not $started) {
        try {
            Start-Process -FilePath $explorer -ErrorAction Stop | Out-Null
            Start-Sleep -Seconds 2
            $started = (@(Get-Process -Name 'explorer' -ErrorAction SilentlyContinue).Count -gt 0)
        } catch { }
    }
    if ($started) {
        Write-ZiOk 'Проводник запущен'
        Add-ZiFinding -Level 'OK' -Code 'EXPLORER_RESTARTED' -Text 'Проводник (панель задач и меню «Пуск») перезапущен'
    } else {
        Write-ZiWarn 'Проводник не запустился автоматически'
        Add-ZiFinding -Level 'WARN' -Code 'EXPLORER_FAIL' -Text 'Проводник не запустился: запустите explorer.exe вручную (Ctrl+Shift+Esc → Файл → Запустить новую задачу) или перезагрузите компьютер'
    }
}

# ----------------------------------------------------------------------------
# Режим: SFC
# ----------------------------------------------------------------------------
function Invoke-ZiSfc {
    Write-ZiHdr 'ПРОВЕРКА ЦЕЛОСТНОСТИ СИСТЕМНЫХ ФАЙЛОВ (SFC)'
    Write-ZiInfo 'Операция может занять от 10 до 40 минут. Не прерывайте её.'
    $sfc = Join-Path $env:WINDIR 'System32\sfc.exe'
    if (-not (Test-Path -LiteralPath $sfc)) {
        Add-ZiFinding -Level 'WARN' -Code 'SFC_MISSING' -Text 'Не найден sfc.exe'
        return
    }
    Write-ZiStep 'Запуск sfc /scannow'
    $start = Get-Date
    try {
        & $sfc /scannow 2>&1 | ForEach-Object {
            $line = [string]$_
            if ($line -and $line.Trim() -ne '') { Write-Zi ('      ' + $line.Trim()) }
        }
    } catch {
        Write-ZiWarn ('ошибка запуска sfc: ' + $_.Exception.Message)
    }
    $mins = [math]::Round(((Get-Date) - $start).TotalMinutes, 1)
    Write-ZiInfo ('время выполнения: ' + $mins + ' мин.')

    Write-ZiStep 'Анализ журнала CBS'
    $cbs = Join-Path $env:WINDIR 'Logs\CBS\CBS.log'
    $bad = @()
    $repaired = @()
    try {
        $tail = Get-Content -LiteralPath $cbs -Tail 4000 -ErrorAction Stop
        foreach ($l in $tail) {
            if ($l -match '\[SR\]') {
                if ($l -match 'Cannot repair|не удается восстановить|repairing file|не удалось восстановить') {
                    if ($l -match 'Cannot repair|не удается восстановить') { $bad += $l }
                    else { $repaired += $l }
                }
            }
        }
    } catch { Write-ZiInfo 'журнал CBS недоступен для чтения' }

    if ($bad.Count -gt 0) {
        Add-ZiFinding -Level 'CRIT' -Code 'SFC_UNREPAIRED' -Text ('SFC обнаружил неустранимые повреждения (' + $bad.Count + ' записей в CBS.log). Выполните «DISM /RestoreHealth», затем повторите SFC.')
        $i = 0
        foreach ($b in $bad) { if ($i -ge 5) { break }; Write-ZiInfo ('  ' + $b); $i++ }
    } elseif ($repaired.Count -gt 0) {
        Add-ZiFinding -Level 'OK' -Code 'SFC_REPAIRED' -Text ('SFC восстановил повреждённые системные файлы (' + $repaired.Count + ' записей)')
    } else {
        Add-ZiFinding -Level 'OK' -Code 'SFC_CLEAN' -Text 'Повреждений системных файлов не обнаружено (SFC не нашёл нарушений целостности)'
    }
}

# ----------------------------------------------------------------------------
# Режим: DISM
# ----------------------------------------------------------------------------
function Invoke-ZiDism {
    Write-ZiHdr 'ВОССТАНОВЛЕНИЕ ОБРАЗА СИСТЕМЫ (DISM)'
    if (-not $ZiAdmin) {
        Add-ZiFinding -Level 'WARN' -Code 'NO_ADMIN_DISM' -Text 'DISM требует прав администратора'
        return
    }
    $dism = Join-Path $env:WINDIR 'System32\Dism.exe'
    if (-not (Test-Path -LiteralPath $dism)) {
        Add-ZiFinding -Level 'WARN' -Code 'DISM_MISSING' -Text 'Не найден Dism.exe'
        return
    }

    Write-ZiStep 'Проверка состояния образа (ScanHealth)'
    $scanCode = 0
    try {
        & $dism /Online /Cleanup-Image /ScanHealth 2>&1 | ForEach-Object {
            $line = [string]$_
            if ($line -and $line.Trim() -ne '') { Write-Zi ('      ' + $line.Trim()) }
        }
        $scanCode = $LASTEXITCODE
    } catch { Write-ZiWarn ('ошибка ScanHealth: ' + $_.Exception.Message) }

    Write-ZiStep 'Восстановление образа (RestoreHealth)'
    $code = 0
    try {
        & $dism /Online /Cleanup-Image /RestoreHealth 2>&1 | ForEach-Object {
            $line = [string]$_
            if ($line -and $line.Trim() -ne '') { Write-Zi ('      ' + $line.Trim()) }
        }
        $code = $LASTEXITCODE
    } catch { Write-ZiWarn ('ошибка RestoreHealth: ' + $_.Exception.Message) }

    if ($code -eq 0) {
        Add-ZiFinding -Level 'OK' -Code 'DISM_OK' -Text 'Восстановление образа системы завершено успешно'
        Write-ZiStep 'Контрольная проверка SFC после DISM'
        try {
            & (Join-Path $env:WINDIR 'System32\sfc.exe') /scannow 2>&1 | ForEach-Object {
                $line = [string]$_
                if ($line -and $line.Trim() -ne '') { Write-Zi ('      ' + $line.Trim()) }
            }
        } catch { }
    } else {
        Add-ZiFinding -Level 'CRIT' -Code 'DISM_FAIL' -Text ('DISM завершился с кодом ' + $code + '. Если ошибка 0x800f0906/0x800f081f — системе не хватает исходных файлов: подключите Интернет или укажите источник установки вручную (DISM /Source).')
    }
    if ($scanCode -ne 0) {
        Write-ZiInfo ('ScanHealth вернул код ' + $scanCode)
    }
}

# ----------------------------------------------------------------------------
# Режим: перерегистрация всех приложений
# ----------------------------------------------------------------------------
function Invoke-ZiApps {
    Write-ZiHdr 'ПЕРЕРЕГИСТРАЦИЯ ВСЕХ ПРИЛОЖЕНИЙ WINDOWS'
    Write-ZiInfo 'Операция длительная. Сообщения об ошибках для отдельных приложений — обычное дело.'
    $pkgs = @()
    try { $pkgs = @(Get-AppxPackage -ErrorAction SilentlyContinue) } catch { }
    if ($pkgs.Count -eq 0) {
        Add-ZiFinding -Level 'WARN' -Code 'APPS_NONE' -Text 'Список приложений пуст или недоступен'
        return
    }
    Write-ZiInfo ('найдено приложений: ' + $pkgs.Count)
    $ok = 0
    $fail = 0
    $i = 0
    foreach ($p in $pkgs) {
        $i++
        if (-not $p.InstallLocation) { continue }
        $manifest = Join-Path $p.InstallLocation 'AppxManifest.xml'
        if (-not (Test-Path -LiteralPath $manifest)) { continue }
        try {
            Add-AppxPackage -DisableDevelopmentMode -Register $manifest -ErrorAction Stop
            $ok++
            if (($i % 10) -eq 0) { Write-ZiInfo ('обработано ' + $i + ' из ' + $pkgs.Count + ' (успешно ' + $ok + ')') }
        } catch {
            $fail++
        }
    }
    Add-ZiFinding -Level 'OK' -Code 'APPS_DONE' -Text ('Перерегистрация приложений завершена: успешно ' + $ok + ', с ошибками ' + $fail + ' (ошибки отдельных приложений допустимы)')
    Write-ZiStep 'Проверка ключевых компонентов меню «Пуск»'
    Test-ZiStartMenuPackages -Quiet
}

# ----------------------------------------------------------------------------
# Режим: откат изменений
# ----------------------------------------------------------------------------
function Invoke-ZiRollback {
    Write-ZiHdr 'ОТКАТ ИЗМЕНЕНИЙ ИЗ РЕЗЕРВНОЙ КОПИИ'
    if (-not $script:ZiBackupDir -or -not (Test-Path -LiteralPath $script:ZiBackupDir)) {
        Add-ZiFinding -Level 'WARN' -Code 'ROLLBACK_NODIR' -Text 'Не указана папка резервной копии'
        return
    }
    Write-ZiInfo ('папка копии: ' + $script:ZiBackupDir)

    # возврат переименованных каталогов
    $actionsFile = Join-Path $script:ZiBackupDir 'actions.txt'
    $renames = 0
    if (Test-Path -LiteralPath $actionsFile) {
        foreach ($line in (Get-Content -LiteralPath $actionsFile -ErrorAction SilentlyContinue)) {
            if ($line -match '^RENAME\t') {
                $parts = $line -split "`t"
                if ($parts.Count -ge 3) {
                    $cur = $parts[1]
                    $orig = $parts[2]
                    if ((Test-Path -LiteralPath $cur) -and -not (Test-Path -LiteralPath $orig)) {
                        try {
                            $parent = Split-Path -Parent $orig
                            if ($parent -and -not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
                            Move-Item -LiteralPath $cur -Destination $orig -Force -ErrorAction Stop
                            $renames++
                            Write-ZiOk ('возвращён каталог: ' + $orig)
                        } catch { Write-ZiWarn ('не удалось вернуть ' + $orig + ' — ' + $_.Exception.Message) }
                    }
                }
            }
        }
    }

    # импорт резервных копий реестра
    $regs = @(Get-ChildItem -LiteralPath $script:ZiBackupDir -Filter '*.reg' -ErrorAction SilentlyContinue)
    $imported = 0
    foreach ($r in $regs) {
        try {
            $out = & reg.exe import $r.FullName 2>&1
            if ($LASTEXITCODE -eq 0) {
                $imported++
                Write-ZiOk ('восстановлены параметры: ' + (Split-Path -Leaf $r.Name))
            } else {
                Write-ZiWarn ('не удалось импортировать ' + $r.Name + ' — ' + ($out -join ' '))
            }
        } catch { Write-ZiWarn ('ошибка импорта ' + $r.Name) }
    }

    Add-ZiFinding -Level 'OK' -Code 'ROLLBACK_DONE' -Text ('Откат выполнен: восстановлено ключей реестра — ' + $imported + ', каталогов — ' + $renames + '. Требуется перезагрузка.')
    Write-ZiInfo 'После отката перезагрузите компьютер.'
}

# ============================================================================
# Диспетчер режимов
# ============================================================================
switch ($Mode.ToLower()) {
    'diag'         { Invoke-ZiDiag }
    'backup'       { Invoke-ZiBackup }
    'restorepoint' { Invoke-ZiRestorePoint }
    'fix-reg'      { Invoke-ZiFixReg }
    'fix-services' { Invoke-ZiFixServices }
    'fix-start'    { Invoke-ZiFixStart }
    'reset-layout' { Invoke-ZiResetLayout }
    'fix-search'   { Invoke-ZiFixSearch }
    'fix-explorer' { Invoke-ZiFixExplorer }
    'sfc'          { Invoke-ZiSfc }
    'dism'         { Invoke-ZiDism }
    'apps'         { Invoke-ZiApps }
    'rollback'     { Invoke-ZiRollback }
    'check'        {
        Write-ZiHdr 'БЫСТРАЯ ПРОВЕРКА'
        Test-ZiShellProcesses
        Test-ZiStartMenuPackages
        Test-ZiStartMenuBlockers
    }
    default {
        Write-ZiErr ('Неизвестный режим: ' + $Mode)
        exit 2
    }
}

Write-Zi ''
Write-Zi ('--- режим ' + $Mode + ' завершён: критично ' + $script:ZiCritCount + ', предупреждений ' + $script:ZiWarnCount + ', исправлено ' + $script:ZiFixCount + ' ---')
exit 0
