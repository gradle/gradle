import accessors.base
import accessors.publishing

import plugins.futurePluginVersionsFile
import plugins.KotlinDslPlugin

import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import com.gradle.publish.PluginBundleExtension


plugins {
    id("kotlin-dsl-module")
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
}


val kotlinDslPlugins = container(KotlinDslPlugin::class.java)
extensions.add("kotlinDslPlugins", kotlinDslPlugins)


pluginBundle {
    tags = listOf("Kotlin", "DSL")
    website = "https://github.com/gradle/kotlin-dsl"
    vcsUrl = "https://github.com/gradle/kotlin-dsl"
}


afterEvaluate {

    pluginBundle {
        mavenCoordinates.artifactId = base.archivesBaseName
    }

    kotlinDslPlugins.all {

        val plugin = this

        gradlePlugin {
            plugins {
                create(plugin.name) {
                    id = plugin.id
                    implementationClass = plugin.implementationClass
                }
            }
        }

        pluginBundle {
            plugins {
                create(plugin.name) {
                    id = plugin.id
                    displayName = plugin.displayName
                    description = plugin.displayName
                }
            }
        }
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

    publishing {
        repositories {
            maven {
                name = "test"
                url = uri("$buildDir/repository")
            }
        }
    }

    val publishPluginsToTestRepository = tasks.create("publishPluginsToTestRepository") {
        dependsOn("publishPluginMavenPublicationToTestRepository")
    }

    tasks.getByName("test") {
        dependsOn(publishPluginsToTestRepository)
    }

    val writeFuturePluginVersions = createWriteFuturePluginVersionsTask()

    afterEvaluate {

        kotlinDslPlugins.all {

            val plugin = this

            publishPluginsToTestRepository
                .dependsOn("publish${plugin.name.capitalize()}PluginMarkerMavenPublicationToTestRepository")

            writeFuturePluginVersions
                .property(plugin.id, version)
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


fun Project.gradlePlugin(action: GradlePluginDevelopmentExtension.() -> Unit) =
    configure(action)


fun Project.pluginBundle(action: PluginBundleExtension.() -> Unit) =
    configure(action)
