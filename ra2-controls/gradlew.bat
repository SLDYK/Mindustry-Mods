@echo off
rem RA2 Controls build entry. ASCII-only on purpose (cmd.exe codepage safety).
rem Falls back to a cached gradle distribution when gradle-wrapper.jar is absent.
setlocal enabledelayedexpansion
set "DIRNAME=%~dp0"
set "GRADLE_HOME="

if exist "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" (
    java -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
    exit /b %errorlevel%
)

for /d %%G in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-*-bin") do (
    for /d %%H in ("%%G\*") do (
        for /d %%I in ("%%H\gradle-*") do (
            if exist "%%I\bin\gradle.bat" set "GRADLE_HOME=%%I"
        )
    )
)

if not defined GRADLE_HOME (
    echo [ra2] ERROR: no gradle-wrapper.jar and no cached gradle distribution found.
    echo [ra2] Install gradle or put gradle-wrapper.jar into gradle\wrapper\
    exit /b 1
)

echo [ra2] using cached gradle: !GRADLE_HOME!
call "!GRADLE_HOME!\bin\gradle.bat" %*
exit /b %errorlevel%

