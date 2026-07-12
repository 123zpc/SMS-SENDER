pluginManagement {
    repositories {
        val isCI = System.getenv("CI") != null
        if (isCI) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val isCI = System.getenv("CI") != null
        if (isCI) {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "SmsAgent"
include(":app")
