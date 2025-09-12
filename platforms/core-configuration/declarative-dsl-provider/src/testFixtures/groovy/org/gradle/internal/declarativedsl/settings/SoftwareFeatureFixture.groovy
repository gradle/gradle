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
import org.gradle.api.internal.plugins.HasBuildModel
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder
import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.api.internal.plugins.BindsSoftwareFeature
import org.gradle.api.internal.plugins.software.RegistersSoftwareFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes

trait SoftwareFeatureFixture extends SoftwareTypeFixture {
    PluginBuilder withSoftwareFeaturePlugins(SoftwareTypeDefinitionClassBuilder softwareTypeDefinition, SoftwareTypePluginClassBuilder softwareType, SoftwareFeatureDefinitionClassBuilder softwareFeatureDefinition, SoftwareFeaturePluginClassBuilder softwareFeature, SettingsPluginClassBuilder settingsBuilder) {
        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            softwareTypeDefinition,
            softwareType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.test-software-feature-impl", softwareFeature.softwareFeaturePluginClassName)
        softwareFeature.build(pluginBuilder)
        softwareFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withSoftwareFeaturePlugins() {
        def softwareTypeDefinition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def softwareFeatureDefinition = new SoftwareFeatureDefinitionClassBuilder()
        def softwareFeature = new SoftwareFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)
            .registersSoftwareFeature(softwareFeature.softwareFeaturePluginClassName)
        return withSoftwareFeaturePlugins(softwareTypeDefinition, softwareType, softwareFeatureDefinition, softwareFeature, settingsBuilder)
    }

    PluginBuilder withMultipleSoftwareFeaturePlugins() {
        def softwareTypeDefinition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def softwareFeatureDefinition = new SoftwareFeatureDefinitionClassBuilder()
        def softwareFeature = new SoftwareFeaturePluginClassBuilder()
        def anotherFeatureDefinition = new SoftwareFeatureDefinitionClassBuilder()
            .implementationTypeClassName("AnotherFeatureDefinition")
        def anotherSoftwareFeature = new SoftwareFeaturePluginClassBuilder()
            .definitionImplementationType(anotherFeatureDefinition.implementationTypeClassName)
            .softwareFeaturePluginClassName("AnotherSoftwareFeatureImplPlugin")
            .name("anotherFeature")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)
            .registersSoftwareFeature(softwareFeature.softwareFeaturePluginClassName)
            .registersSoftwareFeature(anotherSoftwareFeature.softwareFeaturePluginClassName)

        def pluginBuilder = withSoftwareFeaturePlugins(softwareTypeDefinition, softwareType, softwareFeatureDefinition, softwareFeature, settingsBuilder)

        anotherSoftwareFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withSoftwareFeatureDefinitionThatHasPublicAndImplementationTypes() {
        def softwareTypeDefinition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def softwareFeatureDefinition = new SoftwareFeatureDefinitionWithPublicTypeClassBuilder()
        def softwareFeature = new SoftwareFeaturePluginClassBuilder()
            .definitionPublicType(softwareFeatureDefinition.publicTypeClassName)
            .definitionImplementationType(softwareFeatureDefinition.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)
            .registersSoftwareFeature(softwareFeature.softwareFeaturePluginClassName)
        return withSoftwareFeaturePlugins(softwareTypeDefinition, softwareType, softwareFeatureDefinition, softwareFeature, settingsBuilder)
    }

