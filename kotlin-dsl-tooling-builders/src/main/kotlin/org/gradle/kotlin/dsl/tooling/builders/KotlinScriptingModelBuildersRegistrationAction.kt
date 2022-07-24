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
package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.internal.project.ProjectInternal

import org.gradle.configuration.project.ProjectConfigureAction

import org.gradle.kotlin.dsl.support.serviceOf

import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry


class KotlinScriptingModelBuildersRegistrationAction : ProjectConfigureAction {

    override fun execute(project: ProjectInternal) {

        val builders = project.serviceOf<ToolingModelBuilderRegistry>()
        builders.register(KotlinBuildScriptModelBuilder)
        builders.register(KotlinBuildScriptTemplateModelBuilder)

        if (project.parent == null) {
            builders.register(KotlinDslScriptsModelBuilder)
            project.tasks.register(KotlinDslModelsParameters.PREPARATION_TASK_NAME)
        }
    }
}
