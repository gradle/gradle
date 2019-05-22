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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.Test
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
    val binaryDistributions = BinaryDistributions(project.objects)

    @Internal
    val gradleInstallationForTest = GradleInstallationForTestEnvironmentProvider(project)

    @Internal
    val libsRepository = LibsRepositoryEnvironmentProvider(project.objects)

    @get:Internal
    @get:Option(option = "rerun", description = "Always rerun the task")
    val rerun: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)

    init {
        dependsOn(Callable { if (binaryDistributions.distributionsRequired) listOf("all", "bin", "src").map { ":distributions:${it}Zip" } else null })
        dependsOn(Callable { if (binaryDistributions.binZipRequired) ":distributions:binZip" else null })
        dependsOn(Callable { if (libsRepository.required) ":toolingApi:publishLocalArchives" else null })
        jvmArgumentProviders.add(gradleInstallationForTest)
        jvmArgumentProviders.add(BinaryDistributionsEnvironmentProvider(binaryDistributions))
        jvmArgumentProviders.add(libsRepository)
        outputs.upToDateWhen {
            !rerun.getOrElse(false)
        }
    }
}


class LibsRepositoryEnvironmentProvider(objects: ObjectFactory) : CommandLineArgumentProvider, Named {

    @Internal
    val dir = objects.directoryProperty()

    @Input
    var required = false

    override fun asArguments() =
        if (required) mapOf("integTest.libsRepo" to absolutePathOf(dir)).asSystemPropertyJvmArguments()
        else emptyList()

    override fun getName() =
        "libsRepository"
}


class GradleInstallationForTestEnvironmentProvider(project: Project) : CommandLineArgumentProvider, Named {

    @Internal
    val gradleHomeDir = project.objects.directoryProperty()

    @Internal
    val gradleUserHomeDir = project.objects.directoryProperty()

    @Internal
    val gradleGeneratedApiJarCacheDir = project.objects.directoryProperty()

    @Internal
    val toolingApiShadedJarDir = project.objects.directoryProperty()

    /**
     * The user home dir is not wiped out by clean.
     * Move the daemon working space underneath the build dir so they don't pile up on CI.
     */
    @Internal
    val daemonRegistry = project.objects.directoryProperty()

    @get:Nested
    val gradleDistribution = GradleDistribution(project, gradleHomeDir)

    override fun asArguments() =
        mapOf(
            "integTest.gradleHomeDir" to absolutePathOf(gradleHomeDir),
            "integTest.gradleUserHomeDir" to absolutePathOf(gradleUserHomeDir),
            "integTest.gradleGeneratedApiJarCacheDir" to absolutePathOf(gradleGeneratedApiJarCacheDir),
            "org.gradle.integtest.daemon.registry" to absolutePathOf(daemonRegistry),
            "integTest.toolingApiShadedJarDir" to absolutePathOf(toolingApiShadedJarDir)
        ).asSystemPropertyJvmArguments()

    override fun getName() =
        "gradleInstallationForTest"
}


class BinaryDistributions(objects: ObjectFactory) {

    @Input
    var binZipRequired = false

    @Input
    var distributionsRequired = false

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val distsDir = objects.directoryProperty()

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
                "integTest.distsDir" to absolutePathOf(internalDistributions.distsDir),
                "integTest.distZipVersion" to internalDistributions.distZipVersion
            ).asSystemPropertyJvmArguments()
        } else {
            emptyList()
        }

    @Internal
    override fun getName() =
        "binaryDistributions"
}


private
fun absolutePathOf(property: DirectoryProperty) =
    property.asFile.get().absolutePath


internal
fun <K, V> Map<K, V>.asSystemPropertyJvmArguments(): Iterable<String> =
    map { (key, value) -> "-D$key=$value" }
