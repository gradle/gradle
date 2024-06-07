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

package gradlebuild.basics

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject


abstract class BuildEnvironmentService : BuildService<BuildEnvironmentService.Parameters> {

    interface Parameters : BuildServiceParameters {
        val rootProjectDir: DirectoryProperty
        val rootProjectBuildDir: DirectoryProperty
        val artifactoryUserName: Property<String>
        val artifactoryPassword: Property<String>
        val testVersions: Property<String>
        val integtestAgentAllowed: Property<String>
        val integtestDebug: Property<String>
        val integtestLauncherDebug: Property<String>
        val integtestVerbose: Property<String>
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
            isIgnoreExitValue = true
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
