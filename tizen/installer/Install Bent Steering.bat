@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Bent Steering - Samsung TV installer
cd /d "%~dp0"
set "SDB=%~dp0tools\sdb.exe"
set "APPID=BSOffroad1.BentSteering"
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
  set /p "TVIP=   Type the TV's address, for example 192.168.1.50, and press Enter: "
)
if not defined TVIP goto :end
rem keep only the address itself: no spaces or brackets
set "TVIP=!TVIP: =!"
set "TVIP=!TVIP:(=!"
set "TVIP=!TVIP:)=!"
set "TVIP=!TVIP:[=!"
set "TVIP=!TVIP:]=!"
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

rem Same steps as Samsung's own tools: the package goes in sdk_tools/tmp, and the TV is given the full app id.
set "DEST=/home/owner/share/tmp/sdk_tools/tmp/BentSteering.wgt"
echo    Sending the app to the TV ...
"%SDB%" -s !DEV! push "BentSteering.wgt" "!DEST!"
if errorlevel 1 (
  set "DEST=/home/owner/share/tmp/sdk_tools/BentSteering.wgt"
  "%SDB%" -s !DEV! push "BentSteering.wgt" "!DEST!"
  if errorlevel 1 (
    echo    The TV didn't take the file. Try running this again.
    goto :end
  )
)

echo    Installing (about a minute) ...
call :install
findstr /l /c:"failed[" "%LOG%" >nul
if not errorlevel 1 (
  echo    Removing any older copy of Bent Steering and trying again ...
  "%SDB%" -s !DEV! shell 0 vd_appuninstall %APPID% >nul 2>&1
  call :install
)
findstr /l /c:"failed[" "%LOG%" >nul
if not errorlevel 1 (
  echo.
  echo    The install didn't work. Take a photo of this window and send it to Claude.
  goto :end
)

echo    Starting Bent Steering on the TV ...
"%SDB%" -s !DEV! shell 0 execute %APPID% >nul 2>&1
"%SDB%" -s !DEV! shell 0 was_execute %APPID% >nul 2>&1
echo.
echo    Done. Bent Steering is on the TV.
echo    To have it come up when the TV turns on: on the TV, Settings ^> General ^> Smart Features,
echo    turn Autorun Last App ON and Autorun Smart Hub OFF.
goto :end

:install
"%SDB%" -s !DEV! shell 0 vd_appinstall %APPID% "!DEST!" > "%LOG%" 2>&1
findstr /v /r /c:"^ *$" "%LOG%"
exit /b

:end
echo.
pause
