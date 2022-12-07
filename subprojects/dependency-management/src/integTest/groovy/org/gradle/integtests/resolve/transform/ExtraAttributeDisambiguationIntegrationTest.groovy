/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage

/**
 * A regression test which verifies consistent behavior after the addition of the {@code CompileView} attribute
 * and the associated {@code compileElements} configuration. Specifically tests behavior of the
 * {@code disambiguateWithExtraAttributes} step with {@code MultipleCandidateMatcher}.
 *
 * Each test in this class succeeds before the introduction of the new configuration, fails after introduction,
 * then succeeds again after applying the necessary changes to restore the initial behavior.
 */
class ExtraAttributeDisambiguationIntegrationTest extends AbstractIntegrationSpec {

    //region setup

    def setup() {
        // We define a base file which will consume another project, named "other".

        // By default, we consume the other project without any request attributes in order to best
        // stress test attribute disambiguation. If we requested more attributes, we run the chance
        // of finding a variant match before disambiguation, thus not testing the disambiguation process
        // at all. Or, our pre-disambiguation calculations would otherwise reduce the set of `compatible`
        // candidates, thus reducing the complexity of disambiguation. All steps run before disambiguation
        // can only reduce the number of `compatible` candidates, and thus `remaining` candidates,
        // therefore making disambiguation easier and less complex.
        buildFile("""
            plugins {
                id 'java-library'
            }

            configurations {
                resolvable {
                    canBeConsumed = false
                    canBeResolved = true
                }
            }

            dependencies {
                resolvable project(':other')
            }

            tasks.register("consumeConfiguration") {
                def files = configurations.resolvable
                dependsOn files
                doFirst {
                    println("Resolved: " + files*.name)
                }
            }
        """)

        settingsFile << "include 'other'"
        otherBuildFile("""
            plugins {
                id 'java-library'
            }
        """)
        file("other/src/main/java/com/example/Example.java") << """
            package com.example;
            public class Example {}
        """
    }

    def otherBuildFile(@GroovyBuildScriptLanguage String text) {
        file("other/build.gradle") << text
    }

    void assertConfigurationResolved(String name) {
        succeeds "consumeConfiguration"
        if (name == "runtimeElements") {
            outputContains("Resolved: [other.jar]")
        } else {
            outputContains("Resolved: [$name]")
        }
    }

    /**
     * Executes the given attributes code block against the root project's resolvable configuration.
     */
    def withRequestAttributes(String block) {
        buildFile << """
            configurations {
                resolvable {
                    attributes {
                        ${block}
                    }
                }
            }
        """
    }

    /**
     * Defines a new consumable configuration with a single file dependency of the same name of as the configuration.
     */
    def createConfiguration(String name, attributes = "") {
        otherBuildFile("""
            configurations {
                ${name} {
                    canBeConsumed = true
                    canBeResolved = false
                    attributes {
                        ${attributes}
                    }
                }
            }

            // Add a dummy file so when we resolve this configuration, we can tell it apart from the others
            dependencies {
                ${name} files("$name")
            }
        """)
    }

    //endregion

    def "Disambiguating on attribute defined after CompileView in precedence order succeeds"() {
        given:
        createConfiguration("another", """
            // Different value than runtimeElements -- not preferred according to jvm-ecosystem disambiguation rule.
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.SHADOWED))

            // Same as runtimeElements
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
        """)

        expect:
        assertConfigurationResolved("runtimeElements")
    }

    def "Disambiguating on attribute defined after CompileView in precedence order succeeds -- with non-empty request attributes"() {
        given:
        createConfiguration("another", """
            // We add the default TargetJvmEnvironment so this variant is preferred over runtimeElements and another2
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment, TargetJvmEnvironment.STANDARD_JVM))

            // Same as runtimeElements, except with bundling omitted
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
        """)
        createConfiguration("another2", """
            // Same as above but with non-default target environment. Used to ensure we have at two compatible values with target environment.
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment, TargetJvmEnvironment.ANDROID))
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
        """)
        // Whatever we request, we need to make sure two `CompileView` values remain in the `compatible` set.
        withRequestAttributes("""
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
        """)

        expect:
        assertConfigurationResolved("another")
    }

    def "Disambiguating on custom attribute succeeds when both variants have identical ordered attributes"() {
        given:
        createConfiguration("another", """
            attribute(Attribute.of("custom-attribute", String), "another-value")

            // Same as runtimeElements
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
        """)
        createConfiguration("another2", """
            attribute(Attribute.of("custom-attribute", String), "custom-value")
        """)
        otherBuildFile("""
            // When no `custom` attribute is requested, prefer `another-value`.
            abstract class CustomDisambiguationRule implements AttributeDisambiguationRule<String>, org.gradle.api.internal.ReusableAction {
                @Inject
                protected abstract ObjectFactory getObjectFactory()
                @Override
                void execute(MultipleCandidatesDetails<String> details) {
                    String consumerValue = details.getConsumerValue()
                    Set<String> candidateValues = details.getCandidateValues()
                    if (consumerValue == null) {
                        // Default to custom-value
                        if (candidateValues.contains("another-value")) {
                            details.closestMatch("another-value")
                        }
                        // Otherwise, use the selected value
                    } else if (candidateValues.contains(consumerValue)) {
                        details.closestMatch(consumerValue)
                    }
                }
            }
            dependencies.getAttributesSchema().attribute(Attribute.of("custom-attribute", String)).getDisambiguationRules().add(CustomDisambiguationRule.class)
        """)

        expect:
        assertConfigurationResolved("another")
    }

    def "Disambiguating on custom attribute succeeds when both variants have identical ordered attributes -- with non-empty request attributes"() {
        given:
        createConfiguration("another", """
            attribute(Attribute.of("custom-attribute", String), "another-value")

            // Same as runtimeElements
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
        """)
        createConfiguration("another2", """
            attribute(Attribute.of("custom-attribute", String), "custom-value")

            // Same as runtimeElements
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
        """)
        otherBuildFile("""
            // When no `custom` attribute is requested, prefer `another-value`.
            abstract class CustomDisambiguationRule implements AttributeDisambiguationRule<String>, org.gradle.api.internal.ReusableAction {
                @Inject
                protected abstract ObjectFactory getObjectFactory()
                @Override
                void execute(MultipleCandidatesDetails<String> details) {
                    String consumerValue = details.getConsumerValue()
                    Set<String> candidateValues = details.getCandidateValues()
                    if (consumerValue == null) {
                        // Default to custom-value
                        if (candidateValues.contains("another-value")) {
                            details.closestMatch("another-value")
                        }
                        // Otherwise, use the selected value
                    } else if (candidateValues.contains(consumerValue)) {
                        details.closestMatch(consumerValue)
                    }
                }
            }
            dependencies.getAttributesSchema().attribute(Attribute.of("custom-attribute", String)).getDisambiguationRules().add(CustomDisambiguationRule.class)
        """)
        withRequestAttributes("""
            // Same as runtimeElements
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
        """)

        expect:
        assertConfigurationResolved("another")
    }
}
