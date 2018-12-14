/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `kotlin-library`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {

    compile(project(":distributionsDependencies"))

    compile(project(":kotlinDsl"))
    compile(project(":kotlinDslToolingBuilders"))

    compile(gradleTestKit())

    compile(library("junit"))
    compile(testLibrary("hamcrest"))

    compile("com.nhaarman:mockito-kotlin:1.6.0")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.2")
    compile("org.ow2.asm:asm:6.2.1")
}

// Custom Integration Testing

val prepareIntegrationTestFixtures by tasks.registering(GradleBuild::class) {
    dir = file("fixtures")
}

val distributionProjects =
    listOf(
        project(":kotlinDsl"),
        project(":kotlinDslProviderPlugins"),
        project(":kotlinDslToolingModels"),
        project(":kotlinDslToolingBuilders"))

val distribution by configurations.creating
dependencies {
    distributionProjects.forEach {
        distribution(it)
    }
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
