/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.dsl.tooling.builders

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.invocation.Gradle
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import java.io.File
import java.util.Properties

internal fun Gradle.buildSourcePathFor(
    scriptFile: File,
    compileClassPathFile: File,
    resolvedClasspath: Set<ResolvedArtifactResult>
): List<ScriptComponentSourceIdentifier> {
    val externalId = resolvedClasspath.firstOrNull { it.file == compileClassPathFile }?.id?.componentIdentifier
    return if (externalId != null) {
        // Resolved external dependency
        listOf(
            newScriptComponentSourceIdentifier(
                displayName = externalId.displayName,
                scriptFile = scriptFile,
                identifier = SourceComponentIdentifierType.ExternalDependency(externalId)
            )
        )
    } else if (isGradleModule(compileClassPathFile)) {
        // Gradle sources
        listOf(
            newScriptComponentSourceIdentifier(
                displayName = "Gradle ${gradle.gradleVersion}",
                scriptFile = scriptFile,
                identifier = SourceComponentIdentifierType.GradleSrc
            )
        )
    } else if (isGradleDistroLib(compileClassPathFile)) {
        val moduleName = distroLibModuleNameOf(compileClassPathFile)
        serviceOf<CurrentGradleInstallation>().installation?.libDirs
            ?.mapNotNull { libDir ->
                if (libDir.resolve(compileClassPathFile.name) == compileClassPathFile) libDir.resolve("$moduleName.properties").takeIf { it.exists() }
                else null
            }
            ?.singleOrNull()
            ?.let { modulePropertiesFile ->
                try {
                    val moduleProperties = Properties().apply { modulePropertiesFile.inputStream().use { load(it) } }
                    val group = moduleProperties.getProperty("alias.group")
                    val name = moduleProperties.getProperty("alias.name")
                    val version = moduleProperties.getProperty("alias.version")
                    val externalId = DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), version)
                    listOf(
                        newScriptComponentSourceIdentifier(
                            displayName = externalId.displayName,
                            scriptFile = scriptFile,
                            identifier = SourceComponentIdentifierType.ExternalDependency(externalId)
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace(System.err)
                    emptyList()
                }
            }
            ?: emptyList()
    } else {
        emptyList()
    }
}

private fun Gradle.isGradleModule(file: File): Boolean {
    return file.parentFile.name == "generated-gradle-jars" || serviceOf<CurrentGradleInstallation>().installation?.libDirs?.any { libDir ->
        file.name.startsWith("gradle-") && file.absolutePath.startsWith(libDir.absolutePath)
    } == true
}

private fun Gradle.isGradleDistroLib(file: File): Boolean {
    return serviceOf<CurrentGradleInstallation>().installation?.libDirs?.any { libDir ->
        file.absolutePath.startsWith(libDir.absolutePath)
    } == true
}

private val distroLibModuleNameRegex = Regex("(.*)-\\d.*")
private fun distroLibModuleNameOf(file: File): String? {
    return distroLibModuleNameRegex.find(file.nameWithoutExtension)?.groupValues?.getOrNull(1)
}

internal
fun classpathDependencyArtifactsOf(buildscript: ScriptHandler): ArtifactCollection =
    buildscript
        .configurations[CLASSPATH_CONFIGURATION]
        .incoming
        .artifactView { it.lenient(true) }
        .artifacts
