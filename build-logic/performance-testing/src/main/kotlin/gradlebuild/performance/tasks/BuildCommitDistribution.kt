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

package gradlebuild.performance.tasks

import com.google.gson.Gson
import gradlebuild.basics.repoRoot
import gradlebuild.identity.model.ReleasedVersions
import gradlebuild.performance.generator.tasks.RemoteProject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import org.gradle.util.GradleVersion
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Properties
import javax.inject.Inject


// 5.1-commit-1a2b3c4d5e
private
val commitVersionRegex = """(\d+(\.\d+)+)-commit-[a-f0-9]+""".toRegex()


/*
The error output looks like this:

    Downloading https://services.gradle.org/distributions-snapshots/gradle-7.5-20220202183149+0000-bin.zip
    Exception in thread "main" java.io.FileNotFoundException: https://downloads.gradle-dn.com/distributions-snapshots/gradle-7.5-20220202183149+0000-bin.zip
 */
private
val oldWrapperMissingErrorRegex = """\Qjava.io.FileNotFoundException:\E.*/distributions-snapshots/gradle-([\d.]+)""".toRegex()


@CacheableTask
abstract class BuildCommitDistribution @Inject internal constructor(
    private val fsOps: FileSystemOperations,
    private val execOps: ExecOperations,
    private val javaToolchainService: JavaToolchainService
) : DefaultTask() {
    @get:Internal
    abstract val releasedVersionsFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val commitBaseline: Property<String>

    @get:OutputFile
    abstract val commitDistribution: RegularFileProperty

    @get:OutputFile
    abstract val commitDistributionToolingApiJar: RegularFileProperty

    init {
        onlyIf { commitBaseline.getOrElse("").matches(commitVersionRegex) }
    }

    @TaskAction
    fun buildCommitDistribution() {
        val rootProjectDir = project.repoRoot().asFile.absolutePath
        val commit = commitBaseline.map { it.substring(it.lastIndexOf('-') + 1) }
        val checkoutDir = RemoteProject.checkout(fsOps, execOps, rootProjectDir, commit.get(), temporaryDir)

        tryBuildDistribution(checkoutDir)
        copyToFinalDestination(checkoutDir)

        println("Building the commit distribution in $checkoutDir succeeded, now the baseline is ${commitBaseline.get()}")
    }

    /**
     * Sometimes, the nightly might be cleaned up before GA comes out. If this happens, we fall back to latest RC version or nightly version.
     */
    private
    fun determineClosestReleasedVersion(expectedBaseVersion: GradleVersion): GradleVersion {
        val releasedVersions = releasedVersionsFile.asFile.get().reader().use {
            Gson().fromJson(it, ReleasedVersions::class.java)
        }
        if (releasedVersions.finalReleases.any { it.gradleVersion() == expectedBaseVersion }) {
            return expectedBaseVersion
        } else if (releasedVersions.latestRc.gradleVersion().baseVersion == expectedBaseVersion) {
            return releasedVersions.latestRc.gradleVersion()
        } else if (releasedVersions.latestReleaseSnapshot.gradleVersion().baseVersion == expectedBaseVersion) {
            return releasedVersions.latestReleaseSnapshot.gradleVersion()
        } else {
            error("Expected version: $expectedBaseVersion but can't find it")
        }
    }

    @Suppress("SpreadOperator")
    private
    fun runDistributionBuild(checkoutDir: File, os: OutputStream) {
        val cmdArgs = getBuildCommands()
        println("Building commit distribution with command: ${cmdArgs.joinToString(" ")}")
        execOps.exec {
            commandLine(*cmdArgs)
            workingDir = checkoutDir
            standardOutput = os
            errorOutput = os
        }
    }

    private
    fun copyToFinalDestination(checkoutDir: File) {
        val baseVersion = commitBaseline.get().substringBefore("-")
        val distribution = checkoutDir.resolve("subprojects/distributions-full/build/distributions/gradle-$baseVersion-bin.zip")
        if (!distribution.isFile) {
            error("${distribution.absolutePath} doesn't exist. Did you set the wrong base version?\n${distribution.parentFile.list()?.joinToString("\n")}")
        }
        distribution.copyTo(commitDistribution.asFile.get(), true)
    }

    private
    fun tryBuildDistribution(checkoutDir: File) {
        val output = ByteArrayOutputStream()
        try {
            runDistributionBuild(checkoutDir, output)
            println("Building commit distribution succeeded:\n$output")
        } catch (e: Exception) {
            val outputString = output.toByteArray().decodeToString()
            if (failedBecauseOldWrapperMissing(outputString)) {
                val expectedWrapperVersion = oldWrapperMissingErrorRegex.find(outputString)!!.groups[1]!!.value
                val closestReleasedVersion = determineClosestReleasedVersion(GradleVersion.version(expectedWrapperVersion))
                val repository = if (closestReleasedVersion.isSnapshot) "distributions-snapshots" else "distributions"
                val wrapperPropertiesFile = checkoutDir.resolve("gradle/wrapper/gradle-wrapper.properties")
                val wrapperProperties = Properties().apply {
                    load(wrapperPropertiesFile.inputStream())
                    this["distributionUrl"] = "https://services.gradle.org/$repository/gradle-${closestReleasedVersion.version}-bin.zip"
                }
                wrapperProperties.store(wrapperPropertiesFile.outputStream(), "Modified by `BuildCommitDistribution` task")
                println("First attempt to build commit distribution failed: \n\n$outputString\n\nTrying again with ${closestReleasedVersion.version}")

                val output2 = ByteArrayOutputStream()
                try {
                    runDistributionBuild(checkoutDir, output2)
                } catch (e: Exception) {
                    throw GradleException("Failed to build commit distribution:\n\n${output2.toByteArray().decodeToString()}", e)
                }

            } else {
                throw GradleException("Failed to build commit distribution:\n\n${output.toByteArray().decodeToString()}", e)
            }
        }
    }

    private
    fun failedBecauseOldWrapperMissing(buildOutput: String): Boolean {
        return buildOutput.contains(oldWrapperMissingErrorRegex)
    }

    private
    fun getJavaHomeFor(version: Int): String {
        return javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(version)) }.get().metadata.installationPath.asFile.absolutePath
    }

    private
    fun getBuildCommands(): Array<String> {
        val mirrorInitScript = temporaryDir.resolve("mirroring-init-script.gradle")
        BuildCommitDistribution::class.java.getResource("/mirroring-init-script.gradle")?.let { mirrorInitScript.writeText(it.readText()) }

        val buildCommands = mutableListOf(
            "./gradlew" + (if (OperatingSystem.current().isWindows) ".bat" else ""),
            "--no-configuration-cache",
            "--init-script",
            mirrorInitScript.absolutePath,
        )

        System.getProperty(PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY)?.let {
            buildCommands.add("-D${PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=$it")
        }

        buildCommands += listOf(
            "clean",
            "-Dscan.tag.BuildCommitDistribution",
            ":distributions-full:binDistributionZip",
            ":tooling-api:installToolingApiShadedJar",
            "-PtoolingApiShadedJarInstallPath=" + commitDistributionToolingApiJar.get().asFile.absolutePath,
            "-Porg.gradle.java.installations.paths=${getJavaHomeFor(11)},${getJavaHomeFor(17)}",
            "-PbuildCommitDistribution=true",
            "-Dorg.gradle.ignoreBuildJavaVersionCheck=true"
        )

        if (project.gradle.startParameter.isBuildCacheEnabled) {
            buildCommands.add("--build-cache")
        }

        return buildCommands.toTypedArray()
    }
}
