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

package org.gradle.testing

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.build.GradleDistribution
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.CommandLineArgumentProvider
import java.util.concurrent.Callable

/**
 * Base class for all tests that check the end-to-end behavior of a Gradle distribution.
 */
open class DistributionTest : Test() {

    @get: Input
    val operatingSystem: String by lazy {
        val current = OperatingSystem.current()
        // the version currently differs between our dev infrastructure, so we only track the name and the architecture
        current.getName() + " " + System.getProperty("os.arch")
    }

    @Internal
    val gradleInstallationForTest: GradleInstallationForTestEnvironmentProvider = GradleInstallationForTestEnvironmentProvider(project)

    @Internal
    val binaryDistributions: BinaryDistributions = BinaryDistributions(project.layout)

    @Internal
    val libsRepository: LibsRepositoryEnvironmentProvider = LibsRepositoryEnvironmentProvider(project.layout)

    init {
        dependsOn(Callable { if (binaryDistributions.distributionsRequired) listOf("all", "bin", "src").map { ":distributions:${it}Zip" } else null })
        dependsOn(Callable { if (binaryDistributions.binZipRequired) ":distributions:binZip" else null })
        dependsOn(Callable { if (libsRepository.required) ":toolingApi:publishLocalArchives" else null })
        jvmArgumentProviders.add(gradleInstallationForTest)
        jvmArgumentProviders.add(BinaryDistributionsEnvironmentProvider(binaryDistributions))
        jvmArgumentProviders.add(libsRepository)
    }
}

fun <K, V> Map<K, V>.asSystemPropertyJvmArguments(): Iterable<String> = this.map { (key, value) -> "-D$key=$value" }

class LibsRepositoryEnvironmentProvider(layout: ProjectLayout) : CommandLineArgumentProvider, Named {

    @Internal
    val dir: DirectoryProperty = layout.directoryProperty()

    @Input
    var required: Boolean = false

    override fun asArguments(): Iterable<String> =
        if (required) mapOf("integTest.libsRepo" to dir.asFile.get().absolutePath).asSystemPropertyJvmArguments() else emptyList()

    override fun getName(): String = "libsRepository"
}

class GradleInstallationForTestEnvironmentProvider(project: Project) : CommandLineArgumentProvider, Named {

    @Internal
    val gradleHomeDir: DirectoryProperty = project.layout.directoryProperty()

    @Internal
    val gradleUserHomeDir: DirectoryProperty = project.layout.directoryProperty()

    @Internal
    val toolingApiShadedJarDir: DirectoryProperty = project.layout.directoryProperty()

    /**
     * The user home dir is not wiped out by clean.
     * Move the daemon working space underneath the build dir so they don't pile up on CI.
     */
    @Internal
    val daemonRegistry: DirectoryProperty = project.layout.directoryProperty()

    @Nested
    val gradleDistribution = GradleDistribution(project, gradleHomeDir)

    override fun asArguments(): Iterable<String> =
        mapOf(
            "integTest.gradleHomeDir" to gradleHomeDir.asFile.get().absolutePath,
            "integTest.gradleUserHomeDir" to gradleUserHomeDir.asFile.get().absolutePath,
            "org.gradle.integtest.daemon.registry" to daemonRegistry.asFile.get().absolutePath,
            "integTest.toolingApiShadedJarDir" to toolingApiShadedJarDir.getAsFile().get().absolutePath
        ).asSystemPropertyJvmArguments()

    override fun getName(): String = "gradleInstallationForTest"
}

class BinaryDistributions(layout: ProjectLayout) {

    @Input
    var binZipRequired: Boolean = false

    @Input
    var distributionsRequired: Boolean = false

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val distsDir: DirectoryProperty = layout.directoryProperty()

    @Internal
    lateinit var distZipVersion: String
}

class BinaryDistributionsEnvironmentProvider(private val internalDistributions: BinaryDistributions) : CommandLineArgumentProvider, Named {

    @get: Nested
    @get: Optional
    val distributions: BinaryDistributions?
        get() = if (internalDistributions.distributionsRequired) internalDistributions else null

    @get: Input
    val binZipRequired
        get() = internalDistributions.binZipRequired

    override fun asArguments(): Iterable<String> =
        if (internalDistributions.binZipRequired || internalDistributions.distributionsRequired)
            mapOf(
                "integTest.distsDir" to internalDistributions.distsDir.asFile.get().absolutePath,
                "integTest.distZipVersion" to internalDistributions.distZipVersion
            ).asSystemPropertyJvmArguments()
        else
            listOf()

    @Internal
    override fun getName(): String = "binaryDistributions"
}

