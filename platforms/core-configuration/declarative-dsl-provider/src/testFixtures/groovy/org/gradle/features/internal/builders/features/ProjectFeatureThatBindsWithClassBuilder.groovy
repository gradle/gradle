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

import org.gradle.api.provider.ProviderFactory
import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionClassBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * A {@link ProjectFeaturePluginClassBuilder} that uses a {@ProjectFeatureApplyAction} class to apply the feature plugin logic
 * instead of a lambda.
 */
class ProjectFeatureThatBindsWithClassBuilder extends ProjectFeaturePluginClassBuilder {
    ProjectFeatureThatBindsWithClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
        super(definition)
    }

    @Override
    protected String getClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectFeature.class.name};
            import ${ProjectFeatureBindingBuilder.class.name};
            import static ${ProjectFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
            import ${ProjectFeatureBinding.class.name};
            import ${ProjectFeatureApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};

            @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding.class)
            public class ${projectFeaturePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        builder.${bindingMethodName}(
                            "${name}",
                            ${definition.publicTypeClassName}.class,
                            ${bindingTypeClassName}.class,
                            ${projectFeaturePluginClassName}.ApplyAction.class
                        )
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBuildModelImplementationType()}
                        ${maybeDeclareBindingModifiers()};
                    }
                }

                static abstract class ApplyAction implements ${ProjectFeatureApplyAction.class.name}<${definition.publicTypeClassName}, ${definition.getBuildModelFullPublicClassName()}, ${bindingTypeClassName}> {
                    @javax.inject.Inject
                    public ApplyAction() { }

                    ${servicesInjection}

                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${definition.publicTypeClassName} definition, ${definition.getBuildModelFullPublicClassName()} model, ${bindingTypeClassName} parent) {
                        System.out.println("Binding ${definition.publicTypeClassName}");
                        System.out.println("${name} model class: " + model.getClass().getSimpleName());
                        System.out.println("${name} parent model class: " + context.getBuildModel(parent).getClass().getSimpleName());

                        ${definition.buildModelMapping}

                        getTaskRegistrar().register("print${definition.publicTypeClassName}Configuration", task -> {
                            task.doLast(t -> {
                                ${definition.displayDefinitionPropertyValues()}
                                ${definition.displayModelPropertyValues()}
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

    String getServicesInjection() {
        return """
            @javax.inject.Inject
            abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();

            @javax.inject.Inject
            abstract protected ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

            @javax.inject.Inject
            abstract protected ${ProviderFactory.class.name} getProviderFactory();
        """
    }
}
