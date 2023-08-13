/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import gradlebuild.capitalize
import gradlebuild.pluginpublish.extension.PluginPublishExtension
import java.time.Year

plugins {
    id("gradlebuild.module-identity")
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
}

extensions.create<PluginPublishExtension>("pluginPublish", gradlePlugin)

tasks.validatePlugins {
    enableStricterValidation = true
}

// Remove gradleApi() and gradleTestKit() as we want to compile/run against Gradle modules
// TODO consider splitting `java-gradle-plugin` to provide only what's necessary here
configurations.all {
    withDependencies {
        remove(project.dependencies.gradleApi())
        remove(project.dependencies.gradleTestKit())
    }
}

publishing.publications.withType<MavenPublication>().configureEach {
    if (name == "pluginMaven") {
        groupId = project.group.toString()
        artifactId = moduleIdentity.baseName.get()
    }
    pom {
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
    }
}

// publish plugin to local repository for integration testing -----------------
// See AbstractPluginTest
val localRepository = layout.buildDirectory.dir("repository")

val publishPluginsToTestRepository by tasks.registering {
    dependsOn("publishPluginMavenPublicationToTestRepository")
    val repoDir = localRepository // Prevent capturing the Gradle script instance for configuration cache compatibility
    // This should be unified with publish-public-libraries if possible
    doLast {
        repoDir.get().asFileTree.matching { include("**/maven-metadata.xml") }.forEach {
            it.writeText(it.readText().replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>"))
        }
        repoDir.get().asFileTree.matching { include("**/*.module") }.forEach {
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

val futurePluginVersionsPropertiesFile = layout.buildDirectory.file("generated-resources/future-plugin-versions/future-plugin-versions.properties")
val writeFuturePluginVersions by tasks.registering(WriteProperties::class) {
    destinationFile = futurePluginVersionsPropertiesFile
}
val futurePluginVersionsDestDir = futurePluginVersionsPropertiesFile.map { it.asFile.parentFile }
sourceSets.main {
    output.dir(mapOf("builtBy" to writeFuturePluginVersions), futurePluginVersionsDestDir)
}
configurations.runtimeElements {
    outgoing {
        variants.named("resources") {
            artifact(futurePluginVersionsDestDir) {
                builtBy(writeFuturePluginVersions)
            }
        }
    }
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
    website = "https://github.com/gradle/gradle/tree/HEAD/platforms/core-configuration/kotlin-dsl-plugins"
    vcsUrl = "https://github.com/gradle/gradle/tree/HEAD/platforms/core-configuration/kotlin-dsl-plugins"

    plugins.all {

        val plugin = this

        tags.addAll("Kotlin", "DSL")

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

// Workaround for https://github.com/gradle/gradlecom/issues/627
configurations.archives.get().allArtifacts.removeIf {
    it.name != "plugins"
}
