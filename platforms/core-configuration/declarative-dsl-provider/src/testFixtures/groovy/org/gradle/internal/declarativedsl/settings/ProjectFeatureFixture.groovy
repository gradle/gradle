/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.settings

import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilder
import org.gradle.api.internal.plugins.ProjectFeatureBinding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.api.internal.plugins.BindsProjectFeature
import org.gradle.api.internal.plugins.software.RegistersProjectFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes

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
            .bindingTypeClassName(projectTypeDefinition.buildModelClassName)
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

    PluginBuilder withKotlinProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new KotlinProjectFeaturePluginClassBuilder(projectFeatureDefinition)
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
                                    String projectName = context.getProject().getName();
                                    System.out.println("Binding ${definition.publicTypeClassName}");
                                    System.out.println("${name} model class: " + model.getClass().getSimpleName());

                                    ${definition.buildModelMapping}

                                    context.getProject().getTasks().register("print${definition.publicTypeClassName}Configuration", task -> {
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
                import org.gradle.api.internal.plugins.features.dsl.bindProjectFeatureToDefinition
                import org.gradle.test.${bindingTypeClassName}

                @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding::class)
                class ${projectFeaturePluginClassName} : Plugin<Project> {

                    class Binding : ${ProjectFeatureBinding.class.simpleName} {
                        override fun bind(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
                            builder.bindProjectFeatureToDefinition("${name}", ${definition.publicTypeClassName}::class, ${bindingTypeClassName}::class) { definition, model, parent  ->
                                println("Binding ${definition.publicTypeClassName}")
                                ${definition.buildModelMapping.replaceAll(':', '')}
                                val projectName = project.name
                                getProject().getTasks().register("print${definition.publicTypeClassName}Configuration") { task: Task ->
                                    task.doLast { _: Task ->
                                        ${definition.displayDefinitionPropertyValues().replaceAll(';', '')}
                                        ${definition.displayModelPropertyValues().replaceAll(';', '')}
                                    }
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
    }

    static class KotlinSettingsPluginClassBuilder extends ProjectTypeFixture.SettingsPluginClassBuilder {

        KotlinSettingsPluginClassBuilder() {
            this.pluginClassName = "ProjectFeatureRegistrationPlugin"
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/kotlin/org/gradle/test/${pluginClassName}.kt") << """
                package org.gradle.test

                import org.gradle.api.Plugin
                import org.gradle.api.initialization.Settings
                import ${RegistersProjectFeatures.class.name}
                import ${RegistersSoftwareTypes.class.name}

                @RegistersSoftwareTypes(${projectTypePluginClasses.collect { it + "::class" }.join(", ")})
                @${RegistersProjectFeatures.class.simpleName}(${projectFeaturePluginClasses.collect { it + "::class" }.join(", ")})
                class ${pluginClassName} : Plugin<Settings> {
                    override fun apply(settings: Settings) {
                    }
                }
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
                import ${Restricted.class.name};
                import ${Configuring.class.name};
                import org.gradle.api.Action;
                import org.gradle.api.tasks.Nested;
                import javax.inject.Inject;
                import org.gradle.api.model.ObjectFactory;

                @${Restricted.class.simpleName}
                public interface ${className} extends ${Definition.class.simpleName}<${className}.${buildModelPublicTypeClassName}> {
                    @${Restricted.class.simpleName}
                    Property<String> getText();

                    ${getMaybeInjectedServiceDeclaration()}

                    @Nested
                    Fizz getFizz();

                    @${Configuring.class.simpleName}
                    default void fizz(Action<? super Fizz> action) {
                        action.execute(getFizz());
                    }

                    interface ${buildModelPublicTypeClassName} extends BuildModel {
                        Property<String> getText();
                    }

                    interface Fizz {
                        ${getMaybeNestedInjectedServiceDeclaration()}
                        @${Restricted.class.simpleName}
                        Property<String> getBuzz();
                    }
                }
            """
        }

        String getBuildModelMapping() {
            return """
                model.getText().set(definition.getText());
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
            // Note that this assumes that "projectName" variable has been set in some outer scope in order to avoid
            // accessing the project object at execution time.
            return """
                System.out.println(projectName + ": ${objectType} ${propertyName} = " + ${propertyValueExpression});
            """
        }
    }

    static class ProjectFeatureNestedDefinitionClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        @Override
        String getBuildModelMapping() {
            return super.getBuildModelMapping() + """
                model.getText().set(context.getProject().provider(() -> definition.getText().get() + " " + context.getBuildModel(parent).getBarProcessed().get()));
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
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                @Restricted
                public interface ${implementationTypeClassName} extends ${publicTypeClassName} {
                    @Restricted
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
                import org.gradle.declarative.dsl.model.annotations.Restricted;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${publicTypeClassName} extends Definition<${publicTypeClassName}.${buildModelPublicTypeClassName}> {
                    @Restricted
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
                import ${Restricted.class.name};
                import ${Configuring.class.name};
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.Action;
                import org.gradle.api.tasks.Nested;
                import javax.inject.Inject;

                @${Restricted.class.simpleName}
                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.FeatureModel> {
                    @${Restricted.class.simpleName}
                    public abstract Property<String> getText();

                    ${maybeInjectedServiceDeclaration}

                    @Nested
                    abstract Fizz getFizz();

                    @${Configuring.class.simpleName}
                    public void fizz(Action<? super Fizz> action) {
                        action.execute(getFizz());
                    }

                    interface Fizz {
                        ${maybeNestedInjectedServiceDeclaration}
                        Property<String> getBuzz();
                    }

                    public interface FeatureModel extends BuildModel {
                        Property<String> getText();
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
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                @Restricted
                public interface ${publicTypeClassName} extends ${parentTypeClassName} {
                    @Restricted
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
                import ${Restricted.class.name};
                import ${Configuring.class.name};
                import org.gradle.api.Action;
                import org.gradle.api.tasks.Nested;
                import javax.inject.Inject;
                import org.gradle.api.model.ObjectFactory;

                @${Restricted.class.simpleName}
                public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${BuildModel.class.simpleName}.NONE> {
                    @${Restricted.class.simpleName}
                    Property<String> getText();
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return ""
        }

        @Override
        String displayModelPropertyValues() {
            return ""
        }
    }
}
