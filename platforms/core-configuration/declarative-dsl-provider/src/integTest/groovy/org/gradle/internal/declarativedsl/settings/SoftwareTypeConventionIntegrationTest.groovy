/*
 * Copyright 2024 the original author or authors.
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

class SoftwareTypeConventionIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture {
    def "can configure build-level conventions for property objects in a software type (#testCase)"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.something") << getDeclarativeSettingsScriptThatSetsConventions(convention)

        file("build.gradle.something") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(buildConfiguration)

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains(expectedConfiguration)

        where:
        testCase                               | convention                           | buildConfiguration    | expectedConfiguration
        "id has convention and is set"         | setId("convention")                  | setId("test")         | """id = test\nbar = bar"""
        "id has convention, bar is set"        | setId("convention")                  | setFooBar("baz")      | """id = convention\nbar = baz"""
        "bar has convention and is set"        | setFooBar("convention")              | setFooBar("baz")      | """id = <no id>\nbar = baz"""
        "bar has convention, id is set"        | setFooBar("convention")              | setId("test")         | """id = test\nbar = convention"""
        "no conventions, id is set"            | ""                                   | setId("test")         | """id = test\nbar = bar"""
        "everything has convention and is set" | setAll("convention", "convention")   | setAll("test", "baz") | """id = test\nbar = baz"""
    }

    def "can configure build-level conventions in a non-declarative settings file"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.kts") << getDeclarativeSettingsScriptThatSetsConventions(setAll("convention", "convention"))

        file("build.gradle.something") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setId("test"))

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = test\nbar = convention""")
    }

    def "can configure build-level conventions in a declarative settings file and apply in a non-declarative project file"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.something") << getDeclarativeSettingsScriptThatSetsConventions(setAll("convention", "convention"))

        file("build.gradle.kts") << """
            plugins { id("com.example.test-software-type-impl") }
        """ + getDeclarativeScriptThatConfiguresOnlyTestSoftwareType()

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = convention\nbar = convention""")
    }

    def "can configure build-level conventions in a settings plugin"() {
        given:
        withSettingsPluginThatConfiguresSoftwareTypeConventions().prepareToExecute()

        file("settings.gradle.kts") << getDeclarativeSettingsScriptThatSetsConventions()

        file("build.gradle.something") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setId("test"))

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = test\nbar = plugin""")
    }

    static String setId(String id) {
        return "id = \"${id}\""
    }

    static String setFooBar(String bar) {
        return "foo { bar = \"${bar}\" }"
    }

    static String setAll(String id, String bar) {
        return setId(id) + "\n" + setFooBar(bar)
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(String configuration="") {
        return """
            testSoftwareType {
                ${configuration}
            }
        """
    }

    static String getDeclarativeSettingsScriptThatSetsConventions(String configuration="") {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }

            testSoftwareType {
                ${configuration}
            }
        """
    }
}
