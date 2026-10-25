@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Bent Steering - Samsung TV installer
cd /d "%~dp0"
set "SDB=%~dp0tools\sdb.exe"
set "APPID=BSOffroad1.BentSteering"
set "APPNAME=BentSteering"
set "LOG=%TEMP%\bso-install.txt"

echo.
echo    BENT STEERING OFFROAD  -  install on the Samsung TV
echo    ===================================================
echo.
if not exist "%SDB%" (
  echo    tools\sdb.exe is missing. Unzip the whole folder first, then run this again.
  goto :end
)
if not exist "BentSteering.wgt" (
  echo    BentSteering.wgt is missing. Unzip the whole folder first, then run this again.
  goto :end
)

echo    The TV needs Developer Mode on, with THIS computer's address typed in.
echo    This computer's address:
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do echo        %%a
echo    (README.txt has the steps on the TV.)
echo.

set "TVIP="
if exist "tv-address.txt" set /p TVIP=<"tv-address.txt"
if defined TVIP (
  set "NEW="
  set /p "NEW=   TV address [!TVIP!] - press Enter to use it, or type a new one: "
  if defined NEW set "TVIP=!NEW!"
) else (
  set /p "TVIP=   Type the TV's address (like 192.168.1.50) and press Enter: "
)
if not defined TVIP goto :end
set "TVIP=!TVIP: =!"
> "tv-address.txt" echo !TVIP!
set "DEV=!TVIP!:26101"

echo.
echo    Connecting to the TV at !TVIP! ...
"%SDB%" kill-server >nul 2>&1
"%SDB%" connect !TVIP!
"%SDB%" devices > "%TEMP%\bso-devices.txt" 2>&1
findstr /l /c:"!TVIP!" "%TEMP%\bso-devices.txt" | findstr /l /c:"device" >nul
if errorlevel 1 (
  echo.
  echo    Couldn't reach the TV at !TVIP!. Check that:
  echo     - the TV is on and on the same Wi-Fi as this computer
  echo     - Developer Mode is on with this computer's address, and the TV was restarted after
  echo     - the TV's address is right: Settings ^> General ^> Network ^> Network Status ^> IP Settings
  goto :end
)

set "TOOLPATH="
for /f "usebackq tokens=1,* delims=:" %%a in (`"%SDB%" -s !DEV! capability 2^>nul`) do (
  if /i "%%a"=="sdk_toolpath" set "TOOLPATH=%%b"
)
if not defined TOOLPATH set "TOOLPATH=/home/owner/share/tmp/sdk_tools"

echo    Sending the app to the TV ...
"%SDB%" -s !DEV! push "BentSteering.wgt" "!TOOLPATH!/BentSteering.wgt"
if errorlevel 1 (
  echo    The TV didn't take the file. Try running this again.
  goto :end
)

echo    Installing (about a minute) ...
call :install
findstr /l /c:"failed[" "%LOG%" >nul
if not errorlevel 1 (
  findstr /i /c:"not match" "%LOG%" >nul
  if not errorlevel 1 (
    echo    Replacing the older copy of Bent Steering ...
    "%SDB%" -s !DEV! shell 0 vd_appuninstall %APPNAME% >nul 2>&1
    call :install
  )
)
findstr /l /c:"failed[" "%LOG%" >nul
if not errorlevel 1 (
  echo.
  echo    The install didn't work. The TV said:
  type "%LOG%"
  findstr /i /c:"certificate" "%LOG%" >nul
  if not errorlevel 1 echo    This TV wants a Samsung certificate made for it. Send this message to Claude.
  goto :end
)

echo    Starting Bent Steering on the TV ...
"%SDB%" -s !DEV! shell 0 was_execute %APPID% >nul 2>&1
echo.
echo    Done. Bent Steering is on the TV.
echo    To have it come up when the TV turns on: on the TV, Settings ^> General ^> Smart Features,
echo    turn Autorun Last App ON and Autorun Smart Hub OFF.
goto :end

:install
"%SDB%" -s !DEV! shell 0 vd_appinstall %APPNAME% "!TOOLPATH!/BentSteering.wgt" > "%LOG%" 2>&1
findstr /v /r /c:"^ *$" "%LOG%"
exit /b

:end
echo.
pause
