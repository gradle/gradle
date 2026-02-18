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

import org.gradle.internal.build.BuildState
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.InitScriptsModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import java.io.File

object InitScriptsModelBuilder : BuildScopeModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        InitScriptsModel::class.java.name == modelName

    override fun create(target: BuildState): InitScriptsModel {
        val gradle = target.mutableModel
        val initScripts: List<File> = gradle.startParameter.allInitScripts
            // TODO remove KTS filtering
            .filter { it.isKotlinDslFile }
        return StandardInitScriptsModel(
            initScripts.map { scriptFile ->

                val (scriptHandler, scriptClassPath) = compilationClassPathForScriptPluginOf(
                    target = gradle,
                    scriptFile = scriptFile,
                    baseScope = gradle.classLoaderScope,
                    scriptHandlerFactory = scriptHandlerFactoryOf(gradle),
                    gradle = gradle,
                    resourceDescription = "initialization script"
                )

                val ktsModel = KotlinScriptTargetModelBuilder(
                    scriptFile = scriptFile,
                    project = null,
                    gradle = gradle,
                    scriptClassPath = scriptClassPath,
                    sourceLookupScriptHandlers = listOf(scriptHandler)
                ).buildModel()

                StandardGradleScriptModel(
                    scriptFile = scriptFile,
                    implicitImports = ktsModel.implicitImports,
                    contextPath = emptyList()
                )
            }.associateBy { it.scriptFile }
        )
    }
}

class StandardInitScriptsModel(
    private val initScriptModels: Map<File, GradleScriptModel>
) : InitScriptsModel {
    override fun getInitScriptModels(): Map<File, GradleScriptModel> = initScriptModels
}
