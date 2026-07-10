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

package org.gradle.internal.declarativedsl.schema

import org.gradle.features.internal.TestScenarioFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

import static org.gradle.features.internal.builders.TypeShape.ABSTRACT_CLASS

class SchemaBuildingFailureReportingIntegrationTest extends AbstractIntegrationSpec implements TestScenarioFixture {
    def setup() {
        enableProblemsApiCheck()
    }

    def 'schema building failures are reported in the build output'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        def settings = file("settings.gradle.dcl")
        settings << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "java.util.Map<String, String> myMap();\n" +
                "java.util.Map<String, String> anotherMap();\n" +
                "Property<? extends CharSequence> getWildcard();"
        )

        expect:
        fails().assertHasErrorOutput(
            """
            |Failed to interpret the declarative DSL file '${settings.absolutePath}':
            |  Failures in building the schema:
            |    Illegal type 'kotlin.collections.Map<kotlin.String, kotlin.String>': functions returning Map types are not supported
            |      in return value type 'kotlin.collections.Map<kotlin.String, kotlin.String>'
            |      in member 'fun org.gradle.test.FeatureDefinition.anotherMap(): kotlin.collections.(Mutable)Map<kotlin.String!, kotlin.String!>!'
            |      in class 'org.gradle.test.FeatureDefinition'
            |    Illegal 'OUT' variance
            |      in type argument 'out kotlin.CharSequence'
            |      in return value type 'org.gradle.api.provider.Property<out kotlin.CharSequence>'
            |      in member 'fun org.gradle.test.FeatureDefinition.getWildcard(): org.gradle.api.provider.Property<out kotlin.CharSequence!>!'
            |      in class 'org.gradle.test.FeatureDefinition'
            |    Illegal type 'kotlin.collections.Map<kotlin.String, kotlin.String>': functions returning Map types are not supported
            |      in return value type 'kotlin.collections.Map<kotlin.String, kotlin.String>'
            |      in member 'fun org.gradle.test.FeatureDefinition.myMap(): kotlin.collections.(Mutable)Map<kotlin.String!, kotlin.String!>!'
            |      in class 'org.gradle.test.FeatureDefinition'
            """.stripMargin("|").strip()
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:illegal-variance-in-parameterized-type-usage"
            details == "Illegal 'OUT' variance\n" +
                "  in type argument 'out kotlin.CharSequence'\n" +
                "  in return value type 'org.gradle.api.provider.Property<out kotlin.CharSequence>'\n" +
                "  in member 'fun org.gradle.test.FeatureDefinition.getWildcard(): org.gradle.api.provider.Property<out kotlin.CharSequence!>!'\n" +
                "  in class 'org.gradle.test.FeatureDefinition'"
            solutions == [
                "Use invariant type arguments (with no wildcards or in/out-projections)",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == "scripts:dcl-schema:unsupported-map-factory"
            details == "Illegal type 'kotlin.collections.Map<kotlin.String, kotlin.String>': functions returning Map types are not supported\n" +
                "  in return value type 'kotlin.collections.Map<kotlin.String, kotlin.String>'\n" +
                "  in member 'fun org.gradle.test.FeatureDefinition.anotherMap(): kotlin.collections.(Mutable)Map<kotlin.String!, kotlin.String!>!'\n" +
                "  in class 'org.gradle.test.FeatureDefinition'"
            solutions == [
                "If regular Map values (mapOf) don't work for this use case, use a custom type instead of Map.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == "scripts:dcl-schema:unsupported-map-factory"
            details == "Illegal type 'kotlin.collections.Map<kotlin.String, kotlin.String>': functions returning Map types are not supported\n" +
                "  in return value type 'kotlin.collections.Map<kotlin.String, kotlin.String>'\n" +
                "  in member 'fun org.gradle.test.FeatureDefinition.myMap(): kotlin.collections.(Mutable)Map<kotlin.String!, kotlin.String!>!'\n" +
                "  in class 'org.gradle.test.FeatureDefinition'"
            solutions == [
                "If regular Map values (mapOf) don't work for this use case, use a custom type instead of Map.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }

    def 'unsafe non-interface type in safe definition is reported'() {
        given:
        PluginBuilder pluginBuilder = testScenario {
            def type = projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        implementsDefinition("FooBuildModel") {
                            property "barProcessed", String
                        }
                        property "bar", String
                    }
                }
            }
            projectFeature("feature") {
                definition {
                    property "text", String
                    buildModel {
                        property "text", String
                    }
                    nested("fizz", "Fizz") {
                        shape ABSTRACT_CLASS
                        property ("buzz", String)
                    }
                }
                plugin {
                    bindsFeatureTo(type)
                }
            }
        }
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: non-interface type\n" +
            "      in schema type 'org.gradle.test.FeatureDefinition.Fizz'\n" +
            "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-non-interface-type"
            details == "Unsafe declaration in safe definition: non-interface type\n" +
                "  in schema type 'org.gradle.test.FeatureDefinition.Fizz'\n" +
                "  in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Make the type safe by making it an interface.",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }

    def 'unsafe hidden member in safe definition is reported'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "@org.gradle.declarative.dsl.model.annotations.HiddenInDefinition\n" +
                "Property<String> getHiddenProp();"
        )

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: hidden member 'getHiddenProp'\n" +
            "      in schema type 'org.gradle.test.FeatureDefinition'\n" +
            "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-because-has-hidden-members"
            details == "Unsafe declaration in safe definition: hidden member 'getHiddenProp'\n" +
                "  in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "  in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Remove the hidden members.",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }

    def 'unsafe non-public member in safe definition is reported'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\nprivate String nonPublicMember() { return \"\"; }"
        )

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: non-public member 'nonPublicMember'\n" +
                "      in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-because-has-non-public-members"
            details == "Unsafe declaration in safe definition: non-public member 'nonPublicMember'\n" +
                "  in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "  in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Remove the non-public members from the safe definition.",
                "Make the members public.",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }


    def 'unsafe java bean property in safe definition is reported'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "String getPlainText();\n" +
                "void setPlainText(String value);"
        )

        expect:
        fails().assertHasErrorOutput("Unsafe declaration in safe definition: unsafe property\n" +
            "      in schema property 'plainText: String'\n" +
            "      in schema type 'org.gradle.test.FeatureDefinition'\n" +
            "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-java-bean-property"
            details == "Unsafe declaration in safe definition: unsafe property\n" +
                "  in schema property 'plainText: String'\n" +
                "  in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "  in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Make the property safe by using Gradle Property API.",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }

    def 'unsafe non-abstract member in safe definition is reported'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "default String getDefaultValue() { return \"default\"; }"
        )

        expect:
        fails().assertHasErrorOutput("Unsafe declaration in safe definition: non-abstract member\n" +
            "      in schema property 'defaultValue: String'\n" +
            "      in schema type 'org.gradle.test.FeatureDefinition'\n" +
            "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-non-abstract-member"
            details == "Unsafe declaration in safe definition: non-abstract member\n" +
                "  in schema property 'defaultValue: String'\n" +
                "  in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "  in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Make the member safe by removing the implementation (making it abstract).",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }

    def 'unsafe non-pure function in safe definition is reported'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "@org.gradle.declarative.dsl.model.annotations.Adding\n" +
                "Fizz addFizz();"
        )

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: function relying on side effects or custom implementation\n" +
            "      in schema function 'addFizz(): Fizz'\n" +
            "      in schema type 'org.gradle.test.FeatureDefinition'\n" +
            "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-non-pure-function"
            details == "Unsafe declaration in safe definition: function relying on side effects or custom implementation\n" +
                "  in schema function 'addFizz(): Fizz'\n" +
                "  in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "  in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Use read-only properties to expose nested models.",
                "Use NamedDomainObjectContainer or collection properties to model multi-element containers.",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }

    def 'unsafe inject property is reported'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "@javax.inject.Inject\n" +
                "Fizz getInjectedFizz();"
        )

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: injected service property\n" +
                "      in schema property 'injectedFizz: Fizz'\n" +
                "      in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-inject-property"
            details == "Unsafe declaration in safe definition: injected service property\n" +
                "  in schema property 'injectedFizz: Fizz'\n" +
                "  in schema type 'org.gradle.test.FeatureDefinition'\n" +
                "  in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Remove the @Inject annotation.",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }


    def 'unsafe declaration in type used by two safe definitions reports both features'() {
        given:
        PluginBuilder pluginBuilder = testScenario {
            def type = projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        implementsDefinition("FooBuildModel") {
                            property "barProcessed", String
                        }
                        property "bar", String
                    }
                }
            }
            def shared = sharedType("Shared") {
                property("value", String)
                property("hiddenProp", String) {
                    annotations "@org.gradle.declarative.dsl.model.annotations.HiddenInDefinition"
                }
            }
            projectFeature("feature") {
                definition {
                    property "text", String
                    sharedProperty "shared", shared
                    buildModel {
                        property "text", String
                    }
                    nested("fizz", "Fizz") {
                        property "buzz", String
                    }
                }
                plugin {
                    bindsFeatureTo(type)
                }
            }
            projectFeature("anotherFeature") {
                definition {
                    property "text", String
                    sharedProperty("shared", shared)
                    buildModel {
                        property "text", String
                    }
                    nested("fizz", "Fizz") {
                        property "buzz", String
                    }
                }
                plugin {
                    bindsFeatureTo(type)
                }
            }
        }
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: hidden member 'getHiddenProp'\n" +
            "      in schema type 'org.gradle.test.Shared'\n" +
            "      in safe feature definitions of 'feature', 'anotherFeature' (plugin 'com.example.test-software-ecosystem')"
        )

        verifyAll(receivedProblem(0)) {
            fqid == "scripts:dcl-schema:unsafe-because-has-hidden-members"
            details == "Unsafe declaration in safe definition: hidden member 'getHiddenProp'\n" +
                "  in schema type 'org.gradle.test.Shared'\n" +
                "  in safe feature definitions of 'feature', 'anotherFeature' (plugin 'com.example.test-software-ecosystem')"
            solutions == [
                "Remove the hidden members.",
                "Declare the corresponding features as having unsafe definitions.",
                "Remove the violating declaration or make it non-public in an unsafe definition.",
                "In an unsafe definition, annotate the violating declaration as @HiddenInDefinition to exclude it from the Declarative schema.",
            ]
        }
    }

    private PluginBuilder withProjectFeature() {
        return testScenario {
            def type = projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        implementsDefinition("FooBuildModel") {
                            property "barProcessed", String
                        }
                        property "bar", String
                    }
                }
            }
            projectFeature("feature") {
                definition {
                    property "text", String
                    buildModel {
                        property "text", String
                    }
                    nested("fizz", "Fizz") {
                        property "buzz", String
                    }
                }
                plugin {
                    bindsFeatureTo(type)
                }
            }
        }
    }

}
