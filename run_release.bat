@echo off
setlocal
cd /d %~dp0
call gradlew.bat clean bundleRelease --no-daemon --stacktrace
echo.
echo If the build succeeded, your AAB is at:
echo   app\build\outputs\bundle\release\app-release.aab
echo.
endlocal

