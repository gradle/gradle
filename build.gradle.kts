import groovy.lang.GroovyObject

import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.ProjectSettings

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
    version = "1.0-SNAPSHOT"
}

val publishedPluginsVersion by extra { "1.0-rc-9" }
val futurePluginsVersion = "1.0-rc-10"
project(":plugins") {
    group = "org.gradle.kotlin"
    version = futurePluginsVersion
}

val publishedPluginsExperimentsVersion by extra { "0.1.15" }
val futurePluginsExperimentsVersion = "0.1.16"
project(":plugins-experiments") {
    group = "org.gradle.kotlin"
    version = futurePluginsExperimentsVersion
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
    }
}


// -- Integration testing ----------------------------------------------
val prepareIntegrationTestFixtures by task<GradleBuild> {
    dir = file("fixtures")
}

val customInstallationDir = file("$buildDir/custom/gradle-${gradle.gradleVersion}")

val copyCurrentDistro by task<Copy> {
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

val customInstallation by task<Copy> {
    description = "Copies latest gradle-kotlin-dsl snapshot over the custom installation."
    dependsOn(copyCurrentDistro)
    from(distribution)
    into("$customInstallationDir/lib")
}


// -- Performance testing ----------------------------------------------
val benchmark by task<integration.Benchmark> {
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


// --- Utility functions -----------------------------------------------
inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) =
    tasks.registering(T::class, configuration)
