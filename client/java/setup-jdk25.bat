@echo off
REM Set JDK 25 as JAVA_HOME for this build session
set JAVA_HOME=C:\green\jdk-25.0.1+8
SET MAVEN_HOME=C:\green\apache-maven-3.9.9
set MVN_OPTS=-Xmx2g -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo Using JDK: %JAVA_HOME%
echo.
echo Java Version:
"%JAVA_HOME%\bin\java.exe" -version