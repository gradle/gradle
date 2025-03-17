/*
 * Copyright 2024 the original author or authors.
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

/**
 * TODO: Remove once with Gradle 9.0, used so org.gradle.kotlin.dsl.* is kept
 */
@file:Suppress("UnusedImport")

package gradlebuild.basics

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject
/**
 * Used to import assign for Gradle 9.0
 * TODO: Remove once with Gradle 9.0
 */
import org.gradle.kotlin.dsl.*


abstract class BuildEnvironmentService : BuildService<BuildEnvironmentService.Parameters> {

    interface Parameters : BuildServiceParameters {
        val rootProjectDir: DirectoryProperty
        val rootProjectBuildDir: DirectoryProperty
    }

    @get:Inject
    abstract val providers: ProviderFactory

    val gitCommitId = git("rev-parse", "HEAD")
    val gitBranch = git("rev-parse", "--abbrev-ref", "HEAD")

    @Suppress("UnstableApiUsage")
    private
    fun git(vararg args: String): Provider<String> {
        val projectDir = parameters.rootProjectDir.asFile.get()
        val execOutput = providers.exec {
            workingDir = projectDir
            DeprecationLogger.whileDisabled {
                isIgnoreExitValue = true
            }
            commandLine = listOf("git", *args)
            if (OperatingSystem.current().isWindows) {
                commandLine = listOf("cmd.exe", "/d", "/c") + commandLine
            }
        }
        return execOutput.result.zip(execOutput.standardOutput.asText) { result, outputText ->
            if (result.exitValue == 0) outputText.trim()
            else "<unknown>" // It's a source distribution, we don't know.
        }
    }
}
