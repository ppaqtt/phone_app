// Top-level build file: 走 buildscript classpath 注入, 避免与 plugins{} 块冲突
buildscript {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
        // P112-FIX: 项目未使用 Hilt, 移除 hilt-android-gradle-plugin classpath
        // 否则 KSP 会启动 Hilt 处理器, 找不到 @HiltAndroidApp 而失败, 阻断整个 KSP 链
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.8.22-1.0.11")
    }
}
