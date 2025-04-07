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

    static String getSoftwareFeaturePluginContents() {
        // language=Java
        String content = """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.internal.plugins.SoftwareFeatureDslBinding;
            import org.gradle.api.internal.plugins.software.SoftwareFeature;
            import org.gradle.api.plugins.ExtensionAware;

            import static org.gradle.api.internal.plugins.SoftwareFeatureDslBinding.softwareFeature;

            public class SoftwareFeatureImplPlugin implements Plugin<Project> {
                @SoftwareFeature
                public static SoftwareFeatureDslBinding binding = softwareFeature(builder -> builder
                        .bind("feature", FeatureDefinition.class, TestSoftwareTypeExtension.class, FeatureModel.class,
                            (context, feature, parent, model) -> {
                                model.getText().set(feature.getText());
                                context.getProject().getTasks().register("printTestSoftwareFeatureConfiguration", task -> {
                                    task.doLast(t -> {
                                        System.out.println("feature text = " + model.getText().get());
                                    });
                                });
                            }
                    )
                );

                @Override
                public void apply(Project project) {

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
