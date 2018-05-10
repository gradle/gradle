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


pluginBundle {
    tags = listOf("Kotlin", "DSL")
    website = "https://github.com/gradle/kotlin-dsl"
    vcsUrl = "https://github.com/gradle/kotlin-dsl"
}


afterEvaluate {

    pluginBundle {
        mavenCoordinates.artifactId = base.archivesBaseName
    }
}


tasks.getByName("test") {
    dependsOn(rootProject.tasks["customInstallation"])
}


workAroundTestKitWithPluginClassPathIssues()


// TODO Remove work around for TestKit withPluginClassPath() issues
// See https://github.com/gradle/kotlin-dsl/issues/492
// Also see AbstractPluginTest
fun Project.workAroundTestKitWithPluginClassPathIssues() {

    val publishPluginsToTestRepository = tasks.create("publishPluginsToTestRepository") {
        dependsOn("publishPluginMavenPublicationToTestRepository")
    }

    tasks.getByName("test") {
        dependsOn(publishPluginsToTestRepository)
    }

    val writeFuturePluginVersions = createWriteFuturePluginVersionsTask()

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

                publishPluginsToTestRepository
                    .dependsOn("publish${plugin.name.capitalize()}PluginMarkerMavenPublicationToTestRepository")

                writeFuturePluginVersions
                    .property(plugin.id, version)
            }
        }
    }
}


fun Project.createWriteFuturePluginVersionsTask(): WriteProperties {
    val processTestResources = tasks["processTestResources"] as ProcessResources
    return task<WriteProperties>("writeFuturePluginVersions") {
        outputFile = processTestResources.futurePluginVersionsFile
        processTestResources.dependsOn(this)
    }
}
