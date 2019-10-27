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

package org.gradle.plugins.performance

import org.gradle.api.DefaultTask
import org.gradle.api.internal.GradleInternal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.caching.http.HttpBuildCache
import org.gradle.kotlin.dsl.*
import org.gradle.testing.performance.generator.tasks.RemoteProject
import java.io.File


// 5.1-commit-1a2b3c4d5e
private
val commitVersionRegex = """(\d+(\.\d+)+)-commit-[a-f0-9]+""".toRegex()


open class BuildCommitDistribution : DefaultTask() {
    @Input
    @Optional
    val commitBaseline = project.objects.property<String>()

    @OutputDirectory
    val commitDistributionHome = project.objects.directoryProperty()

    @OutputFile
    val commitDistributionToolingApiJar = project.objects.fileProperty()

    init {
        onlyIf { commitBaseline.getOrElse("").matches(commitVersionRegex) }
        commitDistributionHome.set(project.rootProject.layout.buildDirectory.dir(commitBaseline.map { "distributions/gradle-$it" }))
        commitDistributionToolingApiJar.set(project.rootProject.layout.buildDirectory.file(commitBaseline.map { "distributions/gradle-tooling-api-$it.jar" }))
    }

    @TaskAction
    fun buildCommitDistribution() {
        val rootProjectDir = project.rootProject.rootDir.absolutePath
        val commit = commitBaseline.map { it.substring(it.lastIndexOf('-') + 1) }
        tryBuildDistribution(RemoteProject.checkout(this, rootProjectDir, commit.get(), project.rootProject.buildDir.resolve("bcd")))
        println("Building the commit distribution succeeded, now the baseline is ${commitBaseline.get()}")
    }

    private
    fun tryBuildDistribution(checkoutDir: File) {
        project.exec({
            commandLine(*getBuildCommands(checkoutDir))
            workingDir = checkoutDir
        })
    }

    private
    fun getBuildCommands(checkoutDir: File): Array<String> {
        project.delete(commitDistributionHome.get().asFile)
        val buildCommands = mutableListOf(
            "./gradlew",
            "--init-script",
            File(checkoutDir, "gradle/init-scripts/build-scan.init.gradle.kts").absolutePath,
            "clean",
            ":install",
            "-Pgradle_installPath=" + commitDistributionHome.get().asFile.absolutePath,
            ":toolingApi:installToolingApiShadedJar",
            "-PtoolingApiShadedJarInstallPath=" + commitDistributionToolingApiJar.get().asFile.absolutePath)

        if (project.gradle.startParameter.isBuildCacheEnabled) {
            buildCommands.add("--build-cache")

            val buildCacheConf = (project.gradle as GradleInternal).settings.buildCache
            val remoteCache = buildCacheConf.remote as HttpBuildCache?
            if (remoteCache?.url != null) {
                buildCommands.add("-Dgradle.cache.remote.url=${remoteCache.url}")
                buildCommands.add("-Dgradle.cache.remote.username=${remoteCache.credentials.username}")
                buildCommands.add("-Dgradle.cache.remote.password=${remoteCache.credentials.password}")
            }
        }

        return buildCommands.toTypedArray()
    }
}
