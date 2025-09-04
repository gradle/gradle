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
        String buildModelImplementationTypeClassName = "FeatureModel"
        String buildModelPublicTypeClassName = null
        String softwareFeaturePluginClassName = "SoftwareFeatureImplPlugin"
        String bindingTypeClassName = "TestSoftwareTypeExtension"
        String name = "feature"

        SoftwareFeaturePluginClassBuilder withDefinitionImplementationType(String className) {
            this.definitionImplementationTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder withDefinitionPublicType(String className) {
            this.definitionPublicTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder withBuildModelImplementationType(String className) {
            this.buildModelImplementationTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder withBuildModelPublicType(String className) {
            this.buildModelPublicTypeClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder withSoftwareFeaturePlugin(String className) {
            this.softwareFeaturePluginClassName = className
            return this
        }

        SoftwareFeaturePluginClassBuilder withName(String name) {
            this.name = name
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
                            builder.bindSoftwareFeatureToDefinition(
                                "${name}",
                                ${dslTypeClassName}.class,
                                ${bindingTypeClassName}.class,
                                (context, feature, model, parent) -> {
                                    System.out.println("Binding FeatureDefinition");
                                    model.getText().set(feature.getText());
                                    context.getProject().getTasks().register("printTestSoftwareFeatureConfiguration", task -> {
                                        task.doLast(t -> System.out.println("feature text = " + model.getText().get()));
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
                                println("Binding FeatureDefinition")
                                model.getText().set(feature.getText())
                                getProject().getTasks().register("printTestSoftwareFeatureConfiguration") { task: Task ->
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
        String className = "FeatureDefinition"

        SoftwareFeatureDefinitionClassBuilder className(String className) {
            this.className = className
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${className}.java") << getClassContent()
        }

        protected String getClassContent() {
            return """
                package org.gradle.test;

                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};
                import org.gradle.api.provider.Property;
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                @Restricted
                public interface FeatureDefinition extends HasBuildModel<FeatureDefinition.FeatureModel> {
                    @Restricted
                    Property<String> getText();

                    interface FeatureModel extends BuildModel {
                        Property<String> getText();
                    }
                }
            """
        }
    }
}
