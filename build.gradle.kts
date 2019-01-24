import groovy.lang.GroovyObject

import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig

import java.time.LocalDate


buildscript {

    build.loadExtraPropertiesOf(project)

}

plugins {
    base
    kotlin("jvm") apply false
    id("org.jetbrains.gradle.plugin.idea-ext") version "0.4.2"
}

allprojects {
    group = "org.gradle"
    version = "1.2.0-SNAPSHOT"
}

val publishedPluginsVersion by extra { "1.1.3" }
val futurePluginsVersion = "1.1.4"
project(":plugins") {
    group = "org.gradle.kotlin"
    version = futurePluginsVersion
}

// --- Configure publications ------------------------------------------
val distributionProjects =
    listOf(
        project(":provider"),
        project(":provider-plugins"),
        project(":tooling-models"),
        project(":tooling-builders"))

// For documentation and meaningful `./gradlew dependencies` output
val distribution by configurations.creating
dependencies {
    distributionProjects.forEach {
        distribution(it)
    }
}

allprojects {
    repositories {
        gradlePluginPortal()
        repositories {
            maven {
                name = "kotlinx"
                url = uri("https://kotlin.bintray.com/kotlinx/")
            }
        }
    }
}


// -- Integration testing ----------------------------------------------
val prepareIntegrationTestFixtures by tasks.registering(GradleBuild::class) {
    dir = file("fixtures")
}

val customInstallationDir = file("$buildDir/custom/gradle-${gradle.gradleVersion}")

val copyCurrentDistro by tasks.registering(Copy::class) {
    description = "Copies the current Gradle distro into '$customInstallationDir'."

    from(gradle.gradleHomeDir)
    into(customInstallationDir)
    exclude("**/*kotlin*")

    // preserve last modified date on each file to make it easier
    // to check which files were patched by next step
    val copyDetails = mutableListOf<FileCopyDetails>()
    eachFile { copyDetails.add(this) }
    doLast {
        copyDetails.forEach { details ->
            File(customInstallationDir, details.path).setLastModified(details.lastModified)
        }
    }

    // don't bother recreating it
    onlyIf { !customInstallationDir.exists() }
}

val customInstallation by tasks.registering(Copy::class) {
    description = "Copies latest gradle-kotlin-dsl snapshot over the custom installation."
    dependsOn(copyCurrentDistro)
    from(distribution)
    into("$customInstallationDir/lib")
}


// -- Performance testing ----------------------------------------------
val benchmark by tasks.registering(integration.Benchmark::class) {
    excludingSamplesMatching(
        "android",
        "source-control"
    )
    latestInstallation = customInstallationDir
    dependsOn(customInstallation)
}


// -- IntelliJ IDEA configuration --------------------------------------
idea {
    project {
        this as ExtensionAware
        configure<ProjectSettings> {
            this as ExtensionAware
            doNotDetectFrameworks("android", "web")
            configure<TaskTriggersConfig> {
                // Build the `customInstallation` after the initial import to:
                // 1. ensure generated code is available to the IDE
                // 2. allow integration tests to be executed
                afterSync(customInstallation.get())
            }
            configure<CopyrightConfiguration> {
                useDefault = "ASL2"
                profiles {
                    create("ASL2") {
                        keyword = "Copyright"
                        notice = """
                            Copyright ${LocalDate.now().year} the original author or authors.

                            Licensed under the Apache License, Version 2.0 (the "License");
                            you may not use this file except in compliance with the License.
                            You may obtain a copy of the License at

                                 http://www.apache.org/licenses/LICENSE-2.0

                            Unless required by applicable law or agreed to in writing, software
                            distributed under the License is distributed on an "AS IS" BASIS,
                            WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                            See the License for the specific language governing permissions and
                            limitations under the License.
                        """.trimIndent()
                    }
                }
            }
        }
    }
}
