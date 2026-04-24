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

import org.gradle.features.internal.TestScenarioFixture
import org.gradle.features.internal.builders.TypeShape
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.internal.declarativedsl.DeclarativeTestUtils
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.plugin.PluginBuilder

import spock.lang.Issue

@PolyglotDslTest
@SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy DSL is not supported for declarative configuration")
class ProjectTypeModelDefaultsIntegrationTest extends AbstractIntegrationSpec implements TestScenarioFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    def "can configure build-level defaults for property objects in a project type (#testCase)"() {
        given:
        withStandardProjectType().prepareToExecute()

        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults(modelDefault)

        buildFile() << getDeclarativeScriptThatConfiguresOnlyTestProjectType(buildConfiguration) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        expectedValues.each { String value -> outputContains(value) }

        where:
        testCase                                           | modelDefault                 | buildConfiguration    | expectedValues
        "top-level property has default and is set"        | setId("default")             | setId("test")         | expected("id":"test", "foo.bar":"null")
        "top-level property has default, nested is set"    | setId("default")             | setFooBar("baz")      | expected("id":"default", "foo.bar":"baz")
        "nested property has default and is set"           | setFooBar("default")         | setFooBar("baz")      | expected("id":"null", "foo.bar":"baz")
        "nested property has default, top-level is set"    | setFooBar("default")         | setId("test")         | expected("id":"test", "foo.bar":"default")
        "no defaults, top-level property is set"           | ""                           | setId("test")         | expected("id":"test", "foo.bar":"null")
        "everything has default and nothing set"           | setAll("default", "default") | ""                    | expected("id":"default", "foo.bar":"default")
        "everything has default and is set"                | setAll("default", "default") | setAll("test", "baz") | expected("id":"test", "foo.bar":"baz")
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Kotlin DSL does accept re-assigning values")
    def "sensible error when defaults are set more than once (#testCase)"() {
        given:
        withStandardProjectType().prepareToExecute()

        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults(modelDefault)

        buildFile() << getDeclarativeScriptThatConfiguresOnlyTestProjectType("") << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        result.assertHasErrorOutput("reassigned value in ")

        where:
        testCase                                   | modelDefault
        "id has default set twice"                 | setId("default") + setId("again")
        "bar has default set twice"                | configureFoo(setBar("default") + setBar("again"))
        // TODO - doesn't work
        //"bar has default set in multiple blocks" | setFooBar("default") + setFooBar("again")
    }

    def "can configure build-level defaults for adding functions in a project type (#testCase)"() {
        given:
        withDependenciesProjectType().prepareToExecute()

        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults(modelDefault)

        buildFile() << getDeclarativeScriptThatConfiguresOnlyTestProjectType("""
            ${setId("foo")}
            ${dependencies(buildConfiguration)}
        """) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        expectedValues.each { String value -> outputContains(value) }

        where:
        testCase                                          | modelDefault     | buildConfiguration | expectedValues
        "top-level adder has a default and is called"     | addToList("foo") | addToList("bar")   | expected("list":"foo, bar")
        "top-level adder has a default and is not called" | addToList("foo") | ""                 | expected("list":"foo")
        "nested adder has a default and is called"        | addToBaz("foo")  | addToBaz("bar")    | expected("bar.baz": "foo, bar")
        "nested adder has a default and is not called"    | addToBaz("foo")  | ""                 | expected("bar.baz":"foo")
        "everything has defaults and nothing is called"   | addToAll("foo")  | ""                 | expected("list":"foo", "bar.baz":"foo")
        "everything has defaults and all are called"      | addToAll("foo")  | addToAll("bar")    | expected("list":"foo, bar", "bar.baz":"foo, bar")
    }

