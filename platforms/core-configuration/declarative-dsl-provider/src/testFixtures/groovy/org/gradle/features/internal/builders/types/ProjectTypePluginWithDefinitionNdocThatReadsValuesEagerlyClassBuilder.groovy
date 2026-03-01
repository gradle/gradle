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
 * Variant of {@link ProjectTypePluginWithDefinitionNdocClassBuilder} whose apply action reads
 * {@code sourceDir} eagerly (via {@code .get()}) from each NDOC element at apply time. Used to test
 * that NDOC element properties are configured before apply runs (issue #36673 with NDOC).
 */
class ProjectTypePluginWithDefinitionNdocThatReadsValuesEagerlyClassBuilder extends ProjectTypePluginClassBuilder {

    ProjectTypePluginWithDefinitionNdocThatReadsValuesEagerlyClassBuilder(ProjectTypeDefinitionWithNdocContainingDefinitionsClassBuilder definition) {
        super(definition)
        withUnsafeApplyAction()
    }

    @Override
    protected String getClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import ${TaskRegistrar.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                static class DefaultSourceModel implements ${definition.publicTypeClassName}.Source.SourceModel {
                    private final Property<String> processedDir;

                    @Inject
                    public DefaultSourceModel(ObjectFactory objects) {
                        this.processedDir = objects.property(String.class);
                    }

                    @Override
                    public Property<String> getProcessedDir() { return processedDir; }
                }

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${definition.publicTypeClassName}.class, (context, definition, model) -> {
                            Services services = context.getObjectFactory().newInstance(Services.class);

                            System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());

                            // Eager read at apply time - throws if source dir not yet configured
                            // (Pre-computed to avoid capturing non-serializable objects for configuration cache)
                            java.util.List<String> eagerLines = definition.getSources().stream().map(source -> {
                                String dir = source.getSourceDir().get();
                                return "eager source dir for " + source.getName() + " = " + dir;
                            }).collect(java.util.stream.Collectors.toList());
                            services.getTaskRegistrar().register("printEagerSourceValues", DefaultTask.class, task -> {
                                task.doLast("print", t -> {
                                    for (String line : eagerLines) { System.out.println(line); }
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
