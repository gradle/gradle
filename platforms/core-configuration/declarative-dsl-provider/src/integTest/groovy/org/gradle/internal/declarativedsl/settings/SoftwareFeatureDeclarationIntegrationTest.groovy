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

import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.test.fixtures.plugin.PluginBuilder

@PolyglotDslTest
class SoftwareFeatureDeclarationIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture, PolyglotTestFixture {

    def 'can declare and configure a custom software feature from included build'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareTypePlugins()
        pluginBuilder.addPluginId("com.example.test-software-feature-impl", "SoftwareFeatureImplPlugin")
        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareFeatureImplPlugin.java") << softwareFeaturePluginContents
        pluginBuilder.file("src/main/java/org/gradle/test/FeatureDefinition.java") << softwareFeatureDslModelContents
        pluginBuilder.file("src/main/java/org/gradle/test/FeatureModel.java") << softwareFeatureBuildModelContents
        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareTypeRegistrationPlugin.java").text = getSettingsPluginThatRegistersSoftwareType(["SoftwareTypeImplPlugin", "SoftwareFeatureImplPlugin"])
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printTestSoftwareFeatureConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    def "can declare and configure a custom software feature in Kotlin"() {
        PluginBuilder pluginBuilder = withSoftwareTypePlugins()
        pluginBuilder.prepareToExecute()

        def kotlinPluginDir = file("kotlinPlugins").createDir()
        kotlinPluginDir.file("settings.gradle.kts").createFile() << """
            includeBuild("../plugins")
        """
        kotlinPluginDir.file("build.gradle.kts") << kotlinPluginBuildFile
        kotlinPluginDir.file("src/main/kotlin/org/gradle/test/SoftwareFeatureRegistrationPlugin.kt") << kotlinSettingsPlugin
        kotlinPluginDir.file("src/main/kotlin/org/gradle/test/SoftwareFeatureImplPlugin.kt") << kotlinSoftwareFeaturePluginContents
        kotlinPluginDir.file("src/main/java/org/gradle/test/FeatureDefinition.java") << softwareFeatureDslModelContents
        kotlinPluginDir.file("src/main/java/org/gradle/test/FeatureModel.java") << softwareFeatureBuildModelContents

        settingsFile() << kotlinPluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printTestSoftwareFeatureConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    static String getSoftwareFeaturePluginContents() {
        // language=Java
        String content = """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.internal.plugins.BindsSoftwareFeature;
            import org.gradle.api.internal.plugins.SoftwareFeatureBinding;
            import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder;
            import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration;
            import org.gradle.api.internal.plugins.software.SoftwareFeature;
            import org.gradle.api.plugins.ExtensionAware;

            @BindsSoftwareFeature(SoftwareFeatureImplPlugin.Binding.class)
            public class SoftwareFeatureImplPlugin implements Plugin<Project> {

                static class Binding implements SoftwareFeatureBindingRegistration {
                    @Override public void configure(SoftwareFeatureBindingBuilder builder) {
                        builder.bind("feature", FeatureDefinition.class, TestSoftwareTypeExtension.class, FeatureModel.class,
                            (context, feature, parent, model) -> {
                                model.getText().set(feature.getText());
                                context.getProject().getTasks().register("printTestSoftwareFeatureConfiguration", task -> {
                                    task.doLast(t -> System.out.println("feature text = " + model.getText().get()));
                                });
                            }
                        );
                    }
                }

                @Override
                public void apply(Project project) {

                }
            }
        """
        return content
    }

    static String getKotlinSoftwareFeaturePluginContents() {
        // language=kotlin
        String content = """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.Task
            import org.gradle.api.internal.plugins.BindsSoftwareFeature
            import org.gradle.api.internal.plugins.SoftwareFeatureBinding
            import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder
            import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration
            import org.gradle.api.internal.plugins.software.SoftwareFeature
            import org.gradle.api.plugins.ExtensionAware
            import org.gradle.api.internal.plugins.bind
            import org.gradle.test.TestSoftwareTypeExtension

            @BindsSoftwareFeature(SoftwareFeatureImplPlugin.Binding::class)
            class SoftwareFeatureImplPlugin : Plugin<Project> {

                class Binding : SoftwareFeatureBindingRegistration {
                    override fun configure(builder: SoftwareFeatureBindingBuilder) {
                        builder.bind<FeatureDefinition, TestSoftwareTypeExtension, FeatureModel>("feature") { feature, parent, model ->
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

    static String getKotlinSettingsPlugin() {
        //language=kotlin
        String content = """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            import ${RegistersSoftwareTypes.class.name}

            @RegistersSoftwareTypes(org.gradle.test.SoftwareFeatureImplPlugin::class)
            class SoftwareFeatureRegistrationPlugin : Plugin<Settings> {
                override fun apply(settings: Settings) {
                }
            }

        """
        return content
    }

    static String getSoftwareFeatureDslModelContents() {
        // language=Java
        String content = """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface FeatureDefinition {
                @Restricted
                Property<String> getText();
            }
        """
        return content
    }

    static String getSoftwareFeatureBuildModelContents() {
        // language=Java
        String content = """
            package org.gradle.test;

            import org.gradle.api.provider.Property;

            public interface FeatureModel {
                Property<String> getText();
            }
        """
        return content
    }

    static String getPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
        """
    }

    static String getKotlinPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("kotlinPlugins")
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
                id("org.example.test-software-feature-ecosystem")
            }
        """
    }

    static String getKotlinPluginBuildFile() {
        return """
            plugins {
                // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
                `java-gradle-plugin`

                // Apply the Kotlin JVM plugin to add support for Kotlin.
                id("org.jetbrains.kotlin.jvm") version("2.1.0")
            }

            repositories {
                mavenCentral()
            }

            gradlePlugin {
                val feature by plugins.creating {
                    id = "org.example.test-software-feature"
                    implementationClass = "org.gradle.test.SoftwareFeatureImplPlugin"
                }
                val ecosystem by plugins.creating {
                    id = "org.example.test-software-feature-ecosystem"
                    implementationClass = "org.gradle.test.SoftwareFeatureRegistrationPlugin"
                }
            }

            dependencies {
                implementation("org.gradle.test:plugins:1.0")
            }
        """
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareFeature() {
        return """
            testSoftwareType {
                id = "test"

                foo {
                    bar = "baz"
                }

                feature {
                    text = "foo"
                }
            }
        """
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("""id = test\nbar = baz""")
    }
}
