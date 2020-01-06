import accessors.base
import accessors.gradlePlugin
import accessors.pluginBundle
import accessors.publishing

import plugins.futurePluginVersionsFile


plugins {
    id("kotlin-dsl-module")
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
}


// Remove gradleApi() and gradleTestKit() as we want to compile/run against Gradle modules
// TODO consider splitting `java-gradle-plugin` to provide only what's necessary here
afterEvaluate {
    configurations.all {
        dependencies.remove(project.dependencies.gradleApi())
        dependencies.remove(project.dependencies.gradleTestKit())
    }
}


pluginBundle {
    tags = listOf("Kotlin", "DSL")
    website = "https://github.com/gradle/kotlin-dsl"
    vcsUrl = "https://github.com/gradle/kotlin-dsl"
}


afterEvaluate {
    publishing {
        publications {
            named<MavenPublication>("pluginMaven") {
                groupId = project.group.toString()
                artifactId = base.archivesBaseName
            }
        }
    }
}


tasks {
    validatePlugins {
        failOnWarning.set(true)
        enableStricterValidation.set(true)
    }
}


// publish plugin to local repository for integration testing -----------------
// See AbstractPluginTest

val publishPluginsToTestRepository by tasks.registering {
    dependsOn("publishPluginMavenPublicationToTestRepository")
}

val processIntegTestResources by tasks.existing(ProcessResources::class)
val writeFuturePluginVersions by tasks.registering(WriteProperties::class) {
    outputFile = processIntegTestResources.get().futurePluginVersionsFile
}
processIntegTestResources {
    dependsOn(writeFuturePluginVersions)
}

afterEvaluate {

    publishing {
        repositories {
            maven {
                name = "test"
                url = uri("$buildDir/repository")
            }
        }
    }

    gradlePlugin {
        plugins.all {

            val plugin = this

            publishPluginsToTestRepository {
                dependsOn("publish${plugin.name.capitalize()}PluginMarkerMavenPublicationToTestRepository")
            }

            writeFuturePluginVersions {
                property(plugin.id, version)
            }
        }
    }
}
