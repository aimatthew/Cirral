@echo off
setlocal
set "APP_HOME=%~dp0"
set "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

rem An old JAVA_HOME may point to a removed temporary JDK. Use the JDK bundled
rem with Android Studio when its installation is registered on this machine.
if not exist "%JAVA_EXE%" (
  for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\Android Studio" /v Path 2^>nul') do (
    if /I "%%A"=="REG_SZ" if exist "%%B\jbr\bin\java.exe" set "JAVA_EXE=%%B\jbr\bin\java.exe"
  )
)

"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=gradlew" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
