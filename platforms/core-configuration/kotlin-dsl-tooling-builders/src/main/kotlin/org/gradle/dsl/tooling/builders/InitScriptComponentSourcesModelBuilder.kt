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
import org.gradle.tooling.model.buildscript.InitScriptComponentSources
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.io.File

object InitScriptComponentSourcesModelBuilder : ParameterizedToolingModelBuilder<ScriptComponentSourcesRequest> {
    override fun canBuild(modelName: String): Boolean =
        InitScriptComponentSources::class.java.name.equals(modelName)

    override fun getParameterType(): Class<ScriptComponentSourcesRequest> =
        ScriptComponentSourcesRequest::class.java

    override fun buildAll(modelName: String, project: Project): InitScriptComponentSources =
        error("Should not be called")

    override fun buildAll(modelName: String, parameter: ScriptComponentSourcesRequest, project: Project): InitScriptComponentSources {
        val identifiers = parameter.deserializeIdentifiers()
        val gradle = project.gradle as GradleInternal
        val initScripts: List<File> = gradle.startParameter.allInitScripts
        require(identifiers.keys.all { it in initScripts }) {
            "Unexpected requested source identifiers (Only $initScripts were expected): $parameter"
        }
        // TODO Use the right ScriptHandler
        val results = gradle.settings.buildscript.dependencies.downloadSources(identifiers.filterKeys { initScripts.contains(it) })
        return StandardScriptComponentSources(results)
    }
}
