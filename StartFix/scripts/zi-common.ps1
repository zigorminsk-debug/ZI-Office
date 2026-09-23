# =============================================================================
#  ZI Office StartFix — общие функции (Windows 10/11, Server 2012-2025)
#  Совместимо с Windows PowerShell 5.1 (входит в состав Windows).
# =============================================================================

$script:ZiLogFile      = ''
$script:ZiFindingsFile = ''
$script:ZiBackupDir    = ''
$script:ZiStamp        = (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')
$script:ZiCritCount    = 0
$script:ZiWarnCount    = 0
$script:ZiFixCount     = 0
$script:ZiErrorCount   = 0

# --- контекст ---------------------------------------------------------------
function Initialize-ZiContext {
    param(
        [string]$Log = '',
        [string]$Findings = '',
        [string]$BackupDir = ''
    )
    if ($Log)        { $script:ZiLogFile = $Log }
    if ($Findings)   { $script:ZiFindingsFile = $Findings }
    if ($BackupDir)  { $script:ZiBackupDir = $BackupDir }

    if ($script:ZiLogFile) {
        $d = Split-Path -Parent $script:ZiLogFile
        if ($d -and -not (Test-Path -LiteralPath $d)) {
            New-Item -ItemType Directory -Force -Path $d | Out-Null
        }
    }
    if ($script:ZiBackupDir -and -not (Test-Path -LiteralPath $script:ZiBackupDir)) {
        New-Item -ItemType Directory -Force -Path $script:ZiBackupDir | Out-Null
    }
    try { $Host.UI.RawUI.WindowTitle = 'ZI Office StartFix' } catch { }
}

# --- вывод ------------------------------------------------------------------
function Write-Zi {
    param([string]$Text = '', [string]$Color = '', [switch]$OnlyLog)
    if (-not $OnlyLog) {
        if ($Color) { Write-Host $Text -ForegroundColor $Color } else { Write-Host $Text }
    }
    if ($script:ZiLogFile) {
        try {
            Add-Content -LiteralPath $script:ZiLogFile -Value $Text -Encoding UTF8 -ErrorAction Stop
        } catch { }
    }
}

function Write-ZiHdr {
    param([string]$Text)
    Write-Zi ''
    Write-Zi ('=' * 70) 'Cyan'
    Write-Zi ("  " + $Text) 'Cyan'
    Write-Zi ('=' * 70) 'Cyan'
}

function Write-ZiStep {
    param([string]$Text)
    Write-Zi -Text ("  » " + $Text) -Color 'White'
}
function Write-ZiInfo {
    param([string]$Text)
    Write-Zi -Text ("     " + $Text) -Color 'Gray'
}
function Write-ZiOk {
    param([string]$Text)
    Write-Zi -Text ("  [+] " + $Text) -Color 'Green'
}
function Write-ZiWarn {
    param([string]$Text)
    Write-Zi -Text ("  [~] " + $Text) -Color 'Yellow'
}
function Write-ZiErr {
    param([string]$Text)
    Write-Zi -Text ("  [!] " + $Text) -Color 'Red'
}

# --- заключения -------------------------------------------------------------
function Add-ZiFinding {
    param(
        [ValidateSet('CRIT', 'WARN', 'INFO', 'OK')]
        [string]$Level = 'INFO',
        [string]$Code = '',
        [string]$Text = ''
    )
    if ($script:ZiFindingsFile) {
        try {
            Add-Content -LiteralPath $script:ZiFindingsFile -Value ("$Level`t$Code`t$Text") -Encoding UTF8 -ErrorAction Stop
        } catch { }
    }
    switch ($Level) {
        'CRIT'  { Write-ZiErr  $Text; $script:ZiCritCount++ }
        'WARN'  { Write-ZiWarn $Text; $script:ZiWarnCount++ }
        'OK'    { Write-ZiOk   $Text; $script:ZiFixCount++ }
        default { Write-ZiInfo $Text }
    }
}

# --- проверки ---------------------------------------------------------------
function Test-ZiAdmin {
    try {
        $id = [Security.Principal.WindowsIdentity]::GetCurrent()
        $p  = New-Object Security.Principal.WindowsPrincipal($id)
        return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch { return $false }
}

function Get-ZiOSInfo {
    $key = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'
    $info = @{
        ProductName = 'Windows'
        Version     = ''
        Build       = 0
        UBR         = 0
        EditionID   = ''
        IsServer    = $false
        IsWin11     = $false
        IsWin10     = $false
        Format      = 'Unknown'
    }
    try {
        $p = Get-ItemProperty -Path $key -ErrorAction Stop
        if ($p.ProductName)      { $info.ProductName = $p.ProductName }
        if ($p.DisplayVersion)   { $info.Version = $p.DisplayVersion }
        elseif ($p.ReleaseId)    { $info.Version = $p.ReleaseId }
        if ($p.CurrentBuild)     { $info.Build = [int]$p.CurrentBuild }
        if ($p.UBR)              { $info.UBR = [int]$p.UBR }
        if ($p.EditionID)        { $info.EditionID = $p.EditionID }
        if ($p.InstallationType -and $p.InstallationType -ne 'Client') { $info.IsServer = $true }
        if ($info.ProductName -like '*Server*') { $info.IsServer = $true }
        if ($info.Build -ge 22000) { $info.IsWin11 = $true }
        if ($info.Build -ge 10240) { $info.IsWin10 = $true }
    } catch { }
    if ($info.IsWin11) { $info.Format = 'Windows11' }
    elseif ($info.IsWin10) { $info.Format = 'Windows10' }
    elseif ($info.IsServer) { $info.Format = 'ServerLegacy' }
    return $info
}

# --- реестр -----------------------------------------------------------------
function Backup-ZiRegKey {
    param([string]$Path, [string]$Name)
    if (-not $script:ZiBackupDir) { return $false }
    if (-not (Test-Path -LiteralPath ("Registry::" + $Path) -ErrorAction SilentlyContinue)) { return $false }
    $file = Join-Path $script:ZiBackupDir ($Name + '.reg')
    try {
        $out = & reg.exe export $Path $file /y 2>&1
        if (Test-Path -LiteralPath $file) {
            Write-ZiInfo ("резервная копия: " + (Split-Path -Leaf $file))
            return $true
        }
    } catch { }
    return $false
}

function Remove-ZiRegValue {
    param([string]$Path, [string]$Name, [switch]$Backup, [switch]$NoLog)
    $full = "Registry::" + $Path
    try {
        if (-not (Test-Path -LiteralPath $full)) { return $false }
        $props = Get-ItemProperty -LiteralPath $full -ErrorAction Stop
        if (-not ($props.PSObject.Properties.Name -contains $Name)) { return $false }
        if ($Backup) { Backup-ZiRegKey -Path $Path -Name ('key_' + ($Path -replace '[\\:\s]', '_')) }
        Remove-ItemProperty -LiteralPath $full -Name $Name -Force -ErrorAction Stop
        if (-not $NoLog) { Write-ZiOk ("Удалён мешающий параметр реестра: " + $Path + " :: " + $Name) }
        return $true
    } catch {
        Write-ZiWarn ("Не удалось удалить параметр " + $Path + " :: " + $Name + " — " + $_.Exception.Message)
        return $false
    }
}

function Set-ZiRegValue {
    param([string]$Path, [string]$Name, $Value, [string]$Type = 'DWord', [switch]$Backup)
    try {
        if (-not (Test-Path -LiteralPath ("Registry::" + $Path))) {
            New-Item -Path $Path -Force | Out-Null
        }
        if ($Backup) { Backup-ZiRegKey -Path $Path -Name ('key_' + ($Path -replace '[\\:\s]', '_')) }
        Set-ItemProperty -Path $Path -Name $Name -Value $Value -Type $Type -Force -ErrorAction Stop
        Write-ZiOk ("Установлен параметр реестра: " + $Path + " :: " + $Name + " = " + $Value)
        return $true
    } catch {
        Write-ZiWarn ("Не удалось установить параметр " + $Path + " :: " + $Name + " — " + $_.Exception.Message)
        return $false
    }
}

function Get-ZiRegValue {
    param([string]$Path, [string]$Name)
    try {
        $props = Get-ItemProperty -LiteralPath ("Registry::" + $Path) -ErrorAction Stop
        if ($props.PSObject.Properties.Name -contains $Name) { return $props.$Name }
    } catch { }
    return $null
}

# --- службы -----------------------------------------------------------------
$script:ZiServiceMap = @(
    @{ Name = 'AppXSvc';               Start = 'demand'; Role = 'установка и обслуживание приложений (меню «Пуск»)' },
    @{ Name = 'ClipSVC';               Start = 'demand'; Role = 'служба лицензий приложений' },
    @{ Name = 'StateRepository';       Start = 'demand'; Role = 'хранилище состояния приложений' },
    @{ Name = 'TileDataModelSvc';      Start = 'demand'; Role = 'модель плиток меню «Пуск» (Windows 11)' },
    @{ Name = 'AppReadiness';          Start = 'demand'; Role = 'подготовка приложений к работе' },
    @{ Name = 'Appinfo';               Start = 'demand'; Role = 'сведения о приложениях' },
    @{ Name = 'CoreMessagingRegistrar';Start = 'auto';   Role = 'диспетчер обмена сообщениями (XAML-оболочка)' },
    @{ Name = 'SystemEventsBroker';    Start = 'auto';   Role = 'посредник системных событий' },
    @{ Name = 'BrokerInfrastructure';  Start = 'auto';   Role = 'инфраструктура фоновых задач' },
    @{ Name = 'DcomLaunch';            Start = 'auto';   Role = 'запуск сервера DCOM' },
    @{ Name = 'RpcSs';                 Start = 'auto';   Role = 'удалённый вызов процедур' },
    @{ Name = 'RpcEptMapper';          Start = 'auto';   Role = 'сопоставитель конечных точек RPC' },
    @{ Name = 'Themes';                Start = 'auto';   Role = 'темы оформления (панель задач, меню «Пуск»)' },
    @{ Name = 'ProfSvc';               Start = 'auto';   Role = 'служба профилей пользователей' },
    @{ Name = 'UserManager';           Start = 'auto';   Role = 'диспетчер пользователей' },
    @{ Name = 'TabletInputService';    Start = 'demand'; Role = 'служба планшетного ввода и сенсорной клавиатуры' },
    @{ Name = 'ShellHWDetection';      Start = 'auto';   Role = 'обнаружение оборудования оболочкой' },
    @{ Name = 'WSearch';               Start = 'auto';   Role = 'Windows Search (поиск в меню «Пуск»)' },
    @{ Name = 'Winmgmt';               Start = 'auto';   Role = 'инструментарий управления Windows (WMI)' },
    @{ Name = 'TrustedInstaller';      Start = 'demand'; Role = 'установщик модулей Windows (обслуживание компонентов)' },
    @{ Name = 'UsoSvc';                Start = 'auto';   Role = 'служба обновления Orchestrator' },
    @{ Name = 'wuauserv';              Start = 'demand'; Role = 'центр обновления Windows' }
)

function Get-ZiServiceState {
    param([string]$Name)
    try {
        $svc = Get-Service -Name $Name -ErrorAction Stop
        $key = 'HKLM:\SYSTEM\CurrentControlSet\Services\' + $Name
        $start = $null
        try { $start = (Get-ItemProperty -Path $key -Name Start -ErrorAction Stop).Start } catch { }
        return @{ Exists = $true; Status = [string]$svc.Status; Start = $start; Service = $svc }
    } catch {
        return @{ Exists = $false; Status = 'Missing'; Start = $null }
    }
}

function Set-ZiServiceStartType {
    param([string]$Name, [string]$StartType)
    try {
        $out = & sc.exe config $Name start= $StartType 2>&1
        if ($LASTEXITCODE -eq 0) { return $true }
    } catch { }
    return $false
}

function Start-ZiServiceSafe {
    param([string]$Name)
    try {
        $svc = Get-Service -Name $Name -ErrorAction Stop
        if ($svc.Status -ne 'Running') {
            $out = & sc.exe start $Name 2>&1
            Start-Sleep -Milliseconds 400
            $svc = Get-Service -Name $Name -ErrorAction SilentlyContinue
            if ($svc -and $svc.Status -eq 'Running') { return $true }
            return $false
        }
        return $true
    } catch { return $false }
}

# --- AppX -------------------------------------------------------------------
function Get-ZiAppxPackage {
    param([string]$Name, [switch]$AllUsers)
    try {
        if ($AllUsers) {
            return Get-AppxPackage -Name $Name -AllUsers -ErrorAction SilentlyContinue
        }
        return Get-AppxPackage -Name $Name -ErrorAction SilentlyContinue
    } catch { return $null }
}

function Test-ZiAppxCommand {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Register-ZiPackagePath {
    param([string]$ManifestPath, [switch]$AllUsers)
    if (-not $ManifestPath) { return $false }
    if (-not (Test-Path -LiteralPath $ManifestPath)) {
        Write-ZiWarn ("не найден манифест: " + $ManifestPath)
        return $false
    }
    try {
        if ($AllUsers) {
            Add-AppxPackage -DisableDevelopmentMode -Register $ManifestPath -ErrorAction Stop
        } else {
            Add-AppxPackage -DisableDevelopmentMode -Register $ManifestPath -ErrorAction Stop
        }
        return $true
    } catch {
        $msg = $_.Exception.Message
        if ($msg -match '0x80073D02|0x80073CF9') {
            Write-ZiInfo ("пакет занят системой, повтор через паузу: " + $Name)
            Start-Sleep -Seconds 2
            try {
                Add-AppxPackage -DisableDevelopmentMode -Register $ManifestPath -ErrorAction Stop
                return $true
            } catch { }
        }
        Write-ZiWarn ("не удалось зарегистрировать: " + $ManifestPath + " — " + $msg)
        return $false
    }
}

# Перерегистрация пакета: сначала пользовательская, затем -AllUsers
function Repair-ZiAppxPackage {
    param([string]$Name, [switch]$AllUsers)
    $ok = $false
    $pkgs = @(Get-ZiAppxPackage -Name $Name)
    if (-not $pkgs -or $pkgs.Count -eq 0) {
        $pkgs = @(Get-ZiAppxPackage -Name $Name -AllUsers)
    }
    if (-not $pkgs -or $pkgs.Count -eq 0) {
        Write-ZiWarn ("пакет не найден ни у одного пользователя: " + $Name)
        return $false
    }
    foreach ($p in $pkgs) {
        $loc = $p.InstallLocation
        if (-not $loc) { continue }
        $manifest = Join-Path $loc 'AppxManifest.xml'
        if (Register-ZiPackagePath -ManifestPath $manifest -AllUsers:$AllUsers) {
            $ok = $true
            Write-ZiOk ("перерегистрирован пакет " + $Name + " (версия " + $p.Version + ")")
        }
    }
    return $ok
}

function Reset-ZiAppxPackage {
    param([string]$Name)
    if (-not (Test-ZiAppxCommand -Name 'Reset-AppxPackage')) { return $false }
    try {
        $pkgs = @(Get-ZiAppxPackage -Name $Name)
        if (-not $pkgs -or $pkgs.Count -eq 0) { return $false }
        foreach ($p in $pkgs) {
            Reset-AppxPackage -Package $p.PackageFullName -ErrorAction Stop
            Write-ZiOk ("сброшено состояние приложения " + $Name)
        }
        return $true
    } catch {
        Write-ZiInfo ("сброс " + $Name + " не выполнен: " + $_.Exception.Message)
        return $false
    }
}

# --- файлы и папки ----------------------------------------------------------
function Move-ZiFolderAside {
    param([string]$Path, [string]$Suffix = 'bak')
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    $target = $Path + '.' + $Suffix + '-' + $script:ZiStamp
    try {
        Move-Item -LiteralPath $Path -Destination $target -Force -ErrorAction Stop
        if ($script:ZiBackupDir) {
            $manifest = Join-Path $script:ZiBackupDir 'actions.txt'
            Add-Content -LiteralPath $manifest -Value ("RENAME`t" + $target + "`t" + $Path) -Encoding UTF8 -ErrorAction SilentlyContinue
        }
        Write-ZiOk ("состояние сохранено, создан новый каталог: " + (Split-Path -Leaf $Path))
        return $true
    } catch {
        Write-ZiWarn ("не удалось переместить " + $Path + " — " + $_.Exception.Message)
        return $false
    }
}

function Get-ZiAppxLocalStatePath {
    param([string]$PackageFamily)
    $base = Join-Path $env:LOCALAPPDATA ('Packages\' + $PackageFamily + '\LocalState')
    return $base
}

# --- процессы ---------------------------------------------------------------
function Get-ZiProcessInfo {
    param([string]$Name)
    try {
        $p = @(Get-Process -Name $Name -ErrorAction SilentlyContinue)
        return $p.Count
    } catch { return 0 }
}

# --- события ----------------------------------------------------------------
function Get-ZiCrashEvents {
    param([int]$Days = 14)
    $result = @()
    try {
        $filter = @{
            LogName      = 'Application'
            ProviderName = 'Application Error'
            StartTime    = (Get-Date).AddDays(-$Days)
        }
        $events = Get-WinEvent -FilterHashtable $filter -MaxEvents 300 -ErrorAction Stop
        foreach ($e in $events) {
            $m = [string]$e.Message
            if ($m -match 'StartMenuExperienceHost|ShellExperienceHost|SearchHost|SearchApp|sihost|explorer\.exe|Cortana') {
                $result += [pscustomobject]@{ Time = $e.TimeCreated; Id = $e.Id; Text = ($m -split "`n")[0] }
            }
        }
    } catch { }
    return $result
}

function Get-ZiThirdPartyStartTools {
    $found = @()
    $paths = @(
        @{ P = "$env:ProgramFiles\StartAllBack";          N = 'StartAllBack' },
        @{ P = "${env:ProgramFiles(x86)}\StartAllBack";   N = 'StartAllBack' },
        @{ P = "$env:ProgramFiles\Stardock\Start11";      N = 'Start11' },
        @{ P = "$env:ProgramFiles\Open-Shell";            N = 'Open-Shell' },
        @{ P = "${env:ProgramFiles(x86)}\Open-Shell";     N = 'Open-Shell' },
        @{ P = "$env:ProgramFiles\Classic Shell";         N = 'Classic Shell' },
        @{ P = "$env:ProgramFiles\ExplorerPatcher";       N = 'ExplorerPatcher' },
        @{ P = "$env:APPDATA\ExplorerPatcher";            N = 'ExplorerPatcher' },
        @{ P = "$env:ProgramFiles\StartIsBack";           N = 'StartIsBack' },
        @{ P = "$env:ProgramFiles\WindowsStartMenu";      N = 'StartMenu (сторонняя программа)' }
    )
    foreach ($item in $paths) {
        if ($item.P -and (Test-Path -LiteralPath $item.P)) {
            if ($found -notcontains $item.N) { $found += $item.N }
        }
    }
    if (Test-Path -LiteralPath "$env:WINDIR\dxgi.dll") {
        if ($found -notcontains 'ExplorerPatcher (dxgi.dll в C:\Windows)') {
            $found += 'ExplorerPatcher (dxgi.dll в C:\Windows)'
        }
    }
    return $found
}

function Get-ZiPendingReboot {
    $reasons = @()
    if (Test-Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Component Based Servicing\RebootPending') { $reasons += 'обслуживание компонентов' }
    if (Test-Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\WindowsUpdate\Auto Update\RebootRequired') { $reasons += 'центр обновления Windows' }
    try {
        $pfro = (Get-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager' -Name PendingFileRenameOperations -ErrorAction Stop).PendingFileRenameOperations
        if ($pfro) { $reasons += 'отложенная переименование файлов' }
    } catch { }
    return $reasons
}

function Get-ZiClockSkew {
    # если есть файлы «из будущего» — часы системы отстают, а это ломает активацию AppX
    try {
        $now = Get-Date
        $future = Get-ChildItem -LiteralPath "$env:WINDIR\System32" -Filter '*.dll' -ErrorAction SilentlyContinue |
                  Where-Object { $_.LastWriteTime -gt $now.AddDays(1) } |
                  Select-Object -First 1
        if ($future) { return $future.LastWriteTime }
    } catch { }
    return $null
}

function Get-ZiFreeSpaceGB {
    try {
        $d = Get-PSDrive -Name ($env:SystemDrive -replace ':','') -ErrorAction Stop
        return [math]::Round(($d.Free / 1073741824), 2)
    } catch { return -1 }
}
