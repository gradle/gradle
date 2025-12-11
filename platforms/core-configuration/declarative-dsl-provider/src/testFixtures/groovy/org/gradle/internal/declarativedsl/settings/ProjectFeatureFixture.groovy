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
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withUnsafeProjectFeatureDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withUnsafeProjectFeatureDefinitionDeclaredUnsafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder().withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndInjectableDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndNestedInjectableDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withNestedInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndMultipleInjectableDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withInjectedServices().withNestedInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndInjectableParentDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithInjectableParentClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withPolyUnsafeProjectFeatureDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder().withInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withMultipleProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder()
            .definitionImplementationType(anotherFeatureDefinition.defaultClassName)
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
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder()
            .definitionImplementationType(anotherFeatureDefinition.defaultClassName)
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
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithImplementationTypeClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
            .definitionPublicType(projectFeatureDefinition.publicTypeClassName)
            .definitionImplementationType(projectFeatureDefinition.defaultClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeaturePluginThatDoesNotExposeProjectFeatures() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new NotAProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatBindsToBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
            .bindToBuildModel()
            .bindingTypeClassName(projectTypeDefinition.buildModelClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureBuildModelThatHasPublicAndImplementationTypes() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithPublicBuildModelTypeClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
            .buildModelPublicTypeClassName(projectFeatureDefinition.buildModelFullClassName)
            .buildModelImplementationTypeClassName(projectFeatureDefinition.buildModelFullImplementationClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectTypeAndFeatureThatBindsToNestedDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder().applyActionExtraStatements(
            """
            context.registerBuildModel(definition.getFoo())
                .getBarProcessed().set(definition.getFoo().getBar().map(it -> it.toUpperCase()));
            """
        )

        def projectFeatureDefinition = new ProjectFeatureDefinitionWithPublicBuildModelTypeClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
            .bindingTypeClassName("org.gradle.test." + projectTypeDefinition.defaultClassName + ".Foo")
            .buildModelPublicTypeClassName(projectFeatureDefinition.buildModelFullClassName)
            .buildModelImplementationTypeClassName(projectFeatureDefinition.buildModelFullImplementationClassName)
            .applyActionExtraStatements("""
                model.getText().set(context.getProject().provider(() -> feature.getText().get() + " " + context.getBuildModel(parent).getBarProcessed().get()));
            """.stripIndent())

        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withKotlinProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new KotlinProjectFeaturePluginClassBuilder()
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    static class ProjectFeaturePluginClassBuilder {
        String definitionImplementationTypeClassName = "FeatureDefinition"
        String definitionPublicTypeClassName = null
        String buildModelImplementationTypeClassName = null
        String buildModelPublicTypeClassName = null
        String projectFeaturePluginClassName = "ProjectFeatureImplPlugin"
        String bindingTypeClassName = "TestProjectTypeDefinition"
        String applyActionExtraStatements = ""
        String bindingMethodName = "bindProjectFeatureToDefinition"
        List<String> bindingModifiers = []
        String name = "feature"

        ProjectFeaturePluginClassBuilder definitionImplementationType(String className) {
            this.definitionImplementationTypeClassName = className
            return this
        }

        ProjectFeaturePluginClassBuilder definitionPublicType(String className) {
            this.definitionPublicTypeClassName = className
            return this
        }

        ProjectFeaturePluginClassBuilder buildModelImplementationTypeClassName(String className) {
            this.buildModelImplementationTypeClassName = className
            return this
        }

        ProjectFeaturePluginClassBuilder buildModelPublicTypeClassName(String className) {
            this.buildModelPublicTypeClassName = className
            return this
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

        ProjectFeaturePluginClassBuilder applyActionExtraStatements(String statements) {
            this.applyActionExtraStatements = statements
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${projectFeaturePluginClassName}.java") << getClassContent()
        }

        protected String getClassContent() {
            def dslTypeClassName = definitionPublicTypeClassName ?: definitionImplementationTypeClassName
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
                                ${dslTypeClassName}.class,
                                ${bindingTypeClassName}.class,
                                (context, feature, model, parent) -> {
                                    System.out.println("Binding ${dslTypeClassName}");
                                    System.out.println("${name} model class: " + model.getClass().getSimpleName());
                                    model.getText().set(feature.getText());

                                    context.getProject().getTasks().register("print${dslTypeClassName}Configuration", task -> {
                                        task.doLast(t -> System.out.println("${name} text = " + model.getText().get()));
                                    });

                                    ${applyActionExtraStatements}
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
            return (definitionPublicTypeClassName && definitionPublicTypeClassName != definitionImplementationTypeClassName) ? ".withUnsafeDefinitionImplementationType(${definitionImplementationTypeClassName}.class)" : ""
        }

        String maybeDeclareBuildModelImplementationType() {
            return (buildModelPublicTypeClassName && buildModelPublicTypeClassName != buildModelImplementationTypeClassName) ? ".withBuildModelImplementationType(${buildModelImplementationTypeClassName}.class)" : ""
        }

        String maybeDeclareBindingModifiers() {
            return bindingModifiers.isEmpty() ? "" : bindingModifiers.collect { ".${it}" }.join("")
        }
    }

    static class NotAProjectFeaturePluginClassBuilder extends ProjectFeaturePluginClassBuilder {
        NotAProjectFeaturePluginClassBuilder() {
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
        @Override
        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/kotlin/org/gradle/test/${projectFeaturePluginClassName}.kt") << getClassContent()
        }

        @Override
        protected String getClassContent() {
            def dslTypeClassName = definitionPublicTypeClassName ?: definitionImplementationTypeClassName
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
                            builder.bindProjectFeatureToDefinition("${name}", ${dslTypeClassName}::class, ${bindingTypeClassName}::class) { feature, model, parent  ->
                                println("Binding ${dslTypeClassName}")
                                model.getText().set(feature.getText())
                                getProject().getTasks().register("print${definitionImplementationTypeClassName}Configuration") { task: Task ->
                                    task.doLast { _: Task -> System.out.println("feature text = " + model.getText().get()) }
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

    static class ProjectFeatureDefinitionClassBuilder {
        String defaultClassName = "FeatureDefinition"
        boolean hasInjectedServices = false
        boolean hasNestedInjectedServices = false

        ProjectFeatureDefinitionClassBuilder withInjectedServices() {
            this.hasInjectedServices = true
            return this
        }

        ProjectFeatureDefinitionClassBuilder withNestedInjectedServices() {
            this.hasNestedInjectedServices = true
            return this
        }

        ProjectFeatureDefinitionClassBuilder withClassName(String className) {
            this.defaultClassName = className
            return this
        }

        String getBuildModelFullClassName() {
            return "${defaultClassName}.FeatureModel"
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${defaultClassName}.java") << getClassContent()
        }

        protected String getClassContent() {
            getDefaultClassContent(defaultClassName)
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
                public interface ${className} extends ${Definition.class.simpleName}<${className}.FeatureModel> {
                    @${Restricted.class.simpleName}
                    Property<String> getText();

                    ${getMaybeInjectedServiceDeclaration()}

                    @Nested
                    Fizz getFizz();

                    @${Configuring.class.simpleName}
                    default void configureFizz(Action<? super Fizz> action) {
                        action.execute(getFizz());
                    }

                    interface FeatureModel extends BuildModel {
                        Property<String> getText();
                    }

                    interface Fizz {
                        ${getMaybeNestedInjectedServiceDeclaration()}
                        Property<String> getBuzz();
                    }
                }
            """
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
    }

    static class ProjectFeatureDefinitionWithImplementationTypeClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        String publicTypeClassName = "FeatureDefinition"

        ProjectFeatureDefinitionWithImplementationTypeClassBuilder() {
            this.defaultClassName = publicTypeClassName + "Impl"
        }

        @Override
        protected String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.provider.Property;
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                @Restricted
                public interface ${defaultClassName} extends ${publicTypeClassName} {
                    @Restricted
                    Property<String> getNonPublicProperty();
                }
            """
        }

        private String getPublicTypeContent() {
            return getDefaultClassContent(publicTypeClassName)
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/${publicTypeClassName}.java") << getPublicTypeContent()
        }
    }

    static class ProjectFeatureDefinitionWithPublicBuildModelTypeClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        String buildModelPublicTypeClassName = "FeatureModel"
        String buildModelImplementationTypeClassName = "FeatureModelImpl"

        ProjectFeatureDefinitionWithPublicBuildModelTypeClassBuilder buildModelPublicTypeClassName(String className) {
            this.buildModelPublicTypeClassName = className
            return this
        }

        ProjectFeatureDefinitionWithPublicBuildModelTypeClassBuilder buildModelImplementationTypeClassName(String className) {
            this.buildModelImplementationTypeClassName = className
            return this
        }

        @Override
        String getBuildModelFullClassName() {
            return "${defaultClassName}.${buildModelPublicTypeClassName}"
        }

        String getBuildModelFullImplementationClassName() {
            return "${defaultClassName}.${buildModelImplementationTypeClassName}"
        }

        @Override
        protected String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.provider.Property;
                import org.gradle.declarative.dsl.model.annotations.Restricted;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${defaultClassName} extends Definition<${defaultClassName}.${buildModelPublicTypeClassName}> {
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
    }

    static class ProjectFeatureDefinitionAbstractClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        @Override
        protected String getClassContent() {
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
                public abstract class ${defaultClassName} implements ${Definition.class.simpleName}<${defaultClassName}.FeatureModel> {
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

        String getMaybeInjectedServiceDeclaration() {
            return hasInjectedServices ? """
                @Inject
                abstract ObjectFactory getObjects();
            """ : ""
        }

        String getMaybeNestedInjectedServiceDeclaration() {
            return hasNestedInjectedServices ? """
                @Inject
                abstract ObjectFactory getObjects();
            """ : ""
        }
    }

    static class ProjectFeatureDefinitionWithInjectableParentClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        String parentTypeClassName = "ParentFeatureDefinition"

        ProjectFeatureDefinitionWithInjectableParentClassBuilder() {
            // Add injected service to the parent
            withInjectedServices()
        }

        @Override
        protected String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.provider.Property;
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                @Restricted
                public interface ${defaultClassName} extends ${parentTypeClassName} {
                    @Restricted
                    Property<String> getNonPublicProperty();
                }
            """
        }

        private String getPublicTypeContent() {
            return getDefaultClassContent(parentTypeClassName)
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/${parentTypeClassName}.java") << getPublicTypeContent()
        }
    }
}
