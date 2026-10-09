@echo off
rem ---------------------------------------------------------------------
rem TestClient.cmd - a dev client for live two-player testing.
rem
rem   TestClient.cmd          -> TestBuddy  (the player who swears)
rem   TestClient.cmd main     -> Sablednah  (you, as staff)
rem
rem Pair with the dev server, started from WSL:
rem   ./gradlew runServer -PwithStandards
rem
rem Run from WINDOWS, not WSL: a client launched through WSLg often never
rem shows a window. Same arrangement as Standards' TestClient.cmd, which this
rem follows - see there for the history behind each step.
rem ---------------------------------------------------------------------
setlocal
cd /d "%~dp0"

set "TASK=runClientBuddy"
set "WHO=TestBuddy"
set "RUNDIR=runBuddy"
if /i "%~1"=="main" (
    set "TASK=runClientMain"
    set "WHO=Sablednah"
    set "RUNDIR=runMain"
)

rem A real JDK, not a JRE: CurseForge's java-runtime-delta (21) is one. The
rem 26.x branches' Java 25 toolchain is auto-provisioned by Gradle.
set "JAVA_HOME=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-delta\windows-x64\java-runtime-delta"
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo Could not find a JDK at %JAVA_HOME%
    echo Point JAVA_HOME in this file at any JDK 21 or newer.
    pause
    exit /b 1
)

rem Mute every sound category: two clients and a server on one machine play
rem every sound twice, slightly out of step.
powershell -NoProfile -Command ^
  "$d = '%RUNDIR%'; $f = Join-Path $d 'options.txt';" ^
  "New-Item -ItemType Directory -Force -Path $d | Out-Null;" ^
  "$cats = 'master','music','record','weather','block','hostile','neutral','player','ambient','voice','ui';" ^
  "$lines = if (Test-Path $f) { Get-Content $f } else { @() };" ^
  "$lines = $lines | Where-Object { $_ -notmatch '^soundCategory_' };" ^
  "$lines += $cats | ForEach-Object { 'soundCategory_' + $_ + ':0.0' };" ^
  "Set-Content -Path $f -Value $lines"

rem Standards is loaded on both sides so the mod lists match - NeoForge
rem refuses a mismatch with only "bad network protocol". It comes from the
rem published release in libs\standards (scripts/fetch-standards.sh, run from
rem WSL). Override with: set STANDARDS_LIBS=..\SableCraft-Standards\build\libs
if "%STANDARDS_LIBS%"=="" set "STANDARDS_LIBS=libs/standards"
if not exist "%STANDARDS_LIBS%" (
    echo No Standards jars in %STANDARDS_LIBS% - run scripts/fetch-standards.sh from WSL first.
    pause
    exit /b 1
)

echo Starting %WHO% (first run compiles - be patient)...
call gradlew.bat %TASK% --project-cache-dir .gradle-win-%WHO% -PwinClient=%WHO% -PwithStandards -Pstandards_libs=%STANDARDS_LIBS%
pause
