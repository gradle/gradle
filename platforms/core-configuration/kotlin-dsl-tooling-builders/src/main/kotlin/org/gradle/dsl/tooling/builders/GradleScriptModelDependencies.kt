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
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.invocation.Gradle
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.kotlin.dsl.*
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import java.io.File
import java.util.Properties

internal
fun classpathDependencyArtifactsOf(buildscript: ScriptHandler): ArtifactCollection =
    buildscript
        .configurations[CLASSPATH_CONFIGURATION]
        .incoming
        .artifactView { it.lenient(true) }
        .artifacts

@ServiceScope(Scope.Build::class)
internal class GradleScriptModelDependencies(
    private val gradle: Gradle,
    private val install: CurrentGradleInstallation
) {

    fun buildSourcePathFor(
        scriptFile: File,
        classPathFile: File,
        resolvedClasspath: Set<ResolvedArtifactResult>
    ): List<ScriptComponentSourceIdentifier> {
        val externalId = resolvedClasspath.firstOrNull { it.file == classPathFile }?.id?.componentIdentifier
        return if (externalId != null) {
            // External dependency
            listOf(
                newScriptComponentSourceIdentifier(
                    displayName = externalId.displayName,
                    scriptFile = scriptFile,
                    identifier = ScriptComponentSourceIdentifierType.ExternalDependency(externalId)
                )
            )
        } else if (install.isGradleModule(classPathFile)) {
            // Gradle sources
            listOf(
                newScriptComponentSourceIdentifier(
                    displayName = "Gradle ${gradle.gradleVersion}",
                    scriptFile = scriptFile,
                    identifier = ScriptComponentSourceIdentifierType.GradleSrc
                )
            )
        } else if (install.isGradleDistroLib(classPathFile)) {
            // External dependency matching Gradle distribution library
            install.componentIdOrNull(classPathFile)?.let { externalId ->
                listOf(
                    newScriptComponentSourceIdentifier(
                        displayName = externalId.displayName,
                        scriptFile = scriptFile,
                        identifier = ScriptComponentSourceIdentifierType.ExternalDependency(externalId)
                    )
                )
            } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun CurrentGradleInstallation.isGradleModule(file: File): Boolean =
        file.parentFile.name == "generated-gradle-jars" || installation?.libDirs?.any { libDir ->
            file.name.startsWith("gradle-") && file.absolutePath.startsWith(libDir.absolutePath)
        } == true

    private fun CurrentGradleInstallation.isGradleDistroLib(file: File): Boolean =
        installation?.libDirs?.any { libDir -> file.absolutePath.startsWith(libDir.absolutePath) } == true

    private fun CurrentGradleInstallation.componentIdOrNull(classPathFile: File): ComponentIdentifier? =
        installation?.libDirs
            ?.mapNotNull { libDir ->
                val moduleName = distroLibModuleNameOf(classPathFile)
                if (libDir.resolve(classPathFile.name) == classPathFile) libDir.resolve("$moduleName.properties").takeIf { it.exists() }
                else null
            }
            ?.singleOrNull()?.let { componentIdFromModulePropertiesOrNull(it) }

    private val distroLibModuleNameRegex = Regex("(.*)-\\d.*")
    private fun distroLibModuleNameOf(file: File): String? =
        distroLibModuleNameRegex.find(file.nameWithoutExtension)?.groupValues?.getOrNull(1)

    private fun componentIdFromModulePropertiesOrNull(modulePropertiesFile: File): ComponentIdentifier? =
        try {
            Properties().let { props ->
                modulePropertiesFile.inputStream().use { input ->
                    props.load(input)
                    DefaultModuleComponentIdentifier(
                        DefaultModuleIdentifier.newId(props.getProperty("alias.group"), props.getProperty("alias.name")),
                        props.getProperty("alias.version")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace(System.err)
            null
        }
}
