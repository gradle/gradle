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

class ProjectFeaturePluginThatBindsMultipleFeaturesToTheSameName extends ProjectFeaturePluginClassBuilder {
    private final String anotherBindingTypeClassName

    ProjectFeaturePluginThatBindsMultipleFeaturesToTheSameName(ProjectFeatureDefinitionClassBuilder definition, String anotherBindingTypeClassName) {
        super(definition)
        this.anotherBindingTypeClassName = anotherBindingTypeClassName
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

            @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding.class)
            public class ${projectFeaturePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        builder.${bindingMethodName}(
                            "${name}",
                            ${definition.publicTypeClassName}.class,
                            ${bindingTypeClassName}.class,
                            (context, definition, model, parent) -> {
                                Services services = context.getObjectFactory().newInstance(Services.class);
                                System.out.println("Binding ${definition.publicTypeClassName}");
                                System.out.println("${name} model class: " + model.getClass().getSimpleName());
                                System.out.println("${name} parent model class: " + context.getBuildModel(parent).getClass().getSimpleName());

                                ${definition.buildModelMapping}

                                services.getTaskRegistrar().register("print${definition.publicTypeClassName}1Configuration", task -> {
                                    task.doLast(t -> {
                                        ${definition.displayDefinitionPropertyValues()}
                                        ${definition.displayModelPropertyValues()}
                                    });
                                });
                            }
                        )
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBuildModelImplementationType()}
                        ${maybeDeclareBindingModifiers()};

                        builder.${bindingMethodName}(
                            "${name}",
                            ${definition.publicTypeClassName}.class,
                            ${anotherBindingTypeClassName}.class,
                            (context, definition, model, parent) -> {
                                Services services = context.getObjectFactory().newInstance(Services.class);
                                System.out.println("Binding ${definition.publicTypeClassName}");
                                System.out.println("${name} model class: " + model.getClass().getSimpleName());
                                System.out.println("${name} parent model class: " + context.getBuildModel(parent).getClass().getSimpleName());

                                ${definition.buildModelMapping}

                                services.getTaskRegistrar().register("print${definition.publicTypeClassName}2Configuration", task -> {
                                    task.doLast(t -> {
                                        ${definition.displayDefinitionPropertyValues()}
                                        ${definition.displayModelPropertyValues()}
                                    });
                                });
                            }
                        );
                    }

                    ${servicesInterface}
                }

                @Override
                public void apply(Project project) {

                }
            }
        """
    }
}
