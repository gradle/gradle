import groovy.lang.GroovyObject

import org.jetbrains.gradle.ext.ProjectSettings

import java.time.LocalDate


buildscript {

    build.loadExtraPropertiesOf(project)

    val kotlinVersion: String by extra
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }

    repositories {
        gradlePluginPortal()
    }
}

plugins {
    base
    id("org.jetbrains.gradle.plugin.idea-ext") version "0.1"
}

allprojects {
    group = "org.gradle"
    version = "0.17.4"
    createOpenTestReportTasks()
}

val publishedPluginsVersion by extra { "0.17.3" }
val futurePluginsVersion = "0.17.4"
project(":plugins") {
    group = "org.gradle.kotlin"
    version = futurePluginsVersion
}

val publishedPluginsExperimentsVersion by extra { "0.1.7" }
val futurePluginsExperimentsVersion = "0.1.8"
project(":plugins-experiments") {
    group = "org.gradle.kotlin"
    version = futurePluginsExperimentsVersion
}

// --- Configure publications ------------------------------------------
val publishedProjects =
    listOf(
        project(":provider"),
        project(":provider-plugins"),
        project(":tooling-models"),
        project(":tooling-builders"))

// For documentation and meaningful `./gradlew dependencies` output
val distribution by configurations.creating
dependencies {
    publishedProjects.forEach {
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
inline fun <reified T : Task> task(noinline configuration: T.() -> Unit) =
    tasks.creating(T::class, configuration)


fun Project.createOpenTestReportTasks() {
    tasks.withType<Test> {
        val test = this
        reports.all {
            val report = this
            tasks.createLater("open${test.name.capitalize()}${report.name.capitalize()}Report") {
                group = JavaBasePlugin.VERIFICATION_GROUP
                description = "Opens the ${report.name} report produced by the ${test.name} task."
                doLast {
                    exec {
                        commandLine("open", report.destination)
                    }
                }
            }
        }
    }
}
