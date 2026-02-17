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
    def 'schema building failures are reported in the build output'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        def settings = file("settings.gradle.dcl")
        settings << pluginsFromIncludedBuild

        file("plugins/src/main/java/org/gradle/test/FeatureDefinition.java").replace(
            "Property<String> getText();",
            "Property<String> getText();\njava.util.Map<String, String> myMap();\njava.util.Map<String, String> anotherMap();"
        )

        expect:
        fails().assertHasErrorOutput("""
            |Failed to interpret the declarative DSL file '${settings.absolutePath}':
            |  Failures in building the schema:
            |    Illegal type 'kotlin.collections.Map<kotlin.String, kotlin.String>': functions returning Map types are not supported
            |      in return value type 'kotlin.collections.Map<kotlin.String, kotlin.String>'
            |      in member 'fun org.gradle.test.FeatureDefinition.myMap(): kotlin.collections.(Mutable)Map<kotlin.String!, kotlin.String!>!'
            |      in class 'org.gradle.test.FeatureDefinition'
            |    Illegal type 'kotlin.collections.Map<kotlin.String, kotlin.String>': functions returning Map types are not supported
            |      in return value type 'kotlin.collections.Map<kotlin.String, kotlin.String>'
            |      in member 'fun org.gradle.test.FeatureDefinition.anotherMap(): kotlin.collections.(Mutable)Map<kotlin.String!, kotlin.String!>!'
            |      in class 'org.gradle.test.FeatureDefinition'
        """.stripMargin("|").strip())
    }
}
