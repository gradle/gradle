import build.kotlinVersion

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.9.7"
}

apply<plugins.GskModule>()

base {
    archivesBaseName = "gradle-kotlin-dsl-plugins"
}

dependencies {
    compileOnly(gradleKotlinDsl())

    compile(kotlin("stdlib"))
    compile(kotlin("gradle-plugin"))

    testImplementation(project(":test-fixtures"))
}

val customInstallation by rootProject.tasks
val test by tasks
test.dependsOn(customInstallation)


// --- Plugins declaration ----------------------------------------------

data class GradlePlugin(val displayName: String, val id: String, val implementationClass: String)

val plugins = listOf(
    GradlePlugin(
        "Embedded Kotlin Gradle Plugin",
        "org.gradle.kotlin.embedded-kotlin",
        "org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin"),
    GradlePlugin(
        "Gradle Kotlin DSL Plugin",
        "org.gradle.kotlin.kotlin-dsl",
        "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin"))

plugins.forEach { plugin ->

    gradlePlugin {
        (plugins) {
            plugin.id {
                id = plugin.id
                implementationClass = plugin.implementationClass
            }
        }
    }

    pluginBundle {
        tags = listOf("Kotlin", "DSL")
        website = "https://github.com/gradle/gradle-script-kotlin"
        vcsUrl = "https://github.com/gradle/gradle-script-kotlin"
        mavenCoordinates.artifactId = base.archivesBaseName
        (plugins) {
            plugin.id {
                id = plugin.id
                displayName = plugin.displayName
                description = plugin.displayName
            }
        }
    }
}


// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
