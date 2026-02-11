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

/**
 * A {@link ProjectTypePluginClassBuilder} for generating a project type plugin class that binds using an apply action class instead of a lambda.
 */
class ProjectTypeThatBindsWithClassBuilder extends ProjectTypePluginClassBuilder {
    ProjectTypeThatBindsWithClassBuilder(ProjectTypeDefinitionClassBuilder definition) {
        super(definition)
    }

    @Override
    protected String getClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${ProjectTypeApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${definition.publicTypeClassName}.class, ${projectTypePluginClassName}.ApplyAction.class)
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBindingModifiers()};
                    }
                }

                static abstract class ApplyAction implements ${ProjectTypeApplyAction.class.simpleName}<${definition.publicTypeClassName}, ${definition.fullyQualifiedBuildModelClassName}> {
                    @javax.inject.Inject
                    public ApplyAction() { }

                    ${serviceInjections}

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.simpleName} context, ${definition.publicTypeClassName} definition, ${definition.fullyQualifiedBuildModelClassName} model) {
                        System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());
                        ${conventions == null ? "" : conventions}

                        ${definition.buildModelMapping}

                        getTaskRegistrar().register("print${definition.publicTypeClassName}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                ${definition.displayDefinitionPropertyValues()}
                                ${definition.displayModelPropertyValues()}
                            });
                        });
                    }
                }

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }

    String getServiceInjections() {
        return """
            @javax.inject.Inject
            abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();
        """
    }
}
