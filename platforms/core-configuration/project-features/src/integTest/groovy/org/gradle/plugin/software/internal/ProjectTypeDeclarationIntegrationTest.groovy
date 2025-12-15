/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugin.software.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.internal.declarativedsl.settings.ProjectTypeFixture
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.hamcrest.Matchers
import org.junit.Rule

@PolyglotDslTest
class ProjectTypeDeclarationIntegrationTest extends AbstractIntegrationSpec implements ProjectTypeFixture, PolyglotTestFixture {
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
        withProjectType().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    def 'can declare and configure a custom project type from published plugin'() {
        given:
        pluginPortal.start()
        def pluginBuilder = withProjectType()
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        settingsFile() << """
            plugins {
                id("com.example.test-software-ecosystem").version("1.0")
            }
        """

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    def 'can declare and configure a custom project type from plugin published to a custom repository'() {
        given:
        def pluginBuilder = withProjectType()
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

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        succeeds(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    def 'can declare multiple custom project types from a single settings plugin'() {
        given:
        withSettingsPluginThatExposesMultipleProjectTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("Applying ProjectTypeImplPlugin")
        assertThatDeclaredValuesAreSetProperly()

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
        withSettingsPluginThatExposesMultipleProjectTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputDoesNotContain("Applying AnotherProjectTypeImplPlugin")
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy has no problem with finding non-public methods/types ...")
    def 'can declare and configure a custom project type with different public and implementation model types'() {
        given:
        withProjectTypeThatHasDifferentPublicAndImplementationTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        run(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

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
        withProjectTypePluginThatDoesNotExposeProjectTypes().prepareToExecute()

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
        withTwoProjectTypesThatHaveTheSameName().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        fails(":help")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'testProjectType' is registered by multiple plugins:\n" +
            "  - Project feature 'testProjectType' is registered by both 'org.gradle.test.AnotherProjectTypeImplPlugin' and 'org.gradle.test.ProjectTypeImplPlugin'.\n" +
            "    \n" +
            "    Reason: A project feature or type with a given name can only be registered by a single plugin.\n" +
            "    \n" +
            "    Possible solution: Remove one of the plugins from the build."
        )
    }

    def 'a project type plugin can declare multiple project types'() {
        given:
        withProjectTypePluginThatExposesMultipleProjectTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        succeeds(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

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
        withProjectTypePluginThatExposesMultipleProjectTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType + """
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

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy can use a property value on the assignment RHS")
    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Kotlin can use a property value on the assignment RHS")
    def 'sensible error when declarative script uses a property as value for another property'() {
        given:
        withProjectType().prepareToExecute()

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

    static String getDeclarativeScriptThatConfiguresOnlyTestProjectType() {
        return """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")
    }

    void assertDescriptionOrCause(ExecutionFailure failure, String expectedMessage) {
        if (currentDsl() == GradleDsl.DECLARATIVE) {
            failure.assertHasDescription(expectedMessage)
        } else {
            failure.assertHasCause(expectedMessage)
        }
    }
}
