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
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionClassBuilder
import org.gradle.features.registration.TaskRegistrar
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * Builder for generating a project feature plugin class that binds a project feature definition to a build model.
 *
 * This class is used as a basis for subclasses that override methods to create feature plugins with different behavior and qualities.
 */
class ProjectFeaturePluginClassBuilder {
    final ProjectFeatureDefinitionClassBuilder definition
    String projectFeaturePluginClassName = "ProjectFeatureImplPlugin"
    String bindingTypeClassName = "TestProjectTypeDefinition"
    String bindingMethodName = "bindProjectFeatureToDefinition"
    List<String> bindingModifiers = []
    String name = "feature"

    ProjectFeaturePluginClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
        this.definition = definition
    }

    ProjectFeaturePluginClassBuilder projectFeaturePluginClassName(String className) {
        this.projectFeaturePluginClassName = className
        return this
    }

    ProjectFeaturePluginClassBuilder name(String name) {
        this.name = name
        return this
    }

    ProjectFeaturePluginClassBuilder bindToDefinition() {
        this.bindingMethodName = "bindProjectFeatureToDefinition"
        return this
    }

    ProjectFeaturePluginClassBuilder bindToBuildModel() {
        this.bindingMethodName = "bindProjectFeatureToBuildModel"
        return this
    }

    ProjectFeaturePluginClassBuilder withUnsafeDefinition() {
        this.bindingModifiers.add("withUnsafeDefinition()")
        return this
    }

    ProjectFeaturePluginClassBuilder withUnsafeApplyAction() {
        this.bindingModifiers.add("withUnsafeApplyAction()")
        return this
    }

    ProjectFeaturePluginClassBuilder bindingTypeClassName(String className) {
        this.bindingTypeClassName = className
        return this
    }

    void build(PluginBuilder pluginBuilder) {
        pluginBuilder.file("src/main/java/org/gradle/test/${projectFeaturePluginClassName}.java") << getClassContent()
    }

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

                                services.getTaskRegistrar().register("print${definition.publicTypeClassName}Configuration", task -> {
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
                    }

                    ${servicesInterface}
                }

                @Override
                public void apply(Project project) {

                }
            }
        """
    }

    String maybeDeclareDefinitionImplementationType() {
        return (definition.hasDefinitionImplementationType) ? ".withUnsafeDefinitionImplementationType(${definition.implementationTypeClassName}.class)" : ""
    }

    String maybeDeclareBuildModelImplementationType() {
        return (definition.hasBuildModelImplementationType) ? ".withBuildModelImplementationType(${definition.getBuildModelFullImplementationClassName()}.class)" : ""
    }

    String maybeDeclareBindingModifiers() {
        return bindingModifiers.isEmpty() ? "" : bindingModifiers.collect { ".${it}" }.join("")
    }

    String getServicesInterface() {
        return """
            interface Services {
                @javax.inject.Inject
                ${TaskRegistrar.class.name} getTaskRegistrar();

                @javax.inject.Inject
                ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

                @javax.inject.Inject
                ${ProviderFactory.class.name} getProviderFactory();
            }
        """
    }
}
