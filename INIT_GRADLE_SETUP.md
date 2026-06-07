# 重要：如何配置全局 init.gradle 解决网络问题

## 问题
在本地开发时，Gradle 会尝试访问 `https://services.gradle.org` 和 `https://github.com` 
但因网络问题（如防火墙、SSL证书问题等）导致同步失败。

## 解决方案
将 `/workspace/init.gradle.kts` 文件复制到 `C:\Users\<用户名>\.gradle\init.d\aliyun.gradle` 目录

## Windows 操作步骤
1. 按 `Win + R` 打开运行窗口
2. 输入 `%USERPROFILE%\.gradle` 回车
3. 如果 `init.d` 文件夹不存在，手动创建
4. 将本目录下的 `init.gradle.kts` 文件复制到 `init.d` 目录
5. 重命名文件为 `aliyun.gradle` (不要带 .kts 后缀)

或者使用 PowerShell 命令：
```powershell
Copy-Item init.gradle.kts $env:USERPROFILE\.gradle\init.d\aliyun.gradle
```

## 重新同步
1. 打开 Android Studio
2. File → Invalidate Caches / Restart
3. 重启后点击 "Sync Project with Gradle Files"
