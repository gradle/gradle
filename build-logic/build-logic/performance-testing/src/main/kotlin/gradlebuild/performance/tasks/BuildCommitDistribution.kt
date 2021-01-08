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

import gradlebuild.basics.repoRoot
import gradlebuild.performance.generator.tasks.RemoteProject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.GradleInternal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.caching.http.HttpBuildCache
import org.gradle.internal.os.OperatingSystem
import java.io.File


// 5.1-commit-1a2b3c4d5e
private
val commitVersionRegex = """(\d+(\.\d+)+)-commit-[a-f0-9]+""".toRegex()


abstract class BuildCommitDistribution : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val commitBaseline: Property<String>

    @get:OutputDirectory
    abstract val commitDistributionHome: DirectoryProperty

    @get:OutputFile
    abstract val commitDistributionToolingApiJar: RegularFileProperty

    init {
        onlyIf { commitBaseline.getOrElse("").matches(commitVersionRegex) }
        commitDistributionHome.set(project.layout.buildDirectory.dir(commitBaseline.map { "distributions/gradle-$it" }))
        commitDistributionToolingApiJar.set(project.layout.buildDirectory.file(commitBaseline.map { "distributions/gradle-tooling-api-$it.jar" }))
    }

    @TaskAction
    fun buildCommitDistribution() {
        val rootProjectDir = project.repoRoot().asFile.absolutePath
        val commit = commitBaseline.map { it.substring(it.lastIndexOf('-') + 1) }
        tryBuildDistribution(RemoteProject.checkout(this, rootProjectDir, commit.get()))
        println("Building the commit distribution succeeded, now the baseline is ${commitBaseline.get()}")
    }

    private
    fun tryBuildDistribution(checkoutDir: File) {
        project.exec {
            commandLine(*getBuildCommands())
            workingDir = checkoutDir
        }
    }

    private
    fun getBuildCommands(): Array<String> {
        project.delete(commitDistributionHome.get().asFile)
        val buildCommands = mutableListOf(
            "./gradlew" + (if (OperatingSystem.current().isWindows()) ".bat" else ""),
            "clean",
            ":distributions-full:install",
            "-Pgradle_installPath=" + commitDistributionHome.get().asFile.absolutePath,
            ":tooling-api:installToolingApiShadedJar",
            "-PtoolingApiShadedJarInstallPath=" + commitDistributionToolingApiJar.get().asFile.absolutePath
        )

        if (project.gradle.startParameter.isBuildCacheEnabled) {
            buildCommands.add("--build-cache")

            val buildCacheConf = (project.gradle as GradleInternal).settings.buildCache
            val remoteCache = buildCacheConf.remote as HttpBuildCache?
            if (remoteCache?.url != null) {
                buildCommands.add("-Dgradle.cache.remote.url=${remoteCache.url}")
                buildCommands.add("-Dgradle.cache.remote.username=${remoteCache.credentials.username}")
            }
        }

        return buildCommands.toTypedArray()
    }
}
