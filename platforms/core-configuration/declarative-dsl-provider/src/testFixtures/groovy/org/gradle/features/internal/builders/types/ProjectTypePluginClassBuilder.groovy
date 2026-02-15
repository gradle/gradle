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
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * A builder for generating a project type plugin class.
 *
 * The generated plugin class will bind a project type with the provided definition and register a task to print the values of the definition's properties.
 *
 * This class can be used as a basis for subclasses that generate project type plugin classes with different behaviors and qualities.
 */
class ProjectTypePluginClassBuilder {
    final ProjectTypeDefinitionClassBuilder definition
    String projectTypePluginClassName = "ProjectTypeImplPlugin"
    String name = "testProjectType"
    String conventions = """
        definition.getId().convention("<no id>");
        definition.getFoo().getBar().convention("bar");
    """

    ProjectTypePluginClassBuilder(ProjectTypeDefinitionClassBuilder definition) {
        this.definition = definition
    }

    List<String> bindingModifiers = []

    ProjectTypePluginClassBuilder projectTypePluginClassName(String projectTypePluginClassName) {
        this.projectTypePluginClassName = projectTypePluginClassName
        return this
    }

    ProjectTypePluginClassBuilder name(String name) {
        this.name = name
        return this
    }

    ProjectTypePluginClassBuilder conventions(String conventions) {
        this.conventions = conventions
        return this
    }

    ProjectTypePluginClassBuilder withoutConventions() {
        this.conventions = null
        return this
    }

    ProjectTypePluginClassBuilder withUnsafeDefinition() {
        this.bindingModifiers.add("withUnsafeDefinition()")
        return this
    }

    ProjectTypePluginClassBuilder withUnsafeApplyAction() {
        this.bindingModifiers.add("withUnsafeApplyAction()")
        return this
    }

    void build(PluginBuilder pluginBuilder) {
        pluginBuilder.file("src/main/java/org/gradle/test/${projectTypePluginClassName}.java") << getClassContent()
    }

    protected String getClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${definition.publicTypeClassName}.class, (context, definition, model) -> {
                            Services services = context.getObjectFactory().newInstance(Services.class);

                            System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());
                            ${conventions == null ? "" : conventions}

                            ${definition.buildModelMapping}

                            services.getTaskRegistrar().register("print${definition.publicTypeClassName}Configuration", DefaultTask.class, task -> {
                                task.doLast("print restricted extension content", t -> {
                                    ${definition.displayDefinitionPropertyValues()}
                                    ${definition.displayModelPropertyValues()}
                                });
                            });
                        })
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBindingModifiers()};
                    }

                    ${servicesInterface}
                }

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }

    String maybeDeclareDefinitionImplementationType() {
        return definition.hasImplementationType ? ".withUnsafeDefinitionImplementationType(${definition.implementationTypeClassName}.class)" : ""
    }

    String maybeDeclareBindingModifiers() {
        return bindingModifiers.isEmpty() ? "" : bindingModifiers.collect { ".${it}" }.join("")
    }

    String getServicesInterface() {
        return """
            interface Services {
                @javax.inject.Inject
                ${TaskRegistrar.class.name} getTaskRegistrar();
            }
        """
    }
}
