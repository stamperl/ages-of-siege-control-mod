@echo off
setlocal

set "SOURCE=%~dp0build\libs\ages-of-siege-control-0.1.0.jar"
set "TARGET_ONE=C:\Users\Stamp\AppData\Roaming\PrismLauncher\instances\Ages Of Siege-0.1.0\minecraft\mods"
set "TARGET_TWO=C:\Users\Stamp\OneDrive\Documents\Ages Of Siege Pack\tools\prism\instances\Ages Of Siege-0.1.0\minecraft\mods"
set "COPIED_ANY=0"

if not exist "%SOURCE%" (
	echo Build jar not found at:
	echo %SOURCE%
	exit /b 1
)

call :copy_to_target "%TARGET_ONE%"
call :copy_to_target "%TARGET_TWO%"

if "%COPIED_ANY%"=="0" (
	echo Prism mods folders not found.
	echo Checked:
	echo %TARGET_ONE%
	echo %TARGET_TWO%
	exit /b 1
)

echo Installed Ages Of Siege Control Mod into Prism targets.
exit /b 0

:copy_to_target
set "TARGET=%~1"
if not exist "%TARGET%" (
	exit /b 0
)
copy /Y "%SOURCE%" "%TARGET%\ages-of-siege-control-0.1.0.jar" >nul
powershell -NoProfile -Command "(Get-Item '%TARGET%\ages-of-siege-control-0.1.0.jar').LastWriteTime = Get-Date" >nul
set "COPIED_ANY=1"
echo Installed into: %TARGET%
exit /b 0
