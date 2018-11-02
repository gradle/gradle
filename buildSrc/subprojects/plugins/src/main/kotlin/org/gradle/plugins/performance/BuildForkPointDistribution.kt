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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.caching.http.HttpBuildCache
import org.gradle.kotlin.dsl.execAndGetStdout
import java.io.File
import org.gradle.kotlin.dsl.*
import org.gradle.util.GFileUtils


open class BuildForkPointDistribution : DefaultTask() {
    // 5.1-commit-1a2b3c4d5e
    @Input
    val forkPointDistributionVersion = project.objects.property<String>()

    @OutputDirectory
    val forkPointDistributionHome = project.objects.directoryProperty()

    @OutputFile
    val forkPointToolingApiJar = project.objects.fileProperty()

    init {
        forkPointDistributionHome.set(project.rootProject.layout.buildDirectory.dir(forkPointDistributionVersion.map { "distributions/gradle-$it" }))
        forkPointToolingApiJar.set(project.rootProject.layout.buildDirectory.file(forkPointDistributionVersion.map { "distributions/gradle-tooling-api-$it.jar" }))
    }

    @TaskAction
    fun buildDistribution() {
        prepareGradleRepository()
        tryBuildDistribution()
        println("Building fork point succeeded, now the baseline is ${forkPointDistributionVersion.get()}")
    }

    private
    fun prepareGradleRepository() {
        val cloneDir = getGradleCloneTmpDir()
        if (!File(cloneDir, ".git").isDirectory) {
            GFileUtils.mkdirs(cloneDir)
            project.execAndGetStdout(cloneDir.parentFile, "git", "clone", project.rootDir.absolutePath, getGradleCloneTmpDir().absolutePath, "--no-checkout")
        }
        project.execAndGetStdout(cloneDir, "git", "reset", "--hard")
        project.execAndGetStdout(cloneDir, "git", "fetch")
    }

    private
    fun getGradleCloneTmpDir() = File(project.rootProject.buildDir, "tmp/gradle-find-forkpoint")

    private
    fun tryBuildDistribution() {
        val version = forkPointDistributionVersion.get()
        val commit = version.substring(version.lastIndexOf('-') + 1)
        project.execAndGetStdout(getGradleCloneTmpDir(), "git", "checkout", commit, "--force")
        project.execAndGetStdout(getGradleCloneTmpDir(), *getBuildCommands())
    }

    private
    fun getBuildCommands(): Array<String> {
        project.delete(forkPointDistributionHome.get().asFile)
        val buildCommands = mutableListOf(
            "./gradlew",
            "--init-script",
            File(getGradleCloneTmpDir(), "gradle/init-scripts/build-scan.init.gradle.kts").absolutePath,
            "clean",
            ":install",
            "-Pgradle_installPath=" + forkPointDistributionHome.get().asFile.absolutePath,
            ":toolingApi:installToolingApiShadedJar",
            "-PtoolingApiShadedJarInstallPath=" + forkPointToolingApiJar.get().asFile.absolutePath)

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
