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

package org.gradle.features.internal

import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.registration.TaskRegistrar
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.features.annotations.BindsProjectFeature

trait ProjectFeatureFixture extends ProjectTypeFixture {
    PluginBuilder withProjectFeature(ProjectTypeDefinitionClassBuilder projectTypeDefinition, ProjectTypePluginClassBuilder projectType, ProjectFeatureDefinitionClassBuilder projectFeatureDefinition, ProjectFeaturePluginClassBuilder projectFeature, SettingsPluginClassBuilder settingsBuilder) {
        PluginBuilder pluginBuilder = withProjectType(
            projectTypeDefinition,
            projectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.test-software-feature-impl", projectFeature.projectFeaturePluginClassName)
        projectFeature.build(pluginBuilder)
        projectFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectFeature() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withUnsafeProjectFeatureDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withUnsafeProjectFeatureDefinitionDeclaredUnsafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition).withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndInjectableDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndNestedInjectableDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withNestedInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndMultipleInjectableDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withInjectedServices().withNestedInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndInjectableParentDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithInjectableParentClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withPolyUnsafeProjectFeatureDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder().withInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withMultipleProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
            .name("anotherFeature")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)

        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)

        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withTwoProjectFeaturesThatHaveTheSameName() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)

        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)

        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withTwoProjectFeaturesThatHaveTheSameNameButDifferentBindings() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)

        def anotherProjectTypeDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def anotherProjectType = new ProjectTypePluginClassBuilder(anotherProjectTypeDefinition)
            .name("anotherProjectType")
            .projectTypePluginClassName("AnotherProjectTypePlugin")
            .withoutConventions()
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .bindingTypeClassName(anotherProjectTypeDefinition.publicTypeClassName)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectType(anotherProjectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)

        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)

        anotherProjectType.build(pluginBuilder)
        anotherProjectTypeDefinition.build(pluginBuilder)
        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectFeatureDefinitionThatHasPublicAndImplementationTypes() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithPublicAndImplementationTypesClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeaturePluginThatDoesNotExposeProjectFeatures() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new NotAProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatBindsToBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindToBuildModel()
            .bindingTypeClassName(projectTypeDefinition.fullyQualifiedBuildModelClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureBuildModelThatHasPublicAndImplementationTypes() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithImplementationAndPublicBuildModelTypesClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectTypeAndFeatureThatBindsToNestedDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionThatRegistersANestedBindingLocationClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)

        def projectFeatureDefinition = new ProjectFeatureNestedDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindingTypeClassName(projectTypeDefinition.fullyQualifiedPublicTypeClassName + ".Foo")

        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectTypeAndFeatureThatBindsToNestedBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionThatRegistersANestedBindingLocationClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)

        def projectFeatureDefinition = new ProjectFeatureNestedDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindingTypeClassName(projectTypeDefinition.fullyQualifiedPublicTypeClassName + ".FooBuildModel")
            .bindToBuildModel()

        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatHasNoBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithNoBuildModelClassBuilder()
        def projectFeature = new ProjectFeatureWithNoBuildModelPluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatHasNoBuildModelAndAnotherFeatureThatBindsToItsDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithNoBuildModelClassBuilder()
        def projectFeature = new ProjectFeatureWithNoBuildModelPluginClassBuilder(projectFeatureDefinition)
        def anotherFeatureDefinition = new ProjectFeatureThatBindsToDefinitionWithNoBuildModeClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
            .bindingTypeClassName(projectFeatureDefinition.publicTypeClassName)
            .name("anotherFeature")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)
        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)
        return pluginBuilder
    }

    PluginBuilder withProjectFeatureThatBindsToNoneBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindingTypeClassName(BuildModel.name + ".None")
            .bindToBuildModel()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureWithUnsafeApplyActionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureWithUnsafeApplyActionDeclaredUnsafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder(projectFeatureDefinition).withUnsafeApplyAction()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureWithUnsafeApplyActionInjectingUnknownService() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginThatInjectsUnknownServiceClassBuilder(projectFeatureDefinition).withUnsafeApplyAction()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatBindsWithClass() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypeThatBindsWithClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionThatUsesClassInjectedMethods()
        def projectFeature = new ProjectFeatureThatBindsWithClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withKotlinProjectFeaturePlugin() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new KotlinProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withKotlinProjectFeaturePluginsThatHasNoBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithNoBuildModelClassBuilder()
        def projectFeature = new KotlinReifiedProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withKotlinProjectFeaturePluginThatBindsWithClass() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionThatUsesClassInjectedMethods()
        def projectFeature = new KotlinProjectFeaturePluginClassThatBindsWithClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    static class ProjectFeaturePluginClassBuilder {
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

    static class NotAProjectFeaturePluginClassBuilder extends ProjectFeaturePluginClassBuilder {
        NotAProjectFeaturePluginClassBuilder() {
            super(null)
            this.projectFeaturePluginClassName = "NotAProjectFeaturePlugin"
        }

        @Override
        protected String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class ${projectFeaturePluginClassName} implements Plugin<Project> {
                    @Override
                    public void apply(Project project) {

                    }
                }
            """
        }
    }

    static class KotlinProjectFeaturePluginClassBuilder extends ProjectFeaturePluginClassBuilder {
        KotlinProjectFeaturePluginClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
            super(definition)
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/kotlin/org/gradle/test/${projectFeaturePluginClassName}.kt") << getClassContent()
        }

        @Override
        protected String getClassContent() {
            String content = """
                package org.gradle.test

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.Task
                import ${BindsProjectFeature.class.name}
                import ${ProjectFeatureBindingBuilder.class.name}
                import ${ProjectFeatureBinding.class.name}
                import org.gradle.features.dsl.bindProjectFeatureToDefinition
                import org.gradle.test.${bindingTypeClassName}

                @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding::class)
                class ${projectFeaturePluginClassName} : Plugin<Project> {

                    class Binding : ${ProjectFeatureBinding.class.simpleName} {
                        override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                            builder.bindProjectFeatureToDefinition("${name}", ${definition.publicTypeClassName}::class, ${bindingTypeClassName}::class) { definition, model, parent  ->
                                val services = objectFactory.newInstance(Services::class.java)
                                println("Binding ${definition.publicTypeClassName}")
                                ${convertToKotlin(definition.buildModelMapping)}
                                services.taskRegistrar.register("print${definition.publicTypeClassName}Configuration") { task: Task ->
                                    task.doLast { _: Task ->
                                        ${definition.displayDefinitionPropertyValues().replaceAll(';', '')}
                                        ${definition.displayModelPropertyValues().replaceAll(';', '')}
                                    }
                                }
                            }
                            ${maybeDeclareDefinitionImplementationType()}
                            ${maybeDeclareBuildModelImplementationType()}
                            ${maybeDeclareBindingModifiers()}
                        }

                        ${servicesInterface}
                    }

                    override fun apply(project: Project) {
                    }
                }
            """
            return content
        }

        String convertToKotlin(String content) {
            return content.replaceAll(';', '')
                .replaceAll("getProjectFeatureLayout\\Q()\\E", 'projectFeatureLayout')
        }

        @Override
        String getServicesInterface() {
            return """
                interface Services {
                    @get:javax.inject.Inject
                    val taskRegistrar: ${TaskRegistrar.class.name}

                    @get:javax.inject.Inject
                    val projectFeatureLayout: ${ProjectFeatureLayout.class.name}
                }
            """
        }
    }

    static class KotlinReifiedProjectFeaturePluginClassBuilder extends KotlinProjectFeaturePluginClassBuilder {
        KotlinReifiedProjectFeaturePluginClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
            super(definition)
        }

        @Override
        protected String getClassContent() {
            String content = """
                package org.gradle.test

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.Task
                import ${BindsProjectFeature.class.name}
                import ${ProjectFeatureBindingBuilder.class.name}
                import ${ProjectFeatureBinding.class.name}
                import org.gradle.features.dsl.bindProjectFeature

                @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding::class)
                class ${projectFeaturePluginClassName} : Plugin<Project> {

                    class Binding : ${ProjectFeatureBinding.class.simpleName} {
                        override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                            builder.bindProjectFeature<
                                ${definition.publicTypeClassName},
                                ${bindingTypeClassName},
                                ${definition.buildModelFullPublicClassName}
                            >("${name}") { definition, model, parent  ->
                                val services = objectFactory.newInstance(Services::class.java)
                                println("Binding ${definition.publicTypeClassName}")
                                println("${name} model class: " + model::class.java.getSimpleName())
                                println("${name} parent model class: " + getBuildModel(parent)::class.java.getSimpleName())
                                ${convertToKotlin(definition.buildModelMapping)}
                                services.taskRegistrar.register("print${definition.publicTypeClassName}Configuration") { task: Task ->
                                    task.doLast { _: Task ->
                                        ${definition.displayDefinitionPropertyValues().replaceAll(';', '')}
                                        ${definition.displayModelPropertyValues().replaceAll(';', '')}
                                    }
                                }
                            }
                            ${maybeDeclareDefinitionImplementationType()}
                            ${maybeDeclareBuildModelImplementationType()}
                            ${maybeDeclareBindingModifiers()}
                        }

                        ${servicesInterface}
                    }

                    override fun apply(project: Project) {
                    }
                }
            """
            return content
        }
    }

    static class KotlinProjectFeaturePluginClassThatBindsWithClassBuilder extends KotlinProjectFeaturePluginClassBuilder {
        KotlinProjectFeaturePluginClassThatBindsWithClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
            super(definition)
        }

        @Override
        protected String getClassContent() {
            String content = """
                package org.gradle.test

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.Task
                import ${BindsProjectFeature.class.name}
                import ${ProjectFeatureBindingBuilder.class.name}
                import ${ProjectFeatureBinding.class.name}
                import ${ProjectFeatureApplyAction.class.name}
                import org.gradle.features.dsl.bindProjectFeature

                @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding::class)
                class ${projectFeaturePluginClassName} : Plugin<Project> {

                    class Binding : ${ProjectFeatureBinding.class.simpleName} {
                        override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                            builder.bindProjectFeature("${name}", ApplyAction::class)
                        }
                        ${maybeDeclareDefinitionImplementationType()}
                        ${maybeDeclareBuildModelImplementationType()}
                        ${maybeDeclareBindingModifiers()}
                    }

                    abstract class ApplyAction : ${ProjectFeatureApplyAction.class.simpleName}<${definition.publicTypeClassName}, ${definition.buildModelFullPublicClassName}, ${bindingTypeClassName}> {
                        @javax.inject.Inject
                        constructor()

                        ${injectedServices}

                        override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${definition.publicTypeClassName}, model: ${definition.buildModelFullPublicClassName}, parent: ${bindingTypeClassName}) {
                            println("Binding ${definition.publicTypeClassName}")
                            println("${name} model class: " + model::class.java.getSimpleName())
                            println("${name} parent model class: " + context.getBuildModel(parent)::class.java.getSimpleName())
                            ${convertToKotlin(definition.buildModelMapping)}
                            taskRegistrar.register("print${definition.publicTypeClassName}Configuration") { task: Task ->
                                task.doLast { _: Task ->
                                    ${convertToKotlin(definition.displayDefinitionPropertyValues())}
                                    ${convertToKotlin(definition.displayModelPropertyValues())}
                                }
                            }
                        }
                    }

                    override fun apply(project: Project) {
                    }
                }
            """
            return content
        }

        String getInjectedServices() {
            return """
                @get:javax.inject.Inject
                abstract val projectFeatureLayout: ${ProjectFeatureLayout.class.name}

                @get:javax.inject.Inject
                abstract val taskRegistrar: ${TaskRegistrar.class.name}
            """
        }
    }

    static class ProjectFeatureWithNoBuildModelPluginClassBuilder extends ProjectFeaturePluginClassBuilder {
        ProjectFeatureWithNoBuildModelPluginClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
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
                import ${TaskRegistrar.class.name};

                @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding.class)
                public class ${projectFeaturePluginClassName} implements Plugin<Project> {

                    static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                        @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                            builder.${bindingMethodName}(
                                "${name}",
                                ${definition.publicTypeClassName}.class,
                                ${bindingTypeClassName}.class,
                                (context, definition, model, parent) -> {
                                    System.out.println("Binding ${definition.publicTypeClassName}");
                                    System.out.println("${name} model class: " + model.getClass().getSimpleName());

                                    TestProjectTypeDefinition.ModelType parentModel = context.getBuildModel(parent);
                                    parentModel.getId().set(definition.getText());
                                }
                            )
                            ${maybeDeclareDefinitionImplementationType()}
                            ${maybeDeclareBindingModifiers()};
                        }
                    }

                    @Override
                    public void apply(Project project) {

                    }
                }
            """
        }
    }

    static class ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder extends ProjectFeaturePluginClassBuilder {
        ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
            super(definition)
        }

        @Override
        String getServicesInterface() {
            return """
                interface Services {
                    @javax.inject.Inject
                    ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

                    @javax.inject.Inject
                    ${ProviderFactory.class.name} getProviderFactory();

                    @javax.inject.Inject
                    Project getProject(); // Unsafe Service

                    default ${TaskContainer.class.name} getTaskRegistrar() {
                        return getProject().getTasks();
                    }
                }
            """
        }
    }

    static class ProjectFeaturePluginThatInjectsUnknownServiceClassBuilder extends ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder {
        ProjectFeaturePluginThatInjectsUnknownServiceClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
            super(definition)
        }

        @Override
        String getServicesInterface() {
            return """
                interface Services {
                    @javax.inject.Inject
                    ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

                    @javax.inject.Inject
                    ${ProviderFactory.class.name} getProviderFactory();

                    default ${TaskRegistrar.class.name} getTaskRegistrar() {
                        return getUnknownService();
                    }

                    @javax.inject.Inject
                    UnknownService getUnknownService();
                }

                interface UnknownService extends ${TaskRegistrar.class.name} { }
            """
        }
    }

    static class ProjectFeatureThatBindsWithClassBuilder extends ProjectFeaturePluginClassBuilder {
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

    static class ProjectFeatureDefinitionClassBuilder {
        String publicTypeClassName = "FeatureDefinition"
        String implementationTypeClassName = "FeatureDefinitionImpl"
        String buildModelPublicTypeClassName = "FeatureModel"
        String buildModelImplementationTypeClassName = "FeatureModelImpl"

        boolean hasInjectedServices = false
        boolean hasNestedInjectedServices = false
        boolean hasDefinitionImplementationType = false
        boolean hasBuildModelImplementationType = false

        ProjectFeatureDefinitionClassBuilder withInjectedServices() {
            this.hasInjectedServices = true
            return this
        }

        ProjectFeatureDefinitionClassBuilder withNestedInjectedServices() {
            this.hasNestedInjectedServices = true
            return this
        }

        ProjectFeatureDefinitionClassBuilder withPublicClassName(String className) {
            this.publicTypeClassName = className
            return this
        }

        ProjectFeatureDefinitionClassBuilder withImplementationClassName(String className) {
            this.implementationTypeClassName = className
            return this
        }

        ProjectFeatureDefinitionClassBuilder buildModelPublicTypeClassName(String className) {
            this.buildModelPublicTypeClassName = className
            return this
        }

        ProjectFeatureDefinitionClassBuilder buildModelImplementationTypeClassName(String className) {
            this.buildModelImplementationTypeClassName = className
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${publicTypeClassName}.java") << getPublicTypeClassContent()
            if (hasDefinitionImplementationType) {
                pluginBuilder.file("src/main/java/org/gradle/test/${implementationTypeClassName}.java") << getImplementationTypeClassContent()
            }
        }

        protected String getPublicTypeClassContent() {
            getDefaultClassContent(publicTypeClassName)
        }

        protected String getImplementationTypeClassContent() {
            return null
        }

        protected String getDefaultClassContent(String className) {
            return """
                package org.gradle.test;

                import ${Definition.class.name};
                import ${BuildModel.class.name};
                import org.gradle.api.provider.Property;
                import org.gradle.api.file.DirectoryProperty;
                import ${HiddenInDefinition.class.name};
                import org.gradle.api.Action;
                import org.gradle.api.tasks.Nested;
                import javax.inject.Inject;
                import org.gradle.api.model.ObjectFactory;

                public interface ${className} extends ${Definition.class.simpleName}<${className}.${buildModelPublicTypeClassName}> {
                    Property<String> getText();

                    ${getMaybeInjectedServiceDeclaration()}

                    @Nested
                    Fizz getFizz();

                    @${HiddenInDefinition.class.simpleName}
                    default void fizz(Action<? super Fizz> action) {
                        action.execute(getFizz());
                    }

                    interface ${buildModelPublicTypeClassName} extends BuildModel {
                        Property<String> getText();
                        DirectoryProperty getDir();
                    }

                    interface Fizz {
                        ${getMaybeNestedInjectedServiceDeclaration()}
                        Property<String> getBuzz();
                    }
                }
            """
        }

        String getBuildModelMapping() {
            return """
                model.getText().set(definition.getText());
                model.getDir().set(services.getProjectFeatureLayout().getProjectDirectory().dir(definition.getText()));
            """
        }

        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "text", "definition.getText().get()")}
                ${displayProperty("definition", "fizz.buzz", "definition.getFizz().getBuzz().get()")}
            """
        }

        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "text", "model.getText().get()")}
                ${displayProperty("model", "dir", "model.getDir().get().getAsFile().getAbsolutePath()")}
            """
        }

        String getBuildModelFullPublicClassName() {
            return "${publicTypeClassName}.${buildModelPublicTypeClassName}"
        }

        String getBuildModelFullImplementationClassName() {
            return "${publicTypeClassName}.${buildModelImplementationTypeClassName}"
        }

        String getMaybeInjectedServiceDeclaration() {
            return hasInjectedServices ? """
                @Inject
                ObjectFactory getObjects();
            """ : ""
        }

        String getMaybeNestedInjectedServiceDeclaration() {
            return hasNestedInjectedServices ? """
                @Inject
                ObjectFactory getObjects();
            """ : ""
        }

        static String displayProperty(String objectType, String propertyName, String propertyValueExpression) {
            return """
                System.out.println("${objectType} ${propertyName} = " + ${propertyValueExpression});
            """
        }
    }

    static class ProjectFeatureNestedDefinitionClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        @Override
        String getBuildModelMapping() {
            return super.getBuildModelMapping() + """
                model.getText().set(services.getProviderFactory().provider(() -> definition.getText().get() + " " + context.getBuildModel(parent).getBarProcessed().get()));
            """
        }
    }

    static class ProjectFeatureDefinitionWithPublicAndImplementationTypesClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        String publicTypeClassName = "FeatureDefinition"

        ProjectFeatureDefinitionWithPublicAndImplementationTypesClassBuilder() {
            this.hasDefinitionImplementationType = true
        }

        @Override
        protected String getImplementationTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.provider.Property;

                public interface ${implementationTypeClassName} extends ${publicTypeClassName} {
                    Property<String> getNonPublicProperty();
                }
            """
        }
    }

    static class ProjectFeatureDefinitionWithImplementationAndPublicBuildModelTypesClassBuilder extends ProjectFeatureDefinitionClassBuilder {

        ProjectFeatureDefinitionWithImplementationAndPublicBuildModelTypesClassBuilder() {
            this.hasBuildModelImplementationType = true
        }

        @Override
        protected String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                public interface ${publicTypeClassName} extends Definition<${publicTypeClassName}.${buildModelPublicTypeClassName}> {
                    Property<String> getText();

                    interface ${buildModelPublicTypeClassName} extends BuildModel {
                        Property<String> getText();
                    }

                    abstract class ${buildModelImplementationTypeClassName} implements ${buildModelPublicTypeClassName} {

                    }
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return """
                model.getText().set(definition.getText());
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "text", "definition.getText().get()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "text", "model.getText().get()")}
            """
        }
    }

    static class ProjectFeatureDefinitionAbstractClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        @Override
        protected String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import ${Definition.class.name};
                import ${BuildModel.class.name};
                import org.gradle.api.provider.Property;
                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.Action;
                import org.gradle.api.tasks.Nested;
                import ${HiddenInDefinition.class.name};
                import javax.inject.Inject;

                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.FeatureModel> {
                    public abstract Property<String> getText();

                    ${maybeInjectedServiceDeclaration}

                    @Nested
                    public abstract Fizz getFizz();

                    @${HiddenInDefinition.simpleName}
                    public void fizz(Action<? super Fizz> action) {
                        action.execute(getFizz());
                    }

                    public interface Fizz {
                        ${maybeNestedInjectedServiceDeclaration}
                        Property<String> getBuzz();
                    }

                    public interface FeatureModel extends BuildModel {
                        Property<String> getText();
                        DirectoryProperty getDir();
                    }
                }
            """
        }

        @Override
        String getMaybeInjectedServiceDeclaration() {
            return hasInjectedServices ? """
                @Inject
                abstract ObjectFactory getObjects();
            """ : ""
        }

        @Override
        String getMaybeNestedInjectedServiceDeclaration() {
            return hasNestedInjectedServices ? """
                @Inject
                abstract ObjectFactory getObjects();
            """ : ""
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "text", "definition.getText().get()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "text", "model.getText().get()")}
            """
        }
    }

    static class ProjectFeatureDefinitionWithInjectableParentClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        String parentTypeClassName = "ParentFeatureDefinition"

        ProjectFeatureDefinitionWithInjectableParentClassBuilder() {
            // Add injected service to the parent
            withInjectedServices()
        }

        @Override
        protected String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.provider.Property;

                public interface ${publicTypeClassName} extends ${parentTypeClassName} {
                    Property<String> getNonPublicProperty();
                }
            """
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/${parentTypeClassName}.java") << getDefaultClassContent(parentTypeClassName)
        }
    }

    static class ProjectFeatureDefinitionWithNoBuildModelClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        @Override
        protected String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import ${Definition.class.name};
                import ${BuildModel.class.name};
                import org.gradle.api.provider.Property;
                import org.gradle.api.Action;
                import org.gradle.api.tasks.Nested;
                import javax.inject.Inject;
                import org.gradle.api.model.ObjectFactory;

                public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${BuildModel.class.simpleName}.None> {
                    Property<String> getText();
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return ""
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "text", "definition.getText().get()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return ""
        }

        @Override
        String getBuildModelPublicTypeClassName() {
            return "${BuildModel.class.simpleName}.None"
        }

        @Override
        String getBuildModelFullPublicClassName() {
            return "${BuildModel.class.name}.None"
        }

        @Override
        String getBuildModelImplementationTypeClassName() {
            return getBuildModelFullPublicClassName()
        }
    }

    static class ProjectFeatureThatBindsToDefinitionWithNoBuildModeClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        @Override
        String getBuildModelMapping() {
            return super.getBuildModelMapping() + """
                model.getText().set(parent.getText().map(text -> text + " " + definition.getText().get()));
            """
        }
    }

    static class ProjectFeatureDefinitionThatUsesClassInjectedMethods extends ProjectFeatureDefinitionClassBuilder {
        @Override
        String getBuildModelMapping() {
            return """
                model.getText().set(definition.getText());
                model.getDir().set(getProjectFeatureLayout().getProjectDirectory().dir(definition.getText()));
            """
        }
    }

}
