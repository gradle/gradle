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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.internal.build.BuildState
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.builders.compilationClassPathOf
import org.gradle.kotlin.dsl.tooling.builders.scriptHandlerFactoryOf
import org.gradle.kotlin.dsl.tooling.builders.scriptImplicitImports
import org.gradle.kotlin.dsl.tooling.builders.textResourceScriptSource
import org.gradle.tooling.model.buildscript.InitScriptsModel
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.File

object InitScriptsModelBuilder : BuildScopeModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        InitScriptsModel::class.java.name == modelName

    override fun create(target: BuildState): InitScriptsModel {
        val gradle = target.mutableModel
        val initScripts: List<File> = gradle.startParameter.allInitScripts
        return StandardInitScriptsModel(
            initScripts.map { scriptFile ->
                StandardGradleScriptModel(
                    scriptFile = scriptFile,
                    implicitImports = gradle.scriptImplicitImports,
                    contextPath = buildContextPathFor(scriptFile, gradle),
                )
            }
        )
    }

    private fun buildContextPathFor(scriptFile: File, gradle: GradleInternal): List<ScriptContextPathElement> =
        buildList {
            val baseScope = gradle.classLoaderScope
            val compilationClassPath = gradle.compilationClassPathOf(baseScope).asFiles
            val scriptSource = textResourceScriptSource("initialization script", scriptFile, gradle.serviceOf())
            val scriptScope = baseScope.createChild("model-${scriptFile.toURI()}", null)
            val scriptHandler = scriptHandlerFactoryOf(gradle).create(scriptSource, scriptScope, StandaloneDomainObjectContext.forScript(scriptSource))
            val resolvedClassPath = classpathDependencyArtifactsOf(scriptHandler)

            val dependencies = gradle.serviceOf<GradleScriptModelDependencies>()
            compilationClassPath.forEach { file ->
                add(
                    StandardScriptContextPathElement(
                        file,
                        dependencies.buildSourcePathFor(scriptFile, file, resolvedClassPath.artifacts)
                    )
                )
            }
        }
}
