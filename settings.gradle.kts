import org.gradle.internal.impldep.org.apache.ivy.core.IvyPatternHelper.substitute

pluginManagement {
    fun RepositoryHandler.setup() {
        mavenCentral()
        // maven("https://jitpack.io")
        if (this == pluginManagement.repositories) {
            gradlePluginPortal()
        }
    }
    repositories.setup()
    dependencyResolutionManagement {
        @Suppress("UnstableApiUsage")
        repositories.setup()
        
        @Suppress("UnstableApiUsage")
        this.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        
        repositories {
            maven("https://jitpack.io")
        }
        
        versionCatalogs { 
            create("libs") {
                val kotlinVersion = version("kotlin", "1.9.20")
                plugin("kotlin.jvm", "org.jetbrains.kotlin.jvm").versionRef(kotlinVersion)
            }
        }
    }
}

includeBuild("ast") {
    dependencySubstitution {
        substitute(module("kotlinx.ast:parser-antlr-kotlin")).using(project(":parser-antlr-kotlin"))
        substitute(module("kotlinx.ast:grammar-kotlin-parser-antlr-kotlin")).using(project(":grammar-kotlin-parser-antlr-kotlin"))
    }
}
