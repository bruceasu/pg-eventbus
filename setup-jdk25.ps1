# Set JDK 25 as JAVA_HOME for this Maven build
# PowerShell version
# powershell -ExecutionPolicy Bypass -File setup-jdk25.ps1

$env:JAVA_HOME = "C:\green\jdk-25.0.1+8"
# $env:MVN_OPTS=-Xmx2g -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "Using JDK: $env:JAVA_HOME" -ForegroundColor Green
Write-Host "Java Version:" -ForegroundColor Green
& "$env:JAVA_HOME\bin\java.exe" -version

Write-Host "`nMaven Version:" -ForegroundColor Green
& .\mvnw.cmd --version

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Environment Ready!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "You can now run Maven commands, for example:" -ForegroundColor Yellow
Write-Host "  .\mvnw.cmd clean compile" -ForegroundColor White
Write-Host "  .\mvnw.cmd clean package -DskipTests" -ForegroundColor White
Write-Host "`nTo build and run:" -ForegroundColor Yellow
Write-Host "  .\mvnw.cmd -q package -DskipTests" -ForegroundColor White