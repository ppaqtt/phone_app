// Global init script: force all Gradle builds to use Aliyun mirrors
// This file should be placed in ~/.gradle/init.d/ or referenced with --init-script

allprojects {
    repositories {
        maven { url 'http://maven.aliyun.com/repository/google'; allowInsecureProtocol = true }
        maven { url 'http://maven.aliyun.com/repository/public'; allowInsecureProtocol = true }
        maven { url 'http://maven.aliyun.com/repository/gradle-plugin'; allowInsecureProtocol = true }
        maven { url 'http://maven.aliyun.com/repository/central'; allowInsecureProtocol = true }
    }
    buildscript {
        repositories {
            maven { url 'http://maven.aliyun.com/repository/google'; allowInsecureProtocol = true }
            maven { url 'http://maven.aliyun.com/repository/public'; allowInsecureProtocol = true }
            maven { url 'http://maven.aliyun.com/repository/gradle-plugin'; allowInsecureProtocol = true }
        }
    }
}
