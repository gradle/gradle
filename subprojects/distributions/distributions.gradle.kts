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

import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.versioning.buildVersion
import org.gradle.plugins.install.Install

plugins {
    gradlebuild.internal.java
    gradlebuild.`add-verify-production-environment-task`
    gradlebuild.install
    gradlebuild.`binary-compatibility`
}

tasks.withType<AbstractArchiveTask>().configureEach {
    archiveBaseName.set("gradle")
    archiveVersion.set(rootProject.buildVersion.baseVersion)

    // The CI server looks for the distributions at this location
    destinationDirectory.set(rootProject.layout.buildDirectory.dir(rootProject.base.distsDirName))
}

tasks.named("clean").configure {
    delete(tasks.withType<AbstractArchiveTask>())
}

val gradleFullDocs by configurations.docsResolver("full-docs")
val gradleGettingStarted by configurations.docsResolver("getting-started")

dependencies {
    gradleFullDocs(project(":docs"))
    gradleGettingStarted(project(":docs"))

    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(library("ant"))

    integTestRuntimeOnly(project(":runtimeApiInfo"))
}

val zipRootFolder = "gradle-$version"

val binDistImage = copySpec {
    from("$rootDir/LICENSE")
    from("src/toplevel")
    into("bin") {
        from(configurations.gradleScripts)
        fileMode = Integer.parseInt("0755", 8)
    }
    into("lib") {
        from(configurations.coreRuntimeClasspath)
        into("plugins") {
            from(configurations.bundledPluginsRuntimeClasspath.get() - configurations.coreRuntimeClasspath.get())
        }
    }
}

val binWithDistDocImage = copySpec {
    with(binDistImage)
    from(gradleGettingStarted)
}

val allDistImage = copySpec {
    with(binWithDistDocImage)
    // TODO: Change this to a src publication too
    rootProject.subprojects {
        val subproject = this
        subproject.plugins.withId("gradlebuild.java-library") {
            into("src/${subproject.projectDir.name}") {
                from(subproject.sourceSets.main.get().allSource)
            }
        }
    }
    into("docs") {
        from(gradleFullDocs)
        exclude("samples/**")
    }
}

val docsDistImage = copySpec {
    from("$rootDir/LICENSE")
    from("src/toplevel")
    into("docs") {
        from(gradleFullDocs)
    }
}

val allZip = tasks.register<Zip>("allZip") {
    archiveClassifier.set("all")
    into(zipRootFolder) {
        with(allDistImage)
    }
}

val binZip = tasks.register<Zip>("binZip") {
    archiveClassifier.set("bin")
    into(zipRootFolder) {
        with(binWithDistDocImage)
    }
}

val srcZip = tasks.register<Zip>("srcZip") {
    archiveClassifier.set("src")
    into(zipRootFolder) {
        from(rootProject.file("gradlew")) {
            fileMode = Integer.parseInt("0755", 8)
        }
        from(rootProject.projectDir) {
            listOf("buildSrc", "buildSrc/subprojects/*", "subprojects/*").forEach {
                include("$it/*.gradle")
                include("$it/*.gradle.kts")
                include("$it/src/")
            }
            include("gradle.properties")
            include("buildSrc/gradle.properties")
            include("config/")
            include("gradle/")
            include("src/")
            include("*.gradle")
            include("*.gradle.kts")
            include("wrapper/")
            include("gradlew.bat")
            include("version.txt")
            include("released-versions.json")
            exclude("**/.gradle/")
        }
    }
}

val docsZip = tasks.register<Zip>("docsZip") {
    archiveClassifier.set("docs")
    into(zipRootFolder) {
        with(docsDistImage)
    }
}

tasks.register("buildDists") {
    dependsOn(allZip, binZip, srcZip, docsZip)
}

tasks.register<Install>("install") {
    description = "Installs the minimal distribution"
    group = "build"
    with(binDistImage)
}

tasks.register<Install>("installAll") {
    description = "Installs the full distribution"
    group = "build"
    with(allDistImage)
}

tasks.withType<IntegrationTest>().configureEach {
    binaryDistributions.distributionsRequired = true
    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(separator = ":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(separator = ":"))
}

fun ConfigurationContainer.docsResolver(type: String): NamedDomainObjectContainerCreatingDelegateProvider<Configuration> =
    creating {
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named("docs"))
        attributes.attribute(Attribute.of("type", String::class.java), type)
    }
