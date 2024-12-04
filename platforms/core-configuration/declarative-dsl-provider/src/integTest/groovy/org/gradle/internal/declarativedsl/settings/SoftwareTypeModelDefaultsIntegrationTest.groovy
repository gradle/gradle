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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

class SoftwareTypeModelDefaultsIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture {
    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    def "can configure build-level defaults for property objects in a software type (#testCase)"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults(modelDefault)

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(buildConfiguration)

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains(expectedConfiguration)

        where:
        testCase                                           | modelDefault                 | buildConfiguration    | expectedConfiguration
        "top-level property has default and is set"        | setId("default")             | setId("test")         | """id = test\nbar = bar"""
        "top-level property has default, nested is set"    | setId("default")             | setFooBar("baz")      | """id = default\nbar = baz"""
        "nested property has default and is set"           | setFooBar("default")         | setFooBar("baz")      | """id = <no id>\nbar = baz"""
        "nested property has default, top-level is set"    | setFooBar("default")         | setId("test")         | """id = test\nbar = default"""
        "no defaults, top-level property is set"           | ""                           | setId("test")         | """id = test\nbar = bar"""
        "everything has default and nothing set"           | setAll("default", "default") | ""                    | """id = default\nbar = default"""
        "everything has default and is set"                | setAll("default", "default") | setAll("test", "baz") | """id = test\nbar = baz"""
    }

    def "sensible error when defaults are set more than once (#testCase)"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults(modelDefault)

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType("")

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        result.assertHasErrorOutput("Value reassigned")

        where:
        testCase                                   | modelDefault
        "id has default set twice"                 | setId("default") + setId("again")
        "bar has default set twice"                | setFoo(setBar("default") + setBar("again"))
        // TODO - doesn't work
        //"bar has default set in multiple blocks" | setFooBar("default") + setFooBar("again")
    }

    def "can configure build-level defaults for adding functions in a software type (#testCase)"() {
        given:
        withSoftwareTypePluginThatExposesExtensionWithDependencies().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults(modelDefault)

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType("""
            ${setId("foo")}
            ${dependencies(buildConfiguration)}
        """)

        when:
        run(":printTestSoftwareTypeExtensionWithDependenciesConfiguration")

        then:
        expectedConfigurations.each { outputContains(it) }

        where:
        testCase                                          | modelDefault     | buildConfiguration | expectedConfigurations
        "top-level adder has a default and is called"     | addToList("foo") | addToList("bar")   | "list = foo, bar"
        "top-level adder has a default and is not called" | addToList("foo") | ""                 | "list = foo"
        "nested adder has a default and is called"        | addToBaz("foo")  | addToBaz("bar")    | "baz = foo, bar"
        "nested adder has a default and is not called"    | addToBaz("foo")  | ""                 | "baz = foo"
        "everything has defaults and nothing is called"   | addToAll("foo")  | ""                 | ["list = foo", "baz = foo"]
        "everything has defaults and all are called"      | addToAll("foo")  | addToAll("bar")    | ["list = foo, bar", "baz = foo, bar"]
    }

    @UnsupportedWithConfigurationCache
    def "can configure build-level defaults for dependencies objects in a software type (#testCase)"() {
        given:
        withSoftwareTypePluginThatExposesExtensionWithDependencies().prepareToExecute()

        file("foo").createDir()
        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults(dependencies(modelDefault)) + """
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
        [testCase, modelDefault, buildConfiguration, expectedConfigurations] << [
            [
                testCase: "implementation has default and is set",
                modelDefault: implementation("foo:bar:1.0"),
                buildConfiguration: implementation("baz:buzz:2.0"),
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has default and is not set",
                modelDefault: implementation("foo:bar:1.0"),
                buildConfiguration: "",
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}"
                ]
            ],
            [
                testCase: "implementation has default and api is set",
                modelDefault: implementation("foo:bar:1.0"),
                buildConfiguration: api("baz:buzz:2.0"),
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}",
                    "api = ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "all configurations have defaults and are set",
                modelDefault: allConfigs("foo:bar:1.0"),
                buildConfiguration: allConfigs("baz:buzz:2.0"),
                expectedConfigurations: [
                    "api = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}",
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}",
                    "runtimeOnly = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}",
                    "compileOnly = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "all configurations have defaults and are not set",
                modelDefault: allConfigs("foo:bar:1.0"),
                buildConfiguration: "",
                expectedConfigurations: [
                    "api = ${externalDependency('foo', 'bar', '1.0')}",
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}",
                    "runtimeOnly = ${externalDependency('foo', 'bar', '1.0')}",
                    "compileOnly = ${externalDependency('foo', 'bar', '1.0')}"
                ]
            ],
            [
                testCase: "implementation has multiple defaults and is set",
                modelDefault: implementation("foo:bar:1.0", "baz:buzz:2.0"),
                buildConfiguration: implementation("buzz:baz:1.0", "bar:foo:2.0"),
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}, ${externalDependency('buzz', 'baz', '1.0')}, ${externalDependency('bar', 'foo', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has multiple defaults and is not set",
                modelDefault: implementation("foo:bar:1.0", "baz:buzz:2.0"),
                buildConfiguration: "",
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has project default and is set",
                modelDefault: implementation('project(":foo")'),
                buildConfiguration: implementation("baz:buzz:2.0"),
                expectedConfigurations: [
                    "implementation = ${projectDependency(':foo')}, ${externalDependency('baz', 'buzz', '2.0')}"
                ]
            ],
            [
                testCase: "implementation has default and is set to project",
                modelDefault: implementation("foo:bar:1.0"),
                buildConfiguration: implementation('project(":foo")'),
                expectedConfigurations: [
                    "implementation = ${externalDependency('foo', 'bar', '1.0')}, ${projectDependency(':foo')}"
                ]
            ]
        ]
    }

    @UnsupportedWithConfigurationCache
    def "can configure build-level defaults for software types in a multi-project build"() {
        given:
        withSoftwareTypePluginThatExposesExtensionWithDependencies().prepareToExecute()

        file("foo").createDir()
        file("bar").createDir()
        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults("""
            ${setId("default")}
            ${setFooBar("default")}
            ${addToBaz("default")}
            ${dependencies(implementation("foo:bar:1.0"))}
        """)
        file("settings.gradle.dcl") << """
            include("foo")
            include("bar")
        """

        file("foo/build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType("""
            ${setAll("foo", "fooBar")}
            ${addToBaz("foo")}
        """)
        file("bar/build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType("""
            ${setAll("bar", "barBar")}
            ${dependencies(implementation("bar:foo:2.0"))}
        """)

        when:
        run(":foo:printTestSoftwareTypeExtensionWithDependenciesConfiguration")

        then:
        outputContains("id = foo\nbar = fooBar")
        outputContains("baz = default, foo")
        outputContains("implementation = ${externalDependency('foo', 'bar', '1.0')}")

        when:
        run(":bar:printTestSoftwareTypeExtensionWithDependenciesConfiguration")

        then:
        outputContains("id = bar\nbar = barBar")
        outputContains("baz = default")
        outputContains("implementation = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('bar', 'foo', '2.0')}")
    }

    def "can trigger object configuration for nested objects used in defaults"() {
        given:
        withSoftwareTypePluginThatExposesExtensionWithDependencies().prepareToExecute()

        and: 'a default that only accesses a nested object but does not apply any configuration to it'
        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults("""
            ${setFoo("")}
        """)
        file("settings.gradle.dcl") << """
            include("foo")
        """

        and: 'a build file that only specifies the software type'
        file("foo/build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType()

        when:
        run(":foo:printTestSoftwareTypeExtensionWithDependenciesConfiguration")

        then: 'the side effect of the configuring function used in the default should get applied to the project model'
        outputContains("(foo is configured)")
    }

    def "can configure build-level defaults in a non-declarative settings file and apply in a declarative project file (#type settings script)"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle${extension}") << getDeclarativeSettingsScriptThatSetsDefaults(setAll("default", "default")) + """
            include("declarative")
            include("non-declarative")
        """

        file("declarative/build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setId("foo"))

        file("non-declarative/build.gradle${extension}") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setFooBar("bar"))

        when:
        run(":declarative:printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = foo\nbar = default""")

        when:
        run(":non-declarative:printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = default\nbar = bar""")

        where:
        type     | extension
        "groovy" | ""
        "kotlin" | ".kts"
    }

    def "can configure build-level defaults in a declarative settings file and apply in a non-declarative project file (#type build script)"() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults(setAll("default", "default")) + """
            include("non-declarative")
            include("declarative")
        """

        file("non-declarative/build.gradle${extension}") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setFooBar("bar"))
        file("declarative/build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setId("bar"))

        when:
        run(":non-declarative:printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = default\nbar = bar""")

        when:
        run(":declarative:printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = bar\nbar = default""")

        where:
        type     | extension
        "groovy" | ""
        "kotlin" | ".kts"
    }

    def "can configure defaults for named domain object container elements"() {
        given:
        withSoftwareTypePluginWithNdoc().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaultsForNdoc()

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(fooNdocYValues())

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("Foo(name = one, x = 1, y = 11)")
        outputContains("Foo(name = two, x = 2, y = 22)")
    }

    @NotYetImplemented
    def "can configure build-level defaults in a settings plugin"() {
        given:
        withSettingsPluginThatConfiguresModelDefaults().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults()

        file("build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setId("test"))

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""id = test\nbar = plugin""")
    }

    static String setId(String id) {
        return "id = \"${id}\"\n"
    }

    static String setFooBar(String bar) {
        return setFoo(setBar(bar))
    }

    static String setBar(String bar) {
        return "bar = \"${bar}\"\n"
    }

    static String setFoo(String contents) {
        return "foo {\n${contents}\n}"
    }

    static String setAll(String id, String bar) {
        return setId(id) + "\n" + setFooBar(bar)
    }

    static String addToList(String item) {
        return "addToList(\"${item}\")"
    }

    static String addToBaz(String item) {
        return "bar { addToBaz(\"${item}\") }"
    }

    static String addToAll(String item) {
        return addToList(item) + "\n" + addToBaz(item)
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

    static String fooNdocYValues() {
        return """
        foos {
            foo("one") {
                y = 11
            }
            foo("two") {
                y = 22
            }
        }
        """
    }

    static String dependencies(String dependencies) {
        return """
            dependencies {
                ${dependencies}
            }
        """
    }

    static String externalDependency(String group, String name, String version) {
        return "${group}:${name}:${version}"
    }

    static String projectDependency(String projectPath) {
        return "project '${projectPath}'"
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(String configuration="") {
        return """
            testSoftwareType {
                ${configuration}
            }
        """
    }

    static String getDeclarativeSettingsScriptThatSetsDefaults(String configuration="") {
        return """
            pluginManagement {
                includeBuild("plugins")
            }

            plugins {
                id("com.example.test-software-type")
            }

            defaults {
                testSoftwareType {
                    ${configuration}
                }
            }
        """
    }

    static String getDeclarativeSettingsScriptThatSetsDefaultsForNdoc(String configuration="") {
        return getDeclarativeSettingsScriptThatSetsDefaults("" +
            """
            foos {
                foo("one") {
                    x = 1
                }
                foo("two") {
                    x = 2
                }
            }
            """
        )
    }

}
