import build.kotlinVersion

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.9.7"
}

apply<plugins.GskModule>()

base {
    archivesBaseName = "gradle-script-kotlin-plugins"
}

dependencies {

    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin"))

    testImplementation(project(":test-fixtures"))
}

tasks {
    "generateEmbeddedKotlinMetadata"(WriteProperties::class) {
        val metadataPropertiesFile = file("$buildDir/generated-resources/main/embedded-kotlin-metadata.properties")

        outputFile = file(metadataPropertiesFile)
        properties(mapOf("embeddedKotlinVersion" to kotlinVersion))

        val main by java.sourceSets
        main.resources.srcDir(metadataPropertiesFile.parentFile)

        val processResources by tasks
        processResources.dependsOn(this)
    }
    "test" {
        val customInstallation by rootProject.tasks
        dependsOn(customInstallation)
    }
}


// --- Plugins declaration ----------------------------------------------

data class GradlePlugin(val displayName: String, val id: String, val implementationClass: String)

val plugins = listOf(
    GradlePlugin("Embedded Kotlin Gradle Plugin",
                 "org.gradle.script.lang.kotlin.plugins.embedded-kotlin",
                 "org.gradle.script.lang.kotlin.plugins.embedded.EmbeddedKotlinPlugin"))

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
        (plugins) {
            plugin.id {
                id = plugin.id
                displayName = plugin.displayName
            }
        }
    }
}


// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
