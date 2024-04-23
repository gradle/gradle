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

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

abstract class BuildEnvironmentExtension {
    abstract val gitCommitId: Property<String>
    abstract val gitBranch: Property<String>
    abstract val repoRoot: DirectoryProperty
}

@Suppress("UnstableApiUsage")
fun Project.git(vararg args: String): Provider<String> {
    val projectDir = layout.projectDirectory.asFile
    val execOutput = providers.exec {
        workingDir = projectDir
        isIgnoreExitValue = true
        commandLine = listOf("git", *args)
        if (OperatingSystem.current().isWindows) {
            commandLine = listOf("cmd", "/c") + commandLine
        }
    }
    return execOutput.result.zip(execOutput.standardOutput.asText) { result, outputText ->
        if (result.exitValue == 0) outputText.trim()
        else "<unknown>" // It's a source distribution, we don't know.
    }
}