    @UnsupportedWithConfigurationCache
    def "can configure build-level defaults for dependencies objects in a project type (#testCase)"() {
        given:
        withDependenciesProjectType().prepareToExecute()

        file("foo").createDir()
        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults(dependencies(modelDefault)) + """
            include("foo")
        """

        buildFile() << getDeclarativeScriptThatConfiguresOnlyTestProjectType("""
            ${setId("foo")}
            ${dependencies(buildConfiguration)}
        """) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

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
    def "can configure build-level defaults for project types in a multi-project build"() {
        given:
        withDependenciesProjectType().prepareToExecute()

        file("foo").createDir()
        file("bar").createDir()
        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults("""
            ${setId("default")}
            ${setFooBar("default")}
            ${addToBaz("default")}
            ${dependencies(implementation("foo:bar:1.0"))}
        """)
        settingsFile() << """
            include("foo")
            include("bar")
        """

        buildFileForProject("foo") << getDeclarativeScriptThatConfiguresOnlyTestProjectType("""
            ${setAll("foo", "fooBar")}
            ${addToBaz("foo")}
        """) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl
        buildFileForProject("bar") << getDeclarativeScriptThatConfiguresOnlyTestProjectType("""
            ${setAll("bar", "barBar")}
            ${dependencies(implementation("bar:foo:2.0"))}
        """) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":foo:printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = foo")
        outputContains("definition foo.bar = fooBar")
        outputContains("definition bar.baz = default, foo")
        outputContains("definition implementation = ${externalDependency('foo', 'bar', '1.0')}")

        when:
        run(":bar:printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = bar")
        outputContains("definition foo.bar = barBar")
        outputContains("definition bar.baz = default")
        outputContains("definition implementation = ${externalDependency('foo', 'bar', '1.0')}, ${externalDependency('bar', 'foo', '2.0')}")
    }

    def "can trigger object configuration for nested objects used in defaults"() {
        given: 'a project type that opts in to maybe<X>Configured() scaffolding'
        testScenario {
            projectType("testProjectType") {
                definition {
                    shape TypeShape.ABSTRACT_CLASS
                    showConfigureInvocations()
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                    listProperty "list", String
                    property("bar", "Bar") {
                        listProperty "baz", String
                    }
                    dependencies {
                        dependencyCollector 'api'
                        dependencyCollector 'implementation'
                        dependencyCollector 'runtimeOnly'
                        dependencyCollector 'compileOnly'
                    }
                }
                plugin {
                    unsafeDefinition()
                    unsafeApplyAction()
                }
            }
        }.prepareToExecute()

        and: 'a default that only accesses a nested object but does not apply any configuration to it'
        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults("""
            ${configureBar("")}
        """)
        settingsFile() << """
            include("foo")
        """

        and: 'a build file that only specifies the project type'
        buildFileForProject("foo") << getDeclarativeScriptThatConfiguresOnlyTestProjectType() << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":foo:printTestProjectTypeDefinitionConfiguration")

