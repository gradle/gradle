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

package org.gradle.features.internal.builders.types

import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectTypeApplyAction
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionClassBuilder
import org.gradle.features.registration.TaskRegistrar
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * A {@link ProjectTypePluginClassBuilder} for generating project type plugin class implemented in Kotlin that binds using an
 * apply action class instead of a lambda.
 */
class KotlinProjectTypeThatBindsWithClassBuilder extends ProjectTypePluginClassBuilder {
    KotlinProjectTypeThatBindsWithClassBuilder(ProjectTypeDefinitionClassBuilder definition) {
        super(definition)
    }

    @Override
    void build(PluginBuilder pluginBuilder) {
        pluginBuilder.file("src/main/kotlin/org/gradle/test/${projectTypePluginClassName}.kt") << getClassContent()
    }

    @Override
    protected String getClassContent() {
        return """
            package org.gradle.test

            import org.gradle.api.Task
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Nested
            import ${ProjectTypeBinding.class.name}
            import ${BindsProjectType.class.name}
            import ${ProjectTypeBindingBuilder.class.name}
            import ${ProjectTypeApplyAction.class.name}
            import javax.inject.Inject
            import org.gradle.features.dsl.bindProjectType

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding::class)
            class ${projectTypePluginClassName} : Plugin<Project> {

                class Binding : ${ProjectTypeBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectTypeBindingBuilder.class.simpleName}) {
                        builder.bindProjectType("${name}", ApplyAction::class)
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBindingModifiers()}
                    }
                }

                abstract class ApplyAction : ${ProjectTypeApplyAction.class.simpleName}<${definition.publicTypeClassName}, ${definition.fullyQualifiedBuildModelClassName}> {
                    @javax.inject.Inject
                    constructor()

                    ${injectedServices}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${definition.publicTypeClassName}, model: ${definition.fullyQualifiedBuildModelClassName}) {
                        println("Binding " + ${definition.publicTypeClassName}::class.simpleName)
                        ${conventions == null ? "" : convertToKotlin(conventions)}

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
                    println("Applying " + this::class.java.simpleName)
                }
            }
        """
    }

    String getInjectedServices() {
        return """
            @get:javax.inject.Inject
            abstract val taskRegistrar: ${TaskRegistrar.class.name}
        """
    }

    String convertToKotlin(String content) {
        return content.replaceAll(';', '')
    }
}
