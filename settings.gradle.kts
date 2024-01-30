rootProject.name = "kotlin-static-object-notation"

pluginManagement {
    fun RepositoryHandler.setup() {
        mavenCentral()
        if (this == pluginManagement.repositories) {
            gradlePluginPortal()
        } else {
            maven("https://jitpack.io")
        }
    }
    repositories.setup()
    dependencyResolutionManagement {
        @Suppress("UnstableApiUsage")
        repositories.setup()

        @Suppress("UnstableApiUsage")
        this.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

        versionCatalogs {
            create("libs") {
                val kotlinVersion = version("kotlin", "1.9.22")
                plugin("kotlin.jvm", "org.jetbrains.kotlin.jvm").versionRef(kotlinVersion)
                plugin("kotlin.plugin.serialization", "org.jetbrains.kotlin.plugin.serialization").versionRef(kotlinVersion)
            }
        }
    }
}