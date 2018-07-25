/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.build.GradleDistribution
import org.gradle.build.GradleDistributionWithSamples
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.CommandLineArgumentProvider
import java.util.concurrent.Callable


/**
 * Base class for all tests that check the end-to-end behavior of a Gradle distribution.
 */
open class DistributionTest : Test() {

    @get:Input
    val operatingSystem by lazy {
        // the version currently differs between our dev infrastructure, so we only track the name and the architecture
        "${OperatingSystem.current().name} ${System.getProperty("os.arch")}"
    }

    @Internal
    val binaryDistributions = BinaryDistributions(project.layout)

    @Internal
    val gradleInstallationForTest = GradleInstallationForTestEnvironmentProvider(project, binaryDistributions.distributionsRequired)

    @Internal
    val libsRepository = LibsRepositoryEnvironmentProvider(project.layout)

    init {
        dependsOn(Callable { if (binaryDistributions.distributionsRequired) listOf("all", "bin", "src").map { ":distributions:${it}Zip" } else null })
        dependsOn(Callable { if (binaryDistributions.binZipRequired) ":distributions:binZip" else null })
        dependsOn(Callable { if (libsRepository.required) ":toolingApi:publishLocalArchives" else null })
        jvmArgumentProviders.add(gradleInstallationForTest)
        jvmArgumentProviders.add(BinaryDistributionsEnvironmentProvider(binaryDistributions))
        jvmArgumentProviders.add(libsRepository)
        systemProperty("java9Home", project.findProperty("java9Home") ?: System.getProperty("java9Home"))
    }
}


class LibsRepositoryEnvironmentProvider(layout: ProjectLayout) : CommandLineArgumentProvider, Named {

    @Internal
    val dir = layout.directoryProperty()

    @Input
    var required = false

    override fun asArguments() =
        if (required) mapOf("integTest.libsRepo" to dir.asFile.get().absolutePath).asSystemPropertyJvmArguments()
        else emptyList()

    override fun getName() =
        "libsRepository"
}


class GradleInstallationForTestEnvironmentProvider(project: Project, distributionsRequired: Boolean) : CommandLineArgumentProvider, Named {

    @Internal
    val gradleHomeDir = project.layout.directoryProperty()

    @Internal
    val gradleUserHomeDir = project.layout.directoryProperty()

    @Internal
    val toolingApiShadedJarDir = project.layout.directoryProperty()

    /**
     * The user home dir is not wiped out by clean.
     * Move the daemon working space underneath the build dir so they don't pile up on CI.
     */
    @Internal
    val daemonRegistry = project.layout.directoryProperty()

    @Nested
    val gradleDistribution: GradleDistribution = if (distributionsRequired) GradleDistributionWithSamples(project, gradleHomeDir) else GradleDistribution(project, gradleHomeDir)

    override fun asArguments() =
        mapOf(
            "integTest.gradleHomeDir" to gradleHomeDir.asFile.get().absolutePath,
            "integTest.gradleUserHomeDir" to gradleUserHomeDir.asFile.get().absolutePath,
            "org.gradle.integtest.daemon.registry" to daemonRegistry.asFile.get().absolutePath,
            "integTest.toolingApiShadedJarDir" to toolingApiShadedJarDir.asFile.get().absolutePath
        ).asSystemPropertyJvmArguments()

    override fun getName() =
        "gradleInstallationForTest"
}


class BinaryDistributions(layout: ProjectLayout) {

    @Input
    var binZipRequired = false

    @Input
    var distributionsRequired = false

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val distsDir = layout.directoryProperty()

    @Internal
    lateinit var distZipVersion: String
}


class BinaryDistributionsEnvironmentProvider(private val internalDistributions: BinaryDistributions) : CommandLineArgumentProvider, Named {

    @get:Nested
    @get:Optional
    val distributions
        get() =
            if (internalDistributions.distributionsRequired) internalDistributions
            else null

    @get:Input
    val binZipRequired
        get() = internalDistributions.binZipRequired

    override fun asArguments() =
        if (internalDistributions.binZipRequired || internalDistributions.distributionsRequired) {
            mapOf(
                "integTest.distsDir" to internalDistributions.distsDir.asFile.get().absolutePath,
                "integTest.distZipVersion" to internalDistributions.distZipVersion
            ).asSystemPropertyJvmArguments()
        } else {
            emptyList()
        }

    @Internal
    override fun getName() =
        "binaryDistributions"
}


internal
fun <K, V> Map<K, V>.asSystemPropertyJvmArguments(): Iterable<String> =
    map { (key, value) -> "-D$key=$value" }
