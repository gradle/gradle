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
import org.gradle.api.internal.GradleInternal
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.model.buildscript.InitScriptComponentSources
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder

object InitScriptComponentSourcesModelBuilder : ParameterizedToolingModelBuilder<ScriptComponentSourcesRequest> {

    override fun canBuild(modelName: String): Boolean =
        InitScriptComponentSources::class.java.name.equals(modelName)

    override fun getParameterType(): Class<ScriptComponentSourcesRequest> =
        ScriptComponentSourcesRequest::class.java

    override fun buildAll(modelName: String, project: Project): InitScriptComponentSources =
        error("Building model ${InitScriptComponentSources::class.simpleName} requires a parameter of type ${ScriptComponentSourcesRequest::class.simpleName}")

    override fun buildAll(modelName: String, parameter: ScriptComponentSourcesRequest, project: Project): InitScriptComponentSources {
        val gradle = project.gradle as GradleInternal
        val sources = gradle.serviceOf<GradleScriptModelSources>()
        return StandardScriptComponentSources(
            sources.downloadSources(
                parameter.internalIdentifiers,
                // TODO Use the right ScriptHandler
                gradle.settings.buildscript.dependencies
            )
        )
    }
}
