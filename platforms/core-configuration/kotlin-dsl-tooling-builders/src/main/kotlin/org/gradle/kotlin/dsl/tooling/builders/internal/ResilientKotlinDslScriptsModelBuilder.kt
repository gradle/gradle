/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders.internal

import org.gradle.internal.build.BuildState
import org.gradle.internal.resiliency.ResilientSyncListener
import org.gradle.kotlin.dsl.tooling.builders.StandardKotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder

internal
class ResilientKotlinDslScriptsModelBuilder() : BuildScopeModelBuilder {

    override fun create(target: BuildState): KotlinDslScriptsModel {
        try {
            target.ensureProjectsLoaded()
        } catch (e: Exception) {
            val syncListener = target.mutableModel.services.get(ResilientSyncListener::class.java)
            val map = syncListener.classpaths.map {
                it.key to StandardKotlinDslScriptModel(
                    it.value,
                    sourcePath = emptyList(),
                    implicitImports = emptyList(),
                    editorReports = emptyList(),
                    exceptions = emptyList(),
                )
            }.toMap()
            return KotlinDslScriptsModel { map }
        }
        try {
            target.ensureProjectsConfigured()
        } catch (e: Exception) {
            val syncListener = target.mutableModel.services.get(ResilientSyncListener::class.java)
            val map = syncListener.classpaths.map {
                it.key to StandardKotlinDslScriptModel(
                    it.value,
                    sourcePath = emptyList(),
                    implicitImports = emptyList(),
                    editorReports = emptyList(),
                    exceptions = emptyList(),
                )
            }.toMap()
            return KotlinDslScriptsModel { map }
        }
        return KotlinDslScriptsModel { mapOf() }
    }

    override fun canBuild(modelName: String): Boolean {
        return modelName == "org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel"
    }
}
