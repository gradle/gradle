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

package org.gradle.features.internal.builders

import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectTypeApplyAction
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * Generates a {@code @BindsProjectType}-annotated plugin with a single project-type binding,
 * using the Kotlin reified-type-parameter binding form (Java rendering is identical to
 * {@link SingleTypePluginBuilder}).
 */
class ReifiedSingleTypePluginBuilder extends SingleTypePluginBuilder {

    @Override
    protected String renderKotlin() {
        def modifiers = maybeDeclareBindingModifiers()
        def implType = primaryDefinition.implementationClassName
            ? ".withUnsafeDefinitionImplementationType(${primaryDefinition.implementationClassName}::class.java)"
            : ""

        return """
            package ${packageName}

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
            import org.gradle.features.dsl.bindProjectType
            import javax.inject.Inject

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding::class)
            class ${pluginClassName} : Plugin<Project> {

                class Binding : ${ProjectTypeBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectTypeBindingBuilder.class.simpleName}) {
                        builder.bindProjectType("${name}", ${pluginClassName}.ApplyAction::class)
                        ${implType}${modifiers}
                    }
                }

                abstract class ApplyAction @Inject constructor() : ${ProjectTypeApplyAction.class.simpleName}<${primaryDefinition.className}, ${primaryDefinition.fullyQualifiedBuildModelClassName}> {

                    @get:javax.inject.Inject
                    abstract val taskRegistrar: ${TaskRegistrar.class.name}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${primaryDefinition.className}, model: ${primaryDefinition.fullyQualifiedBuildModelClassName}) {
                        println("Binding " + ${primaryDefinition.className}::class.simpleName)

                        ${buildModelMappingForLanguage()}

                        taskRegistrar.register("print${primaryDefinition.className}Configuration") { task: Task ->
                            task.doLast { _: Task ->
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
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
}
