@echo off
setlocal
cd /d %~dp0
call gradlew.bat assembleDebug --no-daemon --stacktrace
endlocal
