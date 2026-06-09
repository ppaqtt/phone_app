@echo off
chcp 65001 >nul
REM ============================================
REM 清笺 release APK 一键打包脚本
REM 需要: 全局 gradle 命令 (Android Studio 自带或手动安装)
REM ============================================

setlocal

cd /d "%~dp0"

echo.
echo [1/4] 检查 gradle 是否可用...
where gradle >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 找不到 gradle 命令!
    echo.
    echo 解决: 用 Android Studio 打开本项目,
    echo       File - Settings - Build - Gradle - 选 "Use Gradle from: 'gradle-wrapper.properties'"
    echo       Sync 完成后, 在 AS 底部 Terminal 跑: gradlew assembleRelease
    echo.
    pause
    exit /b 1
)

echo.
echo [2/4] 清缓存...
call gradle clean
if %ERRORLEVEL% NEQ 0 (
    echo [错误] gradle clean 失败
    pause
    exit /b 1
)

echo.
echo [3/4] 打 release APK...
call gradle :app:assembleRelease
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 打包失败, 看上方日志
    pause
    exit /b 1
)

echo.
echo [4/4] 验证签名...
if not exist "app\build\outputs\apk\release\app-release.apk" (
    echo [错误] 找不到 app-release.apk
    pause
    exit /b 1
)

set "APK=app\build\outputs\apk\release\app-release.apk"
echo.
echo ============================================
echo 打包成功: %APK%
echo ============================================
echo.

REM 验证 SHA1
keytool -printcert -jarfile "%APK%" | findstr "SHA1:"

REM 重命名便于发送
copy /Y "%APK%" "qingjian-v1.0.5.apk" >nul
echo.
echo 复制到当前目录: qingjian-v1.0.5.apk
echo 把这个文件 (不要用微信原名发) 发给别人即可

echo.
pause
