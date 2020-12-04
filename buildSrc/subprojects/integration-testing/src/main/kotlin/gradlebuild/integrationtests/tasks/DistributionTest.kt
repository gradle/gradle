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

package gradlebuild.integrationtests.tasks

import gradlebuild.cleanup.services.CachesCleaner
import gradlebuild.integrationtests.model.GradleDistribution
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestListener
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.util.SortedSet


/**
 * Tests that check the end-to-end behavior of a Gradle distribution.
 * They can have a locally built Gradle distribution on their runtime classpath
 * and distributions as well as local repositories as additional test inputs
 * to test functionality that requires rea distributions (like the wrapper)
 * or separately published libraries (like the Tooling API Jar).
 */
abstract class DistributionTest : Test() {

    /**
     * To further categorize tests. (We should simplify this and get rid of the subclasses if possible)
     */
    @get:Internal
    abstract val prefix: String

    /**
     * A local Gradle installation (unpacked distribution) to test against if the tests should fork a new Gradle process (non-embedded)
     */
    @Internal
    val gradleInstallationForTest = GradleInstallationForTestEnvironmentProvider(project, this)

    /**
     * A 'normalized' distribution to test against if needed
     */
    @Internal
    val normalizedDistributionZip = DistributionZipEnvironmentProvider(project, "normalized")

    /**
     * A 'bin' distribution to test - for integration tests testing the final distributions
     */
    @Internal
    val binDistributionZip = DistributionZipEnvironmentProvider(project, "bin")

    /**
     * A 'all' distribution to test - for integration tests testing the final distributions
     */
    @Internal
    val allDistributionZip = DistributionZipEnvironmentProvider(project, "all")

    /**
     * A 'docs' distribution to test - for integration tests testing the final distributions
     */
    @Internal
    val docsDistributionZip = DistributionZipEnvironmentProvider(project, "docs")

    /**
     * A 'src' distribution to test - for integration tests testing the final distributions
     */
    @Internal
    val srcDistributionZip = DistributionZipEnvironmentProvider(project, "src")

    /**
     * A local repository if needed by the tests (for Tooling API Jar or Kotlin DSL plugins)
     */
    @Internal
    val localRepository = LocalRepositoryEnvironmentProvider(project)

    @get:Internal
    abstract val tracker: Property<BuildService<*>>

    @get:Internal
    abstract val cachesCleaner: Property<CachesCleaner>

    @get:Internal
    @get:Option(option = "rerun", description = "Always rerun the task")
    val rerun: Property<Boolean> = project.objects.property<Boolean>().convention(false)

    @Option(option = "no-rerun", description = "Only run the task when necessary")
    fun setNoRerun(value: Boolean) {
        rerun.set(!value)
    }

    init {
        jvmArgumentProviders.add(gradleInstallationForTest)
        jvmArgumentProviders.add(localRepository)
        jvmArgumentProviders.add(normalizedDistributionZip)
        jvmArgumentProviders.add(binDistributionZip)
        jvmArgumentProviders.add(allDistributionZip)
        jvmArgumentProviders.add(docsDistributionZip)
        jvmArgumentProviders.add(srcDistributionZip)
        outputs.upToDateWhen {
            !rerun.get()
        }
    }

    override fun executeTests() {
        cachesCleaner.get().cleanUpCaches()

        val daemonTrackerService = tracker.get()
        val testListener = daemonTrackerService.javaClass.getMethod("newDaemonListener").invoke(daemonTrackerService) as TestListener
        addTestListener(testListener)
        super.executeTests()
    }
}


class LocalRepositoryEnvironmentProvider(project: Project) : CommandLineArgumentProvider, Named {

    @Internal
    val localRepo = project.objects.fileCollection()

    @get:Classpath
    val jars: SortedSet<File>
        get() = localRepo.asFileTree.matching { include("**/*.jar") }.files.toSortedSet()

    /**
     * Make sure this stays type FileCollection (lazy) to not loose dependency information.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val metadatas: FileCollection
        get() = localRepo.asFileTree.matching {
            include("**/*.pom")
            include("**/*.xml")
            include("**/*.module")
        }

    override fun asArguments() =
        if (!localRepo.isEmpty) mapOf("integTest.localRepository" to localRepo.singleFile).asSystemPropertyJvmArguments()
        else emptyList()

    @Internal
    override fun getName() =
        "libsRepository"
}


class GradleInstallationForTestEnvironmentProvider(project: Project, private val testTask: DistributionTest) : CommandLineArgumentProvider, Named {

    @Internal
    val gradleHomeDir = project.objects.fileCollection()

    @Internal
    val gradleUserHomeDir = project.objects.directoryProperty()

    @Internal
    val gradleSnippetsDir = project.objects.directoryProperty()

    @Internal
    val daemonRegistry = project.objects.directoryProperty()

    @get:Nested
    val gradleDistribution = GradleDistribution(gradleHomeDir)

    @Internal
    val distZipVersion = project.version.toString()

    override fun asArguments(): Iterable<String> {
        val distributionDir = if (gradleHomeDir.files.size == 1) gradleHomeDir.singleFile else null
        val distributionName = if (distributionDir != null) {
            // complete distribution is used from 'build/bin distribution'
            distributionDir.parentFile.parentFile.name
        } else {
            // gradle-runtime-api-info.jar in 'build/libs'
            testTask.classpath.filter { it.name.startsWith("gradle-runtime-api-info") }.singleFile.parentFile.parentFile.parentFile.name
        }
        return (
            (if (distributionDir != null) mapOf("integTest.gradleHomeDir" to distributionDir) else emptyMap()) + mapOf(
                "integTest.gradleUserHomeDir" to absolutePathOf(gradleUserHomeDir.dir(distributionName)),
                "integTest.samplesdir" to absolutePathOf(gradleSnippetsDir),
                "org.gradle.integtest.daemon.registry" to absolutePathOf(daemonRegistry.dir(distributionName)),
                "integTest.distZipVersion" to distZipVersion
            )
            ).asSystemPropertyJvmArguments()
    }

    @Internal
    override fun getName() =
        "gradleInstallationForTest"
}


class DistributionZipEnvironmentProvider(project: Project, private val distributionType: String) : CommandLineArgumentProvider, Named {

    @Classpath
    val distributionZip = project.objects.fileCollection()

    override fun asArguments() =
        if (distributionZip.isEmpty) {
            emptyList()
        } else {
            mapOf("integTest.${distributionType}Distribution" to distributionZip.singleFile).asSystemPropertyJvmArguments()
        }

    @Internal
    override fun getName() =
        "${distributionType}Distribution"
}


private
fun absolutePathOf(provider: Provider<Directory>) =
    provider.get().asFile.absolutePath


internal
fun <K, V> Map<K, V>.asSystemPropertyJvmArguments(): Iterable<String> =
    map { (key, value) -> "-D$key=$value" }