        then: 'the side effect of the configuring function used in the default should get applied to the project model'
        outputContains("(bar is configured)")
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Test is written with build files for specific DSLs in mind")
    def "can configure build-level defaults in a non-declarative settings file and apply in a declarative project file (kotlin settings script)"() {
        given:
        withStandardProjectType().prepareToExecute()

        file("settings.gradle.kts") << getDeclarativeSettingsScriptThatSetsDefaults(setAll("default", "default")) + """
            include("declarative")
            include("non-declarative")
        """

        file("declarative/build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestProjectType(setId("foo"))

        file("non-declarative/build.gradle.kts") << getDeclarativeScriptThatConfiguresOnlyTestProjectType(setFooBar("bar"))

        when:
        run(":declarative:printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = foo")
        outputContains("definition foo.bar = default")

        when:
        run(":non-declarative:printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = default")
        outputContains("definition foo.bar = bar")
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Test is written with build files for specific DSLs in mind")
    def "can configure build-level defaults in a declarative settings file and apply in a non-declarative project file (kotlin build script)"() {
        given:
        withStandardProjectType().prepareToExecute()

        file("settings.gradle.dcl") << getDeclarativeSettingsScriptThatSetsDefaults(setAll("default", "default")) + """
            include("non-declarative")
            include("declarative")
        """

        file("non-declarative/build.gradle.kts") << getDeclarativeScriptThatConfiguresOnlyTestProjectType(setFooBar("bar"))
        file("declarative/build.gradle.dcl") << getDeclarativeScriptThatConfiguresOnlyTestProjectType(setId("bar"))

        when:
        run(":non-declarative:printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = default")
        outputContains("definition foo.bar = bar")

        when:
        run(":declarative:printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = bar")
        outputContains("definition foo.bar = default")
    }

    def "can configure defaults for named domain object container elements"() {
        given:
        withNdocProjectType().prepareToExecute()

        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaultsForNdoc()

        buildFile() << getDeclarativeScriptThatConfiguresOnlyTestProjectType("""
            id = "test"
            ${fooNdocYValues()}
        """)

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("Foo(name = one, x = 1, y = 11)")
        outputContains("Foo(name = two, x = 2, y = 22)")
    }

    def "can configure build-level defaults in a settings plugin"() {
        given:
        withSettingsDefaultsProjectType().prepareToExecute()

        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults()

        buildFile() << getDeclarativeScriptThatConfiguresOnlyTestProjectType(setId("test")) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = settings")
    }

    def "can configure build-level defaults that applies features to a project type (#testCase)"() {
        given:
        withProjectTypeAndFeature().prepareToExecute()

        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults(modelDefault)

        buildFile() << getDeclarativeScriptThatConfiguresOnlyTestProjectType(buildConfiguration) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printFeatureDefinitionConfiguration")

        then:
        expectedValues.each { String value -> outputContains(value) }

        where:
        testCase                                           | modelDefault                 | buildConfiguration      | expectedValues
        "feature is set in default and build script"       | setFeatureText("default")    | setFeatureText("test")  | expected("text":"test")
        "feature is set in default but not build script"   | setFeatureText("default")    | ""                      | expected("text":"default")
    }

    @Issue("https://github.com/gradle/gradle/issues/37377")
    def "configuring build-level defaults applies features in the correct order"() {
        given:
        withProjectTypeAndFeature().prepareToExecute()

        settingsFile() << getDeclarativeSettingsScriptThatSetsDefaults(setFeatureText("default"))

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printFeatureDefinitionConfiguration")

        then:
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
        outputContains("definition text = default")

        and:
        output.indexOf("Binding TestProjectTypeDefinition") < output.indexOf("Binding FeatureDefinition")
    }

    // --- Fixture helpers ---

    private PluginBuilder withStandardProjectType() {
        return testScenario {
            projectType("testProjectType") {
                definition {
                    buildModel {
                        property "id", String
                    }
                    property "id", String
                    property("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
        }
    }

    private PluginBuilder withDependenciesProjectType() {
        return testScenario {
            projectType("testProjectType") {
                definition {
                    shape TypeShape.ABSTRACT_CLASS
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                    listProperty "list", String
                    property("bar", "Bar") {
                        listProperty "baz", String
                    }
                    dependencies {
                        dependencyCollector 'api'
                        dependencyCollector 'implementation'
                        dependencyCollector 'runtimeOnly'
                        dependencyCollector 'compileOnly'
                    }
                }
                plugin {
                    unsafeDefinition()
                    unsafeApplyAction()
                }
            }
        }
    }

    private PluginBuilder withNdocProjectType() {
        return testScenario {
            projectType("testProjectType") {
                definition {
                    shape TypeShape.ABSTRACT_CLASS
                    buildModel {
                        property "id", String
                    }
                    property "id", String
                    ndoc("foos", "Foo") {
                        property "x", Integer
                        property "y", Integer
                    }
                }
                plugin {
                    unsafeDefinition()
                }
            }
        }
    }

    private PluginBuilder withSettingsDefaultsProjectType() {
        return testScenario {
            def type = projectType("testProjectType") {
                definition {
                    buildModel {
                        property "id", String
                    }
                    property "id", String
                    property("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
            settings {
                defaultFor(type) {
                    property "id", "settings"
                    property "foo.bar", "settings"
                }
            }
        }
    }

    private PluginBuilder withProjectTypeAndFeature() {
        return testScenario {
            def type = projectType("testProjectType") {
                definition {
                    buildModel {
                        property "id", String
                    }
                    property "id", String
                    property("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
            projectFeature("feature") {
                definition {
                    buildModel {
                        property "text", String
                    }
                    property "text", String
                    property("fizz", "Fizz") {
                        property "buzz", String
                    }
                }
                plugin {
                    bindsFeatureTo(type)
                }
            }
        }
    }

    // --- DSL content helpers ---

    private static String[] expected(Map<String, String> expectations) {
        return expectations.collect { k, v -> "definition ${k} = ${v}" }
    }

    static String setFeatureText(String text) {
        return """
            feature {
                text = "${text}"
                fizz {
                    buzz = ""
                }
            }
        """
    }

    static String setId(String id) {
        return "id = \"${id}\"\n"
    }

    static String setFooBar(String bar) {
        return configureFoo(setBar(bar))
    }

    static String setBar(String bar) {
        return "bar = \"${bar}\"\n"
    }

    static String configureFoo(String contents) {
        return "foo {\n${contents}\n}"
    }

    static String configureBar(String contents) {
        return "bar {\n${contents}\n}"
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

    static String getDeclarativeScriptThatConfiguresOnlyTestProjectType(String configuration="") {
        return """
            testProjectType {
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
                id("com.example.test-software-ecosystem")
            }

            defaults {
                testProjectType {
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
