/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configuration.project.ProjectConfigureAction
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinDslScriptsModelBuilder
import org.gradle.kotlin.dsl.tooling.builders.KotlinBuildScriptModelBuilder
import org.gradle.kotlin.dsl.tooling.builders.KotlinDslScriptsModelBuilder
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderRegistrar


class KotlinScriptingModelBuildersRegistrationAction : ProjectConfigureAction, ToolingModelBuilderRegistrar {

    override fun execute(project: ProjectInternal) {
        if (project.isRootProject()) {
            project.tasks.register(KotlinDslModelsParameters.PREPARATION_TASK_NAME)
        }
    }

    override fun registerForProject(project: ProjectInternal, registry: ToolingModelBuilderRegistry) {
        registry.register(KotlinBuildScriptModelBuilder)
        registry.register(IsolatedScriptsModelBuilder)

        if (project.isRootProject()) {
            val builder = getBuilder(project)
            registry.register(builder)
        }
    }

    private fun getBuilder(project: ProjectInternal): AbstractKotlinDslScriptsModelBuilder {
        val modelParameters = project.serviceOf<BuildModelParameters>()
        val isolatedProjects = modelParameters.isIsolatedProjects
        return when {
            isolatedProjects -> {
                val intermediateModelProvider = project.serviceOf<IntermediateToolingModelProvider>()
                IsolatedProjectsSafeKotlinDslScriptsModelBuilder(intermediateModelProvider)
            }
            else -> KotlinDslScriptsModelBuilder
        }
    }

    private fun ProjectInternal.isRootProject(): Boolean =
        parent == null
}
