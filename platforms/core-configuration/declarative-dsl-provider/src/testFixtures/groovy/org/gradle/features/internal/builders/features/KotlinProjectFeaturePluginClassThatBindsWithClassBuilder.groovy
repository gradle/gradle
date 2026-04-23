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

package org.gradle.features.internal.builders.features

import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionClassBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * A {@link ProjectFeaturePluginClassBuilder} for creating a project feature plugin implemented in Kotlin that binds using a class
 * rather than a lambda.
 */
class KotlinProjectFeaturePluginClassThatBindsWithClassBuilder extends KotlinProjectFeaturePluginClassBuilder {
    KotlinProjectFeaturePluginClassThatBindsWithClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
        super(definition)
    }

    @Override
    protected String getClassContent() {
        String content = """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.Task
            import ${BindsProjectFeature.class.name}
            import ${ProjectFeatureBindingBuilder.class.name}
            import ${ProjectFeatureBinding.class.name}
            import ${ProjectFeatureApplyAction.class.name}
            import org.gradle.features.dsl.bindProjectFeature

            @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding::class)
            class ${projectFeaturePluginClassName} : Plugin<Project> {

                class Binding : ${ProjectFeatureBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                        builder.bindProjectFeature("${name}", ApplyAction::class)
                    }
                    ${maybeDeclareDefinitionImplementationType()}
                    ${maybeDeclareBuildModelImplementationType()}
                    ${maybeDeclareBindingModifiers()}
                }

                abstract class ApplyAction : ${ProjectFeatureApplyAction.class.simpleName}<${definition.publicTypeClassName}, ${definition.buildModelFullPublicClassName}, ${bindingTypeClassName}> {
                    @javax.inject.Inject
                    constructor()

                    ${injectedServices}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${definition.publicTypeClassName}, model: ${definition.buildModelFullPublicClassName}, parent: ${bindingTypeClassName}) {
                        println("Binding ${definition.publicTypeClassName}")
                        println("${name} model class: " + model::class.java.getSimpleName())
                        println("${name} parent model class: " + context.getBuildModel(parent)::class.java.getSimpleName())
                        ${convertToKotlin(definition.buildModelMapping)}
                        taskRegistrar.register("print${definition.publicTypeClassName}Configuration") { task: Task ->
                            task.doLast { _: Task ->
                                ${convertToKotlin(definition.displayDefinitionPropertyValues())}
                                ${convertToKotlin(definition.displayModelPropertyValues())}
                            }
                        }
                    }
                }

                override fun apply(project: Project) {
                }
            }
        """
        return content
    }

    String getInjectedServices() {
        return """
            @get:javax.inject.Inject
            abstract val projectFeatureLayout: ${ProjectFeatureLayout.class.name}

            @get:javax.inject.Inject
            abstract val taskRegistrar: ${TaskRegistrar.class.name}
        """
    }
}
