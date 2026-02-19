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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.internal.build.BuildState
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.model.buildscript.SettingsScriptModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.File
import java.io.Serializable

object SettingsScriptModelBuilder : BuildScopeModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        SettingsScriptModel::class.java.name == modelName

    override fun create(target: BuildState): SettingsScriptModel {
        target.ensureProjectsLoaded()
        val gradle = target.mutableModel
        val settings = gradle.settings
        val scriptFile = File(settings.settingsScript.fileName)

        return StandardSettingsScriptModel(
            StandardGradleScriptModel(
                scriptFile = scriptFile,
                implicitImports = gradle.scriptImplicitImports,
                contextPath = buildContextPathFor(scriptFile, gradle, settings),
            )
        )
    }

    private fun buildContextPathFor(
        scriptFile: File,
        gradle: GradleInternal,
        settings: SettingsInternal
    ): List<ScriptContextPathElement> =
        buildList {
            val baseScope = settings.classLoaderScope
            val compilationClassPath = gradle.compilationClassPathOf(baseScope).asFiles
            val scriptSource = textResourceScriptSource("settings script", scriptFile, gradle.serviceOf())
            val scriptScope = baseScope.createChild("model-${scriptFile.toURI()}", null)
            val scriptHandler = scriptHandlerFactoryOf(gradle).create(scriptSource, scriptScope, StandaloneDomainObjectContext.forScript(scriptSource))
            val resolvedClassPath = classpathDependencyArtifactsOf(scriptHandler)

            compilationClassPath.forEach { file ->
                add(
                    StandardScriptContextPathElement(
                        file,
                        resolvedClassPath.firstOrNull { it.file == file }
                            ?.id?.componentIdentifier
                            ?.let { componentId ->
                                listOf(
                                    StandardScriptComponentSourceIdentifier(
                                        displayName = componentId.displayName,
                                        bytes = serialize(componentId)
                                    )
                                )
                            } ?: emptyList()
                    )
                )
            }
        }
}

data class StandardSettingsScriptModel(
    private val settingsScriptModel: GradleScriptModel
) : SettingsScriptModel, Serializable {
    override fun getSettingsScriptModel(): GradleScriptModel = settingsScriptModel
}
