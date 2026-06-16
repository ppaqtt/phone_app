allprojects {
    buildscript {
        repositories {
            gradlePluginPortal()
            google()
            mavenCentral()
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

settingsEvaluated { settings ->
    settings.pluginManagement {
        repositories {
            gradlePluginPortal()
            google()
            mavenCentral()
        }
    }
}