    PluginBuilder withSoftwareFeaturePluginThatDoesNotExposeSoftwareFeatures() {
        def softwareTypeDefinition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def softwareFeatureDefinition = new SoftwareFeatureDefinitionClassBuilder()
        def softwareFeature = new NotASoftwareFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)
            .registersSoftwareFeature(softwareFeature.softwareFeaturePluginClassName)
        return withSoftwareFeaturePlugins(softwareTypeDefinition, softwareType, softwareFeatureDefinition, softwareFeature, settingsBuilder)
    }

    PluginBuilder withSoftwareFeatureThatBindsToBuildModel() {
        def softwareTypeDefinition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def softwareFeatureDefinition = new SoftwareFeatureDefinitionClassBuilder()
        def softwareFeature = new SoftwareFeaturePluginClassBuilder()
            .bindToBuildModel()
            .bindingTypeClassName(softwareTypeDefinition.buildModelClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)
            .registersSoftwareFeature(softwareFeature.softwareFeaturePluginClassName)
        return withSoftwareFeaturePlugins(softwareTypeDefinition, softwareType, softwareFeatureDefinition, softwareFeature, settingsBuilder)
    }

    PluginBuilder withSoftwareFeatureBuildModelThatHasPublicAndImplementationTypes() {
        def softwareTypeDefinition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def softwareFeatureDefinition = new SoftwareFeatureDefinitionWithPublicBuildModelTypeClassBuilder()
        def softwareFeature = new SoftwareFeaturePluginClassBuilder()
            .buildModelPublicTypeClassName(softwareFeatureDefinition.buildModelFullClassName)
            .buildModelImplementationTypeClassName(softwareFeatureDefinition.buildModelFullImplementationClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)
            .registersSoftwareFeature(softwareFeature.softwareFeaturePluginClassName)
        return withSoftwareFeaturePlugins(softwareTypeDefinition, softwareType, softwareFeatureDefinition, softwareFeature, settingsBuilder)
    }

    PluginBuilder withKotlinSoftwareFeaturePlugins() {
        def softwareTypeDefinition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def softwareFeatureDefinition = new SoftwareFeatureDefinitionClassBuilder()
        def softwareFeature = new KotlinSoftwareFeaturePluginClassBuilder()
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)
            .registersSoftwareFeature(softwareFeature.softwareFeaturePluginClassName)
        return withSoftwareFeaturePlugins(softwareTypeDefinition, softwareType, softwareFeatureDefinition, softwareFeature, settingsBuilder)
    }

    static class SoftwareFeaturePluginClassBuilder {
        String definitionImplementationTypeClassName = "FeatureDefinition"
        String definitionPublicTypeClassName = null
        String buildModelImplementationTypeClassName = null
        String buildModelPublicTypeClassName = null
        String softwareFeaturePluginClassName = "SoftwareFeatureImplPlugin"
        String bindingTypeClassName = "TestSoftwareTypeExtension"
        String bindingMethodName = "bindSoftwareFeatureToDefinition"
        String name = "feature"

        SoftwareFeaturePluginClassBuilder definitionImplementationType(String className) {
            this.definitionImplementationTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder definitionPublicType(String className) {
            this.definitionPublicTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder buildModelImplementationTypeClassName(String className) {
            this.buildModelImplementationTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder buildModelPublicTypeClassName(String className) {
            this.buildModelPublicTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder softwareFeaturePluginClassName(String className) {
            this.softwareFeaturePluginClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder name(String name) {
            this.name = name
            return this
        }

        SoftwareFeaturePluginClassBuilder bindToDefinition() {
            this.bindingMethodName = "bindSoftwareFeatureToDefinition"
            return this
        }

        SoftwareFeaturePluginClassBuilder bindToBuildModel() {
            this.bindingMethodName = "bindSoftwareFeatureToBuildModel"
            return this
        }

        SoftwareFeaturePluginClassBuilder bindingTypeClassName(String className) {
            this.bindingTypeClassName = className
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${softwareFeaturePluginClassName}.java") << getClassContent()
        }

        protected String getClassContent() {
            def dslTypeClassName = definitionPublicTypeClassName ?: definitionImplementationTypeClassName
            return """
                package org.gradle.test;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import ${BindsSoftwareFeature.class.name};
                import ${SoftwareFeatureBindingBuilder.class.name};
                import static ${SoftwareFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
                import ${SoftwareFeatureBindingRegistration.class.name};

                @BindsSoftwareFeature(${softwareFeaturePluginClassName}.Binding.class)
                public class ${softwareFeaturePluginClassName} implements Plugin<Project> {

                    static class Binding implements SoftwareFeatureBindingRegistration {
                        @Override public void register(SoftwareFeatureBindingBuilder builder) {
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

    static class NotASoftwareFeaturePluginClassBuilder extends SoftwareFeaturePluginClassBuilder {
        NotASoftwareFeaturePluginClassBuilder() {
            this.softwareFeaturePluginClassName = "NotASoftwareFeaturePlugin"
        }

        @Override
        protected String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class ${softwareFeaturePluginClassName} implements Plugin<Project> {
                    @Override
                    public void apply(Project project) {

                    }
                }
            """
        }
    }

    static class KotlinSoftwareFeaturePluginClassBuilder extends SoftwareFeaturePluginClassBuilder {
        @Override
        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/kotlin/org/gradle/test/${softwareFeaturePluginClassName}.kt") << getClassContent()
        }

        @Override
        protected String getClassContent() {
            def dslTypeClassName = definitionPublicTypeClassName ?: definitionImplementationTypeClassName
            String content = """
                package org.gradle.test

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.Task
                import ${BindsSoftwareFeature.class.name}
                import ${SoftwareFeatureBindingBuilder.class.name}
                import ${SoftwareFeatureBindingRegistration.class.name}
                import org.gradle.api.internal.plugins.features.dsl.bindSoftwareFeatureToDefinition
                import org.gradle.test.TestSoftwareTypeExtension

                @BindsSoftwareFeature(${softwareFeaturePluginClassName}.Binding::class)
                class ${softwareFeaturePluginClassName} : Plugin<Project> {

                    class Binding : SoftwareFeatureBindingRegistration {
                        override fun register(builder: SoftwareFeatureBindingBuilder) {
                            builder.bindSoftwareFeatureToDefinition("${name}", ${dslTypeClassName}::class, ${bindingTypeClassName}::class) { feature, model, parent  ->
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

    static class KotlinSettingsPluginClassBuilder extends SoftwareTypeFixture.SettingsPluginClassBuilder {

        KotlinSettingsPluginClassBuilder() {
            this.pluginClassName = "SoftwareFeatureRegistrationPlugin"
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/kotlin/org/gradle/test/${pluginClassName}.kt") << """
                package org.gradle.test

                import org.gradle.api.Plugin
                import org.gradle.api.initialization.Settings
                import ${RegistersSoftwareFeatures.class.name}
                import ${RegistersSoftwareTypes.class.name}

                @RegistersSoftwareTypes(${softwareTypePluginClasses.collect { it + "::class" }.join(", ")})
                @RegistersSoftwareFeatures(${softwareFeaturePluginClasses.collect { it + "::class" }.join(", ")})
                class ${pluginClassName} : Plugin<Settings> {
                    override fun apply(settings: Settings) {
                    }
                }
            """
        }
    }

    static class SoftwareFeatureDefinitionClassBuilder {
        String implementationTypeClassName = "FeatureDefinition"

        SoftwareFeatureDefinitionClassBuilder implementationTypeClassName(String className) {
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

                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};
                import org.gradle.api.provider.Property;
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                @Restricted
                public interface ${implementationTypeClassName} extends HasBuildModel<${implementationTypeClassName}.FeatureModel> {
                    @Restricted
                    Property<String> getText();

                    interface FeatureModel extends BuildModel {
                        Property<String> getText();
                    }
                }
            """
        }
    }

    static class SoftwareFeatureDefinitionWithPublicTypeClassBuilder extends SoftwareFeatureDefinitionClassBuilder {
        String publicTypeClassName = "FeatureDefinition"

        SoftwareFeatureDefinitionWithPublicTypeClassBuilder(String publicTypeClassName) {
            this.implementationTypeClassName = publicTypeClassName + "Impl"
        }

        SoftwareFeatureDefinitionWithPublicTypeClassBuilder withPublicType(String className) {
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
                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${publicTypeClassName} extends HasBuildModel<${publicTypeClassName}.FeatureModel> {
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

    static class SoftwareFeatureDefinitionWithPublicBuildModelTypeClassBuilder extends SoftwareFeatureDefinitionClassBuilder {
        String buildModelPublicTypeClassName = "FeatureModel"
        String buildModelImplementationTypeClassName = "FeatureModelImpl"

        SoftwareFeatureDefinitionWithPublicBuildModelTypeClassBuilder buildModelPublicTypeClassName(String className) {
            this.buildModelPublicTypeClassName = className
            return this
        }

        SoftwareFeatureDefinitionWithPublicBuildModelTypeClassBuilder buildModelImplementationTypeClassName(String className) {
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
                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${implementationTypeClassName} extends HasBuildModel<${implementationTypeClassName}.${buildModelPublicTypeClassName}> {
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
