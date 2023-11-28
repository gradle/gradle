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

import gradlebuild.basics.repoRoot
import gradlebuild.cleanup.services.CachesCleaner
import gradlebuild.integrationtests.extension.IntegrationTestExtension
import gradlebuild.integrationtests.setSystemPropertiesOfTestJVM
import gradlebuild.integrationtests.tasks.DistributionTest

plugins {
    java
    id("gradlebuild.module-identity")
}

val docsProjectLocation = "platform/documentation/docs" // TODO instead of reaching directly into the project we should use dependency management

val intTestHomeDir = repoRoot().dir("intTestHomeDir")

val cachesCleanerService = gradle.sharedServices.registerIfAbsent("cachesCleaner", CachesCleaner::class) {
    parameters.gradleVersion = moduleIdentity.version.map { it.version }
    parameters.homeDir = intTestHomeDir
}

fun Gradle.rootBuild(): Gradle = parent.let { it?.rootBuild() ?: this }

tasks.withType<DistributionTest>().configureEach {
    shouldRunAfter("test")

    setJvmArgsOfTestJvm()
    setSystemPropertiesOfTestJVM("default")
    configureGradleTestEnvironment()
    addSetUpAndTearDownActions()
}

fun executerRequiresDistribution(taskName: String) =
    !taskName.startsWith("embedded") || taskName.contains("CrossVersion") // <- Tooling API [other-version]->[current]

fun executerRequiresFullDistribution(taskName: String) =
    taskName.startsWith("noDaemon")

fun DistributionTest.addSetUpAndTearDownActions() {
    cachesCleaner = cachesCleanerService
    gradle.rootBuild().sharedServices.registrations.findByName("daemonTracker")?.let {
        tracker = it.service
    }
}

fun DistributionTest.configureGradleTestEnvironment() {
    val taskName = name

    gradleInstallationForTest.apply {
        if (executerRequiresDistribution(taskName)) {
            gradleHomeDir = if (executerRequiresFullDistribution(taskName)) {
                configurations["${prefix}TestFullDistributionRuntimeClasspath"]
            } else {
                configurations["${prefix}TestDistributionRuntimeClasspath"]
            }
        }
        // Set the base user home dir to be share by integration tests.
        // The actual user home dir will be a subfolder using the name of the distribution.
        gradleUserHomeDir = intTestHomeDir
        // The user home dir is not wiped out by clean. Move the daemon working space underneath the build dir so they don't pile up on CI.
        // The actual daemon registry dir will be a subfolder using the name of the distribution.
        daemonRegistry = repoRoot().dir("build/daemon")
        gradleSnippetsDir = repoRoot().dir("$docsProjectLocation/src/snippets")
    }

    // Wire the different inputs for local distributions and repos that are declared by dependencies in the build scripts
    normalizedDistributionZip.distributionZip = configurations["${prefix}TestNormalizedDistributionPath"]
    binDistributionZip.distributionZip = configurations["${prefix}TestBinDistributionPath"]
    allDistributionZip.distributionZip = configurations["${prefix}TestAllDistributionPath"]
    docsDistributionZip.distributionZip = configurations["${prefix}TestDocsDistributionPath"]
    srcDistributionZip.distributionZip = configurations["${prefix}TestSrcDistributionPath"]
    localRepository.localRepo = configurations["${prefix}TestLocalRepositoryPath"]
}

fun DistributionTest.setJvmArgsOfTestJvm() {
    jvmArgs("-Xmx${project.the<IntegrationTestExtension>().testJvmXmx.get()}", "-XX:+HeapDumpOnOutOfMemoryError")
    if (!javaVersion.isJava8Compatible) {
        jvmArgs("-XX:MaxPermSize=768m")
    }
}
