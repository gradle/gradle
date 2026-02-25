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
}
