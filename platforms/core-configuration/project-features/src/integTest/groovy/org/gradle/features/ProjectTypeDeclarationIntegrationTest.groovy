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

package org.gradle.features

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.features.internal.TestScenarioFixture
import org.gradle.features.internal.builders.PluginClassBuilder
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions

import static org.gradle.features.internal.builders.Language.KOTLIN
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository

import org.hamcrest.Matchers
import org.junit.Rule

@PolyglotDslTest
@SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy DSL is not supported for declarative configuration")
class ProjectTypeDeclarationIntegrationTest extends AbstractIntegrationSpec implements TestScenarioFixture, PolyglotTestFixture {
    @Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Rule
    MavenHttpPluginRepository mavenHttpRepo = new MavenHttpPluginRepository(mavenRepo)

    def setup() {
        // enable DCL support to have KTS accessors generated
        propertiesFile << "org.gradle.kotlin.dsl.dcl=true"

        // We only need the test plugin portal for one test, but we need the actual plugin portal for
        // other tests, so we stop it by default and start it only when needed.
        pluginPortal.stop()
    }

    def 'can declare and configure a custom project type from included build'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        and:
        outputContains("Applying TestProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    def 'can declare and configure a custom project type from published plugin'() {
        given:
        pluginPortal.start()
        def pluginBuilder = testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
        }
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        settingsFile() << """
            plugins {
                id("com.example.test-software-ecosystem").version("1.0")
            }
        """

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        and:
        outputContains("Applying TestProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    @Requires(JdkVersionTestPreconditions.Jdk23OrEarlier) // Because Kotlin does not support 24 yet and falls back to 23 causing inconsistent JVM targets
    def 'can declare and configure a custom project type using reified Kotlin binding'() {
        given:
        PluginBuilder pluginBuilder = testScenario {
            language KOTLIN
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
                plugin {
                    bindingStyle PluginClassBuilder.BindingStyle.REIFIED
                }
            }
        }
        pluginBuilder.applyBuildScriptPlugin("org.jetbrains.kotlin.jvm", new KotlinGradlePluginVersions().getLatestStableOrRC())
        pluginBuilder.addBuildScriptContent pluginBuildScriptForKotlin
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        and:
        outputContains("Applying TestProjectTypeImplPlugin")
    }

    def 'can declare and configure a custom project type from plugin published to a custom repository'() {
        given:
        def pluginBuilder = testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
        }
        pluginBuilder.publishAs("com", "example", "1.0", mavenHttpRepo, createExecuter()).allowAll()

