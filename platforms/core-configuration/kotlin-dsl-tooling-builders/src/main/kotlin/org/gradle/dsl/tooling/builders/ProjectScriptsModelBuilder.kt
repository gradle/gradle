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

import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.api.invocation.Gradle
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.builders.scriptCompilationClassPath
import org.gradle.kotlin.dsl.tooling.builders.scriptImplicitImports
import org.gradle.kotlin.dsl.tooling.builders.sourceLookupScriptHandlersFor
import org.gradle.tooling.model.buildscript.ProjectScriptsModel
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File

object ProjectScriptsModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        ProjectScriptsModel::class.java.name == modelName

    override fun buildAll(modelName: String, project: Project): ProjectScriptsModel {
        return StandardProjectScriptsModel(
            buildScriptModel = StandardGradleScriptModel(
                scriptFile = project.buildFile,
                implicitImports = project.gradle.scriptImplicitImports,
                contextPath = buildContextPathFor(project.buildFile, project),
            ),
            precompiledScriptModels = emptyList()
        )
    }

    private fun buildContextPathFor(scriptFile: File, project: Project): List<ScriptContextPathElement> =
        buildList {
            val compilationClassPath = project.scriptCompilationClassPath.asFiles

            val resolvedClassPath: MutableSet<ResolvedArtifactResult> = hashSetOf()
            for (buildscript in sourceLookupScriptHandlersFor(project).asReversed()) {
                resolvedClassPath += classpathDependencyArtifactsOf(buildscript)
                    .filter { dep -> dep.id !in resolvedClassPath.map { it.id } }
            }

            compilationClassPath.forEach { file ->
                add(
                    StandardScriptContextPathElement(
                        classPath = file,
                        sourcePath = project.gradle.buildSourcePathFor(scriptFile, file, resolvedClassPath)
                    )
                )
            }
        }
}


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
        emptyList()
    } else {
        emptyList()
    }
}

internal fun Gradle.isGradleModule(file: File): Boolean {
    return file.parentFile.name == "generated-gradle-jars" || serviceOf<CurrentGradleInstallation>().installation?.libDirs?.any { libDir ->
        file.name.startsWith("gradle-") && file.absolutePath.startsWith(libDir.absolutePath)
    } == true
}

internal fun Gradle.isGradleDistroLib(file: File): Boolean {
    return false
}

internal
fun classpathDependencyArtifactsOf(buildscript: ScriptHandler): ArtifactCollection =
    buildscript
        .configurations[CLASSPATH_CONFIGURATION]
        .incoming
        .artifactView { it.lenient(true) }
        .artifacts

