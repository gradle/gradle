import accessors.base
import accessors.gradlePlugin
import accessors.pluginBundle
import accessors.publishing

import plugins.futurePluginVersionsFile
import java.time.Year


plugins {
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

// publish plugin to local repository for integration testing -----------------
// See AbstractPluginTest
val localRepository = layout.buildDirectory.dir("repository")

val publishPluginsToTestRepository by tasks.registering {
    dependsOn("publishPluginMavenPublicationToTestRepository")
    // This should be unified with publish-public-libraries if possible
    doLast {
        localRepository.get().asFileTree.matching { include("**/maven-metadata.xml") }.forEach {
            it.writeText(it.readText().replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>"))
        }
        localRepository.get().asFileTree.matching { include("**/*.module") }.forEach {
            val content = it.readText()
                .replace("\"buildId\":\\s+\"\\w+\"".toRegex(), "\"buildId\": \"\"")
                .replace("\"size\":\\s+\\d+".toRegex(), "\"size\": 0")
                .replace("\"sha512\":\\s+\"\\w+\"".toRegex(), "\"sha512\": \"\"")
                .replace("\"sha1\":\\s+\"\\w+\"".toRegex(), "\"sha1\": \"\"")
                .replace("\"sha256\":\\s+\"\\w+\"".toRegex(), "\"sha256\": \"\"")
                .replace("\"md5\":\\s+\"\\w+\"".toRegex(), "\"md5\": \"\"")
            it.writeText(content)
        }
    }
}

afterEvaluate {
    val processIntegTestResources by tasks.existing(ProcessResources::class)
    val writeFuturePluginVersions by tasks.registering(WriteProperties::class) {
        outputFile = processIntegTestResources.get().futurePluginVersionsFile
    }
    processIntegTestResources {
        dependsOn(writeFuturePluginVersions)
    }

    publishing {
        repositories {
            maven {
                name = "test"
                url = uri(localRepository)
            }
        }
    }

    gradlePlugin {
        plugins.all {

            val plugin = this

            publishPluginsToTestRepository.configure {
                dependsOn("publish${plugin.name.capitalize()}PluginMarkerMavenPublicationToTestRepository")
            }

            writeFuturePluginVersions {
                property(plugin.id, version)
            }
        }
    }

    // For local consumption by tests - this should be unified with publish-public-libraries if possible
    configurations.create("localLibsRepositoryElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("gradle-local-repository"))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EMBEDDED))
        }
        isCanBeResolved = false
        isCanBeConsumed = true
        isVisible = false
        outgoing.artifact(localRepository) {
            builtBy(publishPluginsToTestRepository)
        }
    }
}
