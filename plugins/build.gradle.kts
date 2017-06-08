import build.kotlinVersion
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension


plugins {
    `java-gradle-plugin`
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

configure<GradlePluginDevelopmentExtension> {
    `plugins` {
        val plugin: PluginDeclaration by creating
        plugin.id = pluginId
        plugin.implementationClass = "org.gradle.script.lang.kotlin.plugins.embedded.EmbeddedKotlinPlugin"
    }
}

// --- Utility functions -----------------------------------------------
fun kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
