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
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionClassBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * Generates a project type plugin whose apply action reads {@code id} and {@code foo.bar} eagerly
 * (via {@code .get()}) at apply time. This is the key fixture for testing issue #36673: if apply runs
 * before the build script configures the definition, {@code .get()} throws {@code MissingValueException}.
 */
class ProjectTypePluginThatReadsValuesEagerlyClassBuilder extends ProjectTypePluginClassBuilder {

    ProjectTypePluginThatReadsValuesEagerlyClassBuilder(ProjectTypeDefinitionClassBuilder definition) {
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
            import org.gradle.api.provider.Property;
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import ${TaskRegistrar.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${definition.publicTypeClassName}.class, (context, definition, model) -> {
                            Services services = context.getObjectFactory().newInstance(Services.class);

                            System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());

                            // Eagerly read values at apply time.
                            // These reads throw MissingValueException if the definition
                            // hasn't been configured yet - that is what this fixture tests against.
                            String idAtApplyTime = definition.getId().get();
                            String barAtApplyTime = definition.getFoo().getBar().get();

                            ${definition.buildModelMapping}

                            services.getTaskRegistrar().register("printApplyTimeValues", DefaultTask.class, task -> {
                                task.doLast("print", t -> {
                                    System.out.println("apply time id = " + idAtApplyTime);
                                    System.out.println("apply time foo.bar = " + barAtApplyTime);
                                });
                            });
                        })
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
