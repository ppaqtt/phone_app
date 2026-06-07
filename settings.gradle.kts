pluginManagement {
    repositories {
        maven {
            url = uri("http://maven.aliyun.com/repository/gradle-plugin")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/public")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/google")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/")
            isAllowInsecureProtocol = true
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven {
            url = uri("http://maven.aliyun.com/repository/google")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/public")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/central")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/gradle-plugin")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/")
            isAllowInsecureProtocol = true
        }
    }
}

rootProject.name = "NotesApp"
include(":app")
