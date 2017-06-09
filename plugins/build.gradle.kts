import build.kotlinVersion

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.9.7"
}

apply {
    plugin("kotlin")
}

base {
    archivesBaseName = "gradle-script-kotlin-plugins"
}

dependencies {

    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin"))

    testImplementation(project(":test-fixtures"))
}

tasks {
    "generateEmbeddedKotlinVersionResource" {
        val generatedResourcesDir = "$buildDir/generate-resources/main"
        val versionResourceFile = file("$generatedResourcesDir/embedded-kotlin-version.txt")

        inputs.property("embeddedKotlinVersion", kotlinVersion)
        outputs.file(versionResourceFile)

        val main by java.sourceSets
        main.resources.srcDir(generatedResourcesDir)

        val processResources by tasks
        processResources.dependsOn(this)

        doLast {
            versionResourceFile.parentFile.mkdirs()
            versionResourceFile.writeText(kotlinVersion)
        }
    }
    "test" {
        val customInstallation by rootProject.tasks
        dependsOn(customInstallation)
    }
}

// --- Plugin declaration ----------------------------------------------
val pluginId = "embedded-kotlin"

gradlePlugin {
    (plugins) {
        pluginId {
            id = pluginId
            implementationClass = "org.gradle.script.lang.kotlin.plugins.embedded.EmbeddedKotlinPlugin"
        }
    }
}

pluginBundle {
    (plugins) {
        pluginId {
            id = pluginId
            displayName = "Embedded Kotlin Gradle Plugin"
        }
    }
}

// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
