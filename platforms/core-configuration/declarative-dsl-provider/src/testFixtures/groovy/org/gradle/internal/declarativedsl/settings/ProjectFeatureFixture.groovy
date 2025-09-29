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
import org.gradle.api.internal.plugins.ProjectFeatureBindingRegistration
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.api.internal.plugins.BindsProjectFeature
import org.gradle.api.internal.plugins.software.RegistersProjectFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes

trait ProjectFeatureFixture extends ProjectTypeFixture {
    PluginBuilder withProjectFeaturePlugins(ProjectTypeDefinitionClassBuilder projectTypeDefinition, ProjectTypePluginClassBuilder projectType, ProjectFeatureDefinitionClassBuilder projectFeatureDefinition, ProjectFeaturePluginClassBuilder projectFeature, SettingsPluginClassBuilder settingsBuilder) {
        PluginBuilder pluginBuilder = withProjectTypePlugins(
            projectTypeDefinition,
            projectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.test-software-feature-impl", projectFeature.projectFeaturePluginClassName)
        projectFeature.build(pluginBuilder)
        projectFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withMultipleProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .implementationTypeClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder()
            .definitionImplementationType(anotherFeatureDefinition.implementationTypeClassName)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
            .name("anotherFeature")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)

        def pluginBuilder = withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)

        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectFeatureDefinitionThatHasPublicAndImplementationTypes() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithPublicTypeClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder()
            .definitionPublicType(projectFeatureDefinition.publicTypeClassName)
            .definitionImplementationType(projectFeatureDefinition.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeaturePluginThatDoesNotExposeProjectFeatures() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new NotAProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
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
        return withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
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
        return withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
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
            .bindingTypeClassName("org.gradle.test." + projectTypeDefinition.implementationTypeClassName + ".Foo")
            .buildModelPublicTypeClassName(projectFeatureDefinition.buildModelFullClassName)
            .buildModelImplementationTypeClassName(projectFeatureDefinition.buildModelFullImplementationClassName)
            .applyActionExtraStatements("""
                model.getText().set(context.getProject().provider(() -> feature.getText().get() + " " + context.getBuildModel(parent).getBarProcessed().get()));
            """.stripIndent())

        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }


    PluginBuilder withKotlinProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new KotlinProjectFeaturePluginClassBuilder()
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeaturePlugins(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
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
                import ${ProjectFeatureBindingRegistration.class.name};

                @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding.class)
                public class ${projectFeaturePluginClassName} implements Plugin<Project> {

                    static class Binding implements ${ProjectFeatureBindingRegistration.class.simpleName} {
                        @Override public void register(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
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
                            ${maybeDeclareBuildModelImplementationType()};
                        }
                    }

                    @Override
                    public void apply(Project project) {

                    }
                }
            """
        }

        String maybeDeclareDefinitionImplementationType() {
            return (definitionPublicTypeClassName && definitionPublicTypeClassName != definitionImplementationTypeClassName) ? ".withDefinitionImplementationType(${definitionImplementationTypeClassName}.class);" : ""
        }

        String maybeDeclareBuildModelImplementationType() {
            return (buildModelPublicTypeClassName && buildModelPublicTypeClassName != buildModelImplementationTypeClassName) ? ".withBuildModelImplementationType(${buildModelImplementationTypeClassName}.class);" : ""
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
                import ${ProjectFeatureBindingRegistration.class.name}
                import org.gradle.api.internal.plugins.features.dsl.bindProjectFeatureToDefinition
                import org.gradle.test.${bindingTypeClassName}

                @${BindsProjectFeature.class.simpleName}(${projectFeaturePluginClassName}.Binding::class)
                class ${projectFeaturePluginClassName} : Plugin<Project> {

                    class Binding : ${ProjectFeatureBindingRegistration.class.simpleName} {
                        override fun register(builder: ${ProjectFeatureBindingBuilder.class.simpleName}) {
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
        String implementationTypeClassName = "FeatureDefinition"

        ProjectFeatureDefinitionClassBuilder implementationTypeClassName(String className) {
            this.implementationTypeClassName = className
            return this
        }

        String getBuildModelFullClassName() {
            return "${implementationTypeClassName}.FeatureModel"
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${implementationTypeClassName}.java") << getClassContent()
        }

        protected String getClassContent() {
            return """
                package org.gradle.test;

                import ${Definition.class.name};
                import ${BuildModel.class.name};
                import org.gradle.api.provider.Property;
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                @Restricted
                public interface ${implementationTypeClassName} extends ${Definition.class.simpleName}<${implementationTypeClassName}.FeatureModel> {
                    @Restricted
                    Property<String> getText();

                    interface FeatureModel extends BuildModel {
                        Property<String> getText();
                    }
                }
            """
        }
    }

    static class ProjectFeatureDefinitionWithPublicTypeClassBuilder extends ProjectFeatureDefinitionClassBuilder {
        String publicTypeClassName = "FeatureDefinition"

        ProjectFeatureDefinitionWithPublicTypeClassBuilder(String publicTypeClassName) {
            this.implementationTypeClassName = publicTypeClassName + "Impl"
        }

        ProjectFeatureDefinitionWithPublicTypeClassBuilder withPublicType(String className) {
            this.publicTypeClassName = className
            return this
        }

        @Override
        protected String getClassContent() {
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

        private String getPublicTypeContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.provider.Property;
                import org.gradle.declarative.dsl.model.annotations.Restricted;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.FeatureModel> {
                    @Restricted
                    Property<String> getText();

                    interface FeatureModel extends BuildModel {
                        Property<String> getText();
                    }
                }
            """
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
            return "${implementationTypeClassName}.${buildModelPublicTypeClassName}"
        }

        String getBuildModelFullImplementationClassName() {
            return "${implementationTypeClassName}.${buildModelImplementationTypeClassName}"
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
                public interface ${implementationTypeClassName} extends Definition<${implementationTypeClassName}.${buildModelPublicTypeClassName}> {
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
}
