@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script (self-downloading "only-script" wrapper)
@REM Requires only a JDK on PATH. On first run it downloads Maven to
@REM %USERPROFILE%\.m2\wrapper and caches it.
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set MAVEN_VERSION=3.9.9
set WRAPPER_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%
set MVN_CMD=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd

if exist "%MVN_CMD%" goto run

echo Downloading Apache Maven %MAVEN_VERSION% (first run only)...
if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
set ZIP=%WRAPPER_DIR%\maven.zip
powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%ZIP%'"
powershell -NoProfile -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%WRAPPER_DIR%' -Force"
del "%ZIP%"

:run
call "%MVN_CMD%" %*
endlocal
