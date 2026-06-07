// Global init script: force all Gradle builds to use Aliyun mirrors
// This file replaces all repositories with Aliyun mirrors to bypass network restrictions

allprojects {
    buildscript {
        repositories {
            clear()
            maven { url 'https://maven.aliyun.com/repository/gradle-plugin'; allowInsecureProtocol = true }
            maven { url 'https://maven.aliyun.com/repository/public'; allowInsecureProtocol = true }
            maven { url 'https://maven.aliyun.com/repository/google'; allowInsecureProtocol = true }
            maven { url 'https://maven.aliyun.com/repository/central'; allowInsecureProtocol = true }
        }
    }
    repositories {
        clear()
        maven { url 'https://maven.aliyun.com/repository/google'; allowInsecureProtocol = true }
        maven { url 'https://maven.aliyun.com/repository/public'; allowInsecureProtocol = true }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin'; allowInsecureProtocol = true }
        maven { url 'https://maven.aliyun.com/repository/central'; allowInsecureProtocol = true }
    }
}

settingsEvaluated { settings ->
    settings.pluginManagement {
        repositories {
            clear()
            maven { url 'https://maven.aliyun.com/repository/gradle-plugin'; allowInsecureProtocol = true }
            maven { url 'https://maven.aliyun.com/repository/public'; allowInsecureProtocol = true }
            maven { url 'https://maven.aliyun.com/repository/google'; allowInsecureProtocol = true }
        }
    }
}
