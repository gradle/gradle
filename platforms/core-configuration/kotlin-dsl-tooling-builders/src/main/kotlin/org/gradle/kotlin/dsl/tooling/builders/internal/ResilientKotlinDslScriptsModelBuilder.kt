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

import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinDslScriptsModelBuilder
import org.gradle.tooling.Failure
import org.gradle.tooling.internal.consumer.DefaultFailure
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.tooling.model.kotlin.dsl.ResilientKotlinDslScriptsModel
import org.gradle.tooling.provider.model.ToolingModelBuilder

internal
class ResilientKotlinDslScriptsModelBuilder(val delegate: AbstractKotlinDslScriptsModelBuilder) : ToolingModelBuilder {

    override fun buildAll(modelName: String, project: Project): Any? {
        val buildState = (project.gradle as GradleInternal).owner
        var exception = null as Throwable?
        try {
            // TODO: Is there a better way to get the exception from the build state if it exists?
            buildState.ensureProjectsLoaded()
        } catch (e: Exception) {
            exception = e
        }
        val model = delegate.buildAll(modelName, project)
        return object : ResilientKotlinDslScriptsModel {
            override fun getModel(): KotlinDslScriptsModel = model
            override fun getFailure(): Failure? = if (exception == null) { exception } else { DefaultFailure.fromThrowable(exception) }
        }
    }

    override fun canBuild(modelName: String): Boolean {
        return modelName == "org.gradle.tooling.model.kotlin.dsl.ResilientKotlinDslScriptsModel"
    }
}
