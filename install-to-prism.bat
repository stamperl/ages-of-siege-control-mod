@echo off
setlocal

set "SOURCE=%~dp0build\libs\ages-of-siege-control-0.1.0.jar"
set "TARGET=C:\Users\Stamp\AppData\Roaming\PrismLauncher\instances\Ages Of Siege-0.1.0\minecraft\mods"

if not exist "%SOURCE%" (
	echo Build jar not found at:
	echo %SOURCE%
	exit /b 1
)

if not exist "%TARGET%" (
	echo Prism mods folder not found at:
	echo %TARGET%
	exit /b 1
)

copy /Y "%SOURCE%" "%TARGET%\ages-of-siege-control-0.1.0.jar" >nul
echo Installed Ages Of Siege Control Mod into Prism.
