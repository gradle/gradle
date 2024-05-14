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

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsConventions(convention)

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(buildConfiguration)

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

    def "can configure build-level conventions for dependencies objects in a software type (#testCase)"() {
        given:
        withSoftwareTypePluginThatExposesExtensionWithDependencies().prepareToExecute()

        file("foo").createDir()
        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsConventions(dependencies(convention)) + """
            include("foo")
        """

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType("""
            ${setId("foo")}
            ${dependencies(buildConfiguration)}
        """)

        when:
        run(":printTestSoftwareTypeExtensionWithDependenciesConfiguration")

        then:
        expectedConfigurations.each { outputContains(it) }

        where:
        [testCase, convention, buildConfiguration, expectedConfigurations] << [
            [
                testCase: "implementation has convention and is set",
                convention: implementation("foo:bar:1.0"),
                buildConfiguration: implementation("baz:buzz:2.0"),
                expectedConfigurations: [
                    "implementation = ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has convention and is not set",
                convention: implementation("foo:bar:1.0"),
                buildConfiguration: "",
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}"
                ]
            ],
            [
                testCase: "implementation has convention and api is set",
                convention: implementation("foo:bar:1.0"),
                buildConfiguration: api("baz:buzz:2.0"),
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}",
                    "api = ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "all configurations have conventions and are set",
                convention: allConfigs("foo:bar:1.0"),
                buildConfiguration: allConfigs("baz:buzz:2.0"),
                expectedConfigurations: [
                    "api = ${externalDependency('baz', 'buzz', '2.0')}",
                    "implementation = ${externalDependency('baz', 'buzz', '2.0')}",
                    "runtimeOnly = ${externalDependency('baz', 'buzz', '2.0')}",
                    "compileOnly = ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "all configurations have conventions and are not set",
                convention: allConfigs("foo:bar:1.0"),
                buildConfiguration: "",
                expectedConfigurations: [
                    "api = ${externalDependency('foo', 'bar', '1.0')}",
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}",
                    "runtimeOnly = ${externalDependency('foo', 'bar', '1.0')}",
                    "compileOnly = ${externalDependency('foo', 'bar', '1.0')}"
                ]
            ],
            [
                testCase: "implementation has multiple conventions and is set",
                convention: implementation("foo:bar:1.0", "baz:buzz:2.0"),
                buildConfiguration: implementation("buzz:baz:1.0", "bar:foo:2.0"),
                expectedConfigurations: [
                    "implementation = ${externalDependency('buzz', 'baz', '1.0')}, ${externalDependency('bar', 'foo', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has multiple conventions and is not set",
                convention: implementation("foo:bar:1.0", "baz:buzz:2.0"),
                buildConfiguration: "",
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has project convention and is set",
                convention: implementation('project(":foo")'),
                buildConfiguration: implementation("baz:buzz:2.0"),
                expectedConfigurations: [
                    "implementation = ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has convention and is set to project",
                convention: implementation("foo:bar:1.0"),
                buildConfiguration: implementation('project(":foo")'),
                expectedConfigurations: [
                    "implementation = ${projectDependency(':foo')}"
                ]
            ]
        ]
    }

    def "can configure build-level conventions in a non-declarative settings file"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.kts") << getDeclarativeSettingsScriptThatSetsConventions(setAll("convention", "convention"))

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setId("test"))

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = test\nbar = convention""")
    }

    def "can configure build-level conventions in a declarative settings file and apply in a non-declarative project file"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsConventions(setAll("convention", "convention"))

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

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsConventions()

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setId("test"))

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

    static String dependencyFor(String configuration, String[] dependencies) {
        return dependencies.collect { dependency(configuration, it) }.join("\n")
    }

    static String dependency(String configuration, String dependency) {
        return dependency.startsWith("project(") ? "${configuration}(${dependency})" : "${configuration}(\"${dependency}\")"
    }

    static String implementation(String... dependencies) {
        return dependencyFor("implementation", dependencies)
    }

    static String api(String... dependencies) {
        return dependencyFor("api", dependencies)
    }

    static String compileOnly(String... dependencies) {
        return dependencyFor("compileOnly", dependencies)
    }

    static String runtimeOnly(String... dependencies) {
        return dependencyFor("runtimeOnly", dependencies)
    }

    static String allConfigs(String... dependencies) {
        return implementation(dependencies) + "\n" + api(dependencies) + "\n" + compileOnly(dependencies) + "\n" + runtimeOnly(dependencies)
    }

    static String dependencies(String dependencies) {
        return """
            dependencies {
                ${dependencies}
            }
        """
    }

    static String externalDependency(String group, String name, String version) {
        return "DefaultExternalModuleDependency{group='${group}', name='${name}', version='${version}', configuration='default'}"
    }

    static String projectDependency(String projectPath) {
        return "DefaultProjectDependency{identityPath='${projectPath}', configuration='default'}"
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

            conventions {
                testSoftwareType {
                    ${configuration}
                }
            }
        """
    }
}
