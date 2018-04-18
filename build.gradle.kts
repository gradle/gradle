import groovy.lang.GroovyObject

import org.jetbrains.gradle.ext.ProjectSettings

import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig
import org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask

import java.time.LocalDate


buildscript {

    build.loadExtraPropertiesOf(project)

    repositories {
        maven(url = build.kotlinRepo)
    }

    val kotlinVersion: String by extra
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    base
    id("com.jfrog.artifactory") version "4.1.1"
    id("org.jetbrains.gradle.plugin.idea-ext") version "0.1"
}

allprojects {
    group = "org.gradle"
    version = "0.17.0-SNAPSHOT"
}

val publishedPluginsVersion by extra { "0.16.2" }
val futurePluginsVersion = "0.16.3"
project(":plugins") {
    group = "org.gradle.kotlin"
    version = futurePluginsVersion
}

val publishedPluginsExperimentsVersion by extra { "0.1.5" }
val futurePluginsExperimentsVersion = "0.1.6"
project(":plugins-experiments") {
    group = "org.gradle.kotlin"
    version = futurePluginsExperimentsVersion
}

// --- Configure publications ------------------------------------------
val publishedProjects =
    listOf(
        project(":provider"),
        project(":tooling-models"),
        project(":tooling-builders"))

// For documentation and meaningful `./gradlew dependencies` output
val distribution by configurations.creating
dependencies {
    publishedProjects.forEach {
        distribution(it)
    }
}

tasks {
    // Disable publication on root project
    "artifactoryPublish"(ArtifactoryTask::class) {
        skip = true
    }
}

artifactory {
    setContextUrl("https://repo.gradle.org/gradle")
    publish(delegateClosureOf<PublisherConfig> {
        repository(delegateClosureOf<GroovyObject> {
            val targetRepoKey = "libs-${buildTagFor(project.version as String)}s-local"
            setProperty("repoKey", targetRepoKey)
            setProperty("username", project.findProperty("artifactory_user") ?: "nouser")
            setProperty("password", project.findProperty("artifactory_password") ?: "nopass")
            setProperty("maven", true)
        })
        defaults(delegateClosureOf<GroovyObject> {
            invokeMethod("publications", "mavenJava")
        })
    })
    resolve(delegateClosureOf<ResolverConfig> {
        setProperty("repoKey", "repo")
    })
}

fun buildTagFor(version: String): String =
    when (version.substringAfterLast('-')) {
        "SNAPSHOT"                  -> "snapshot"
        in Regex("""M\d+[a-z]*$""") -> "milestone"
        else                        -> "release"
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
    dependsOn(customInstallation)
    latestInstallation = customInstallationDir
}


// -- IntelliJ IDEA configuration --------------------------------------
idea {
    project {
        (this as ExtensionAware)
        configure<ProjectSettings> {
            doNotDetectFrameworks("android", "web")
            copyright {
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
operator fun Regex.contains(s: String) = matches(s)

inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)
