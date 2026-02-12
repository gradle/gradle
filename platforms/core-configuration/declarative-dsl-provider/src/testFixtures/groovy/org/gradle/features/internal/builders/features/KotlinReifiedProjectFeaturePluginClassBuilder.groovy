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
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionClassBuilder

/**
 * A {@link ProjectFeaturePluginClassBuilder} for creating a project feature plugin implemented in Kotlin that binds using reified type parameters
 * rather than binding with explicit class references.
 */
class KotlinReifiedProjectFeaturePluginClassBuilder extends KotlinProjectFeaturePluginClassBuilder {
    KotlinReifiedProjectFeaturePluginClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
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
            import org.gradle.features.dsl.bindProjectFeature

            @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding::class)
            class ${projectFeaturePluginClassName} : Plugin<Project> {

                class Binding : ${ProjectFeatureBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                        builder.bindProjectFeature<
                            ${definition.publicTypeClassName},
                            ${bindingTypeClassName},
                            ${definition.buildModelFullPublicClassName}
                        >("${name}") { definition, model, parent  ->
                            val services = objectFactory.newInstance(Services::class.java)
                            println("Binding ${definition.publicTypeClassName}")
                            println("${name} model class: " + model::class.java.getSimpleName())
                            println("${name} parent model class: " + getBuildModel(parent)::class.java.getSimpleName())
                            ${convertToKotlin(definition.buildModelMapping)}
                            services.taskRegistrar.register("print${definition.publicTypeClassName}Configuration") { task: Task ->
                                task.doLast { _: Task ->
                                    ${definition.displayDefinitionPropertyValues().replaceAll(';', '')}
                                    ${definition.displayModelPropertyValues().replaceAll(';', '')}
                                }
                            }
                        }
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBuildModelImplementationType()}
                        ${maybeDeclareBindingModifiers()}
                    }

                    ${servicesInterface}
                }

                override fun apply(project: Project) {
                }
            }
        """
        return content
    }
}
