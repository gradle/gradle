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

import org.gradle.features.internal.ProjectFeatureFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

class SchemaBuildingFailureReportingIntegrationTest extends AbstractIntegrationSpec implements ProjectFeatureFixture {
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

        receivedProblem(0).fqid == "scripts:dcl-schema:illegal-variance-in-parameterized-type-usage"
        receivedProblem(1).fqid == "scripts:dcl-schema:unsupported-map-factory"
        receivedProblem(2).fqid == "scripts:dcl-schema:unsupported-map-factory"
    }

    def 'unsafe non-interface type in safe definition is reported'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "interface Fizz {",
            "abstract class Fizz {"
        )
        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getBuzz();",
            "abstract Property<String> getBuzz();"
        )

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: non-interface type\n" +
            "      in schema type 'org.gradle.test.FeatureDefinition.Fizz'\n" +
            "      in safe feature definition of 'feature' (plugin 'com.example.test-software-ecosystem')"
        )

        receivedProblem(0).fqid == "scripts:dcl-schema:unsafe-non-interface-type"
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

        receivedProblem(0).fqid == "scripts:dcl-schema:unsafe-because-has-hidden-members"
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

        receivedProblem(0).fqid == "scripts:dcl-schema:unsafe-java-bean-property"
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

        receivedProblem(0).fqid == "scripts:dcl-schema:unsafe-non-abstract-member"
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

        receivedProblem(0).fqid == "scripts:dcl-schema:unsafe-non-pure-function"
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
        PluginBuilder pluginBuilder = withMultipleProjectFeaturePlugins()
        pluginBuilder.prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        pluginBuilder.file("src/main/java/org/gradle/test/SharedType.java") << """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition;

            public interface SharedType {
                Property<String> getValue();

                @HiddenInDefinition
                Property<String> getHiddenProp();
            }
        """

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "@org.gradle.api.tasks.Nested\n" +
                "SharedType getShared();"
        )

        file("plugins/src/main/java/org/gradle/test/AnotherFeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\n" +
                "@org.gradle.api.tasks.Nested\n" +
                "SharedType getShared();"
        )

        expect:
        fails().assertHasErrorOutput(
            "Unsafe declaration in safe definition: hidden member 'getHiddenProp'\n" +
            "      in schema type 'org.gradle.test.SharedType'\n" +
            "      in safe feature definitions of 'feature', 'anotherFeature' (plugin 'com.example.test-software-ecosystem')"
        )

        receivedProblem(0).fqid == "scripts:dcl-schema:unsafe-because-has-hidden-members"
    }
}
