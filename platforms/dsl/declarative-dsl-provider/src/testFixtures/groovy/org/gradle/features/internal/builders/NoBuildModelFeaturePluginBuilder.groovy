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

import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * Generates a {@code @BindsProjectFeature}-annotated plugin whose feature uses
 * {@code BuildModel.None} (i.e., no build model). The Kotlin rendering is the reified-style
 * variant since that is the only shape the framework supports for a no-build-model feature.
 */
class NoBuildModelFeaturePluginBuilder extends AbstractFeaturePluginBuilder {

    @Override
    protected String renderJava() {
        def modifiers = maybeDeclareBindingModifiers()

        return """
            package ${packageName};

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectFeature.class.name};
            import ${ProjectFeatureBindingBuilder.class.name};
            import static ${ProjectFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
            import ${ProjectFeatureBinding.class.name};
            import ${ProjectFeatureApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import org.gradle.features.binding.BuildModel;

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding.class)
            public class ${pluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        builder.${bindingMethodName}(
                            "${name}",
                            ${primaryDefinition.className}.class,
                            ${bindingTypeClassName}.class,
                            ${pluginClassName}.ApplyAction.class
                        )${modifiers};
                    }
                }

                static abstract class ApplyAction implements ${ProjectFeatureApplyAction.class.name}<${primaryDefinition.className}, BuildModel.None, ${bindingTypeClassName}> {
                    @javax.inject.Inject public ApplyAction() { }

                    ${generateJavaFeatureApplyActionServices()}

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${primaryDefinition.className} definition, BuildModel.None model, ${bindingTypeClassName} parent) {
                        System.out.println("Binding ${primaryDefinition.className}");
                        System.out.println("${name} model class: " + model.getClass().getSimpleName());

                        ${customApplyActionCode}

                        getTaskRegistrar().register("print${primaryDefinition.className}Configuration", task -> {
                            task.doLast(t -> {
                                ${displayDefinitionValuesForLanguage()}
                            });
                        });
                    }
                }

                @Override
                public void apply(Project project) {
                }
            }
        """
    }

    @Override
    protected String renderKotlin() {
        def modifiers = maybeDeclareBindingModifiers()

        return """
            package ${packageName}

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import ${BindsProjectFeature.class.name}
            import ${ProjectFeatureBindingBuilder.class.name}
            import ${ProjectFeatureBinding.class.name}
            import ${ProjectFeatureApplyAction.class.name}
            import ${ProjectFeatureApplicationContext.class.name}
            import ${BuildModel.class.name}
            import org.gradle.features.dsl.bindProjectFeature
            import javax.inject.Inject

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding::class)
            class ${pluginClassName} : Plugin<Project> {

                class Binding : ${ProjectFeatureBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                        builder.bindProjectFeature("${name}", ${pluginClassName}.ApplyAction::class)${modifiers}
                    }
                }

                abstract class ApplyAction @Inject constructor() : ${ProjectFeatureApplyAction.class.simpleName}<${primaryDefinition.className}, BuildModel.None, ${bindingTypeClassName}> {

                    @get:Inject
                    abstract val taskRegistrar: ${TaskRegistrar.class.name}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${primaryDefinition.className}, model: BuildModel.None, parent: ${bindingTypeClassName}) {
                        println("Binding ${primaryDefinition.className}")
                        println("${name} model class: " + model::class.simpleName)

                        taskRegistrar.register("print${primaryDefinition.className}Configuration") { task ->
                            task.doLast { _ ->
                                ${displayDefinitionValuesForLanguage()}
                            }
                        }
                    }
                }

                override fun apply(project: Project) {
                }
            }
        """
    }
}
