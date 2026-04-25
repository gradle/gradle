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
 * Generates a {@code @BindsProjectType}-annotated plugin that exposes multiple project types from
 * a single plugin file. Each binding gets its own concrete {@code ApplyAction} class because the
 * definition / build-model generic parameters differ per binding.
 *
 * <p>Kotlin rendering is intentionally unsupported — no fixture currently exercises a multi-type
 * project-type plugin in Kotlin.</p>
 */
class MultiTypePluginBuilder extends AbstractTypePluginBuilder {

    @Override
    protected String renderJava() {
        def allBindings = bindings.collect { [name: it.name, definition: it.definition] }

        def bindCalls = allBindings.collect { binding ->
            "builder.bindProjectType(\"${binding.name}\", ${binding.definition.className}.class, ${binding.definition.className}ApplyAction.class);"
        }.join("\n")

        def applyActionClasses = allBindings.collect { binding ->
            def bindingDefinition = binding.definition
            """
                static abstract class ${bindingDefinition.className}ApplyAction implements ${ProjectTypeApplyAction.class.name}<${bindingDefinition.className}, ${bindingDefinition.fullyQualifiedBuildModelClassName}> {
                    @javax.inject.Inject public ${bindingDefinition.className}ApplyAction() { }

                    @javax.inject.Inject
                    abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${bindingDefinition.className} definition, ${bindingDefinition.fullyQualifiedBuildModelClassName} model) {
                        System.out.println("Binding " + ${bindingDefinition.className}.class.getSimpleName());
                        ${bindingDefinition.getBuildModelMapping(language)}
                        getTaskRegistrar().register("print${bindingDefinition.className}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                ${bindingDefinition.displayDefinitionPropertyValues(language)}
                                ${bindingDefinition.displayModelPropertyValues(language)}
                            });
                        });
                    }
                }
            """
        }.join("\n")

        return """
            package ${packageName};

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${ProjectTypeApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${BindsProjectType.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding.class)
            abstract public class ${pluginClassName} implements Plugin<Project> {
                static class Binding implements ${ProjectTypeBinding.class.name} {
                    public void bind(${ProjectTypeBindingBuilder.class.name} builder) {
                        ${bindCalls}
                    }
                }

                ${applyActionClasses}

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }

    @Override
    protected String renderKotlin() {
        throw new UnsupportedOperationException(
            "Multi-type project-type plugin Kotlin rendering is not supported. " +
            "No existing fixture exercises this combination."
        )
    }
}
