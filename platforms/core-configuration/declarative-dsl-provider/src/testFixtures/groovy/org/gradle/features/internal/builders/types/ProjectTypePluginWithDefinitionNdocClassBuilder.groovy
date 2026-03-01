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
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionWithNdocContainingDefinitionsClassBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * Generates a project type plugin that uses {@code withNestedBuildModelImplementationType} to register
 * a concrete implementation ({@code DefaultSourceModel}) for {@code Source.SourceModel}. The apply action
 * uses {@code context.getBuildModel(source)} to retrieve auto-registered source models, maps the definition
 * values, and registers tasks to display results.
 */
class ProjectTypePluginWithDefinitionNdocClassBuilder extends ProjectTypePluginClassBuilder {

    ProjectTypePluginWithDefinitionNdocClassBuilder(ProjectTypeDefinitionWithNdocContainingDefinitionsClassBuilder definition) {
        super(definition)
    }

    @Override
    protected String getClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import ${TaskRegistrar.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {

                    // Concrete implementation for Source.SourceModel, registered via
                    // withNestedBuildModelImplementationType below.
                    static class DefaultSourceModel implements ${definition.publicTypeClassName}.Source.SourceModel {
                        private final Property<String> processedDir;

                        @Inject
                        public DefaultSourceModel(ObjectFactory objects) {
                            this.processedDir = objects.property(String.class);
                        }

                        @Override
                        public Property<String> getProcessedDir() { return processedDir; }
                    }

                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${definition.publicTypeClassName}.class, (context, definition, model) -> {
                            Services services = context.getObjectFactory().newInstance(Services.class);

                            System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());

                            ${definition.buildModelMapping}

                            services.getTaskRegistrar().register("printSourceModels", DefaultTask.class, task -> {
                                task.doLast("print", t -> {
                                    model.getSources().get().forEach(s ->
                                        System.out.println("source processed dir = " + s.getProcessedDir().get()));
                                });
                            });

                            // Pre-compute at apply time (configuration cache requires serializable captures)
                            java.util.List<String> modelClassLines = new java.util.ArrayList<>();
                            definition.getSources().forEach(s ->
                                modelClassLines.add("source model class: " + context.getBuildModel(s).getClass().getSimpleName())
                            );
                            services.getTaskRegistrar().register("printSourceModelClass", DefaultTask.class, task -> {
                                task.doLast("print", t -> {
                                    for (String line : modelClassLines) { System.out.println(line); }
                                });
                            });
                        })
                        .withNestedBuildModelImplementationType(
                            ${definition.sourceModelPublicClassName}.class,
                            DefaultSourceModel.class
                        )
                        .withUnsafeApplyAction();
                    }

                    interface Services {
                        @javax.inject.Inject
                        ${TaskRegistrar.class.name} getTaskRegistrar();
                    }
                }

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }
}