        settingsFile() << """
            pluginManagement {
                repositories {
                    maven { url = uri("$mavenHttpRepo.uri") }
                }
            }
            plugins {
                id("com.example.test-software-ecosystem").version("1.0")
            }
        """

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        succeeds(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        and:
        outputContains("Applying TestProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    def 'can declare multiple custom project types from a single settings plugin'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
            projectType("anotherProjectType") {
                definition {
                    property "id", String
                    property "foo", String
                    buildModel {
                        property "id", String
                        property "foo", String
                    }
                    nested("bar", "Bar") {
                        property "baz", String
                    }
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("Applying TestProjectTypeImplPlugin")
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        when:
        buildFile().text = """
            anotherProjectType {
                id = "another"
                foo = "test2"

                bar {
                    baz = "fizz"
                }
            }
        """
        run(":printAnotherProjectTypeDefinitionConfiguration")

        then:
        outputContains("Applying AnotherProjectTypeImplPlugin")
        outputContains("definition foo = test2")
        outputContains("definition bar.baz = fizz")
        outputContains("model id = another")
    }

    def 'can declare multiple custom project types from a single settings plugin but apply only one'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
            projectType("anotherProjectType") {
                definition {
                    property "id", String
                    property "foo", String
                    buildModel {
                        property "id", String
                        property "foo", String
                    }
                    nested("bar", "Bar") {
                        property "baz", String
                    }
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        and:
        outputContains("Applying TestProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    def 'can declare and configure a custom project type with different public and implementation model types'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                    implementationType("TestProjectTypeDefinitionImpl")
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        when:
        buildFile() << """
            testProjectType {
                nonPublic = "foo"
            }
        """
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        if (GradleDsl.KOTLIN == currentDsl()) {
            failure.assertThatDescription(Matchers.containsString("Unresolved reference 'nonPublic'"))
        } else if (GradleDsl.DECLARATIVE == currentDsl()) {
            failure.assertThatCause(Matchers.containsString("Failed to interpret the declarative DSL file"))
            failure.assertThatCause(Matchers.containsString("unresolved reference 'nonPublic'"))
        } else {
            throw new RuntimeException("Test wasn't meant to be run with " + currentDsl().languageCodeName + " DSL")
        }
    }

    def 'sensible error when a project type plugin is registered that does not expose a project type'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    ndoc("foos", "Foo") {}
                }
                plugin {
                    noBindings()
                    pluginClassName "NotAProjectTypePlugin"
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        when:
        fails(":help")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-ecosystem'.")
        failure.assertHasCause("A problem was found with the NotAProjectTypePlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.NotAProjectTypePlugin' is registered as a project feature plugin but does not expose a project feature.")
    }

    def 'sensible error when two plugins register the same project type'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
            projectType("testProjectType") {
                definition("AnotherTestProjectTypeDefinition") {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
                plugin {
                    pluginClassName "AnotherTestProjectTypeImplPlugin"
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        fails(":help")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'testProjectType' is registered by multiple plugins:\n" +
                "  - Project feature 'testProjectType' is registered by both 'org.gradle.test.AnotherTestProjectTypeImplPlugin' and 'org.gradle.test.TestProjectTypeImplPlugin' but their bindings have overlapping target types.\n" +
                "    \n" +
                "    Reason: A project feature or type with a given name must bind to a unique target type.\n" +
                "    \n" +
                "    Possible solution: Remove one of the plugins from the build."
        )
    }

    def 'a project type plugin can declare multiple project types'() {
        given:
        testScenario {
            def testProjectType = projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
                noPlugin()
            }
            def anotherProjectType = projectType("anotherProjectType") {
                definition {
                    buildModel {
                        property "id", String
                        property "foo", String
                    }
                    property("id", String)
                    property("foo", String)
                    nested("bar", "Bar") {
                        property "baz", String
                    }
                }
                noPlugin()
            }
            plugin("CombinedPlugin") {
                bindsType anotherProjectType
                bindsType testProjectType
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """

        when:
        succeeds(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")

        when:
        buildFile().text = """
            anotherProjectType {
                id = "another"
                foo = "test"
                bar {
                    baz = "fizz"
                }
            }
        """
        succeeds(":printAnotherProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition foo = test")
        outputContains("definition bar.baz = fizz")
        outputContains("model id = another")
    }

    def 'sensible error when a script applies multiple project types'() {
        given:
        testScenario {
            projectType("anotherProjectType") {
                definition {
                    buildModel("ModelType") {
                        property "id", String
                        property "foo", String
                    }

                    property("id", String)
                    property("foo", String)
                    nested("bar", "Bar") {
                        property "baz", String
                    }
                }
            }

            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }

            anotherProjectType {
                bar {
                    baz = "fizz"
                }
            }
        """

        when:
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasCause("The project has already applied the 'testProjectType' project type and is also attempting to apply the 'anotherProjectType' project type.  Only one project type can be applied to a project.")
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Kotlin can use a property value on the assignment RHS")
    def 'sensible error when declarative script uses a property as value for another property'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    nested("foo", "Foo") {
                        property "bar", String
                    }
                }
            }
        }.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = id
                }
            }
        """

        when:
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        errorOutput.contains(
            "6:27: property cannot be used as a value: 'id'"
        )
    }

    void assertDescriptionOrCause(ExecutionFailure failure, String expectedMessage) {
        if (currentDsl() == GradleDsl.DECLARATIVE) {
            failure.assertHasDescription(expectedMessage)
        } else {
            failure.assertHasCause(expectedMessage)
        }
    }
}
