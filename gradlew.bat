@rem
@rem Gradle startup script for Windows (minimal wrapper) for Chemopunk RPG.
@rem Requires JAVA_HOME or java.exe on PATH and gradle\wrapper\gradle-wrapper.jar.
@rem If the jar is missing, run: gradle wrapper --gradle-version 9.6.0
@rem

@echo off
setlocal

set "DIRNAME=%~dp0"
if "%DIRNAME%"=="" set "DIRNAME=."
set "WRAPPER_JAR=%DIRNAME%gradle\wrapper\gradle-wrapper.jar"

if defined JAVA_HOME goto findJavaFromJavaHome

set "JAVA_EXE=java.exe"
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
goto fail

:findJavaFromJavaHome
set "JAVA_HOME=%JAVA_HOME:"=%"
set "JAVA_EXE=%JAVA_HOME%/bin/java.exe"
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
goto fail

:execute
if not exist "%WRAPPER_JAR%" (
    echo ERROR: gradle-wrapper.jar not found.
    echo Regenerate the wrapper once with a local Gradle installation:
    echo     gradle wrapper --gradle-version 9.6.0
    goto fail
)
set "CLASSPATH=%WRAPPER_JAR%"
"%JAVA_EXE%" %GRADLE_OPTS% %JAVA_OPTS% -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
exit /b %ERRORLEVEL%

:fail
exit /b 1
