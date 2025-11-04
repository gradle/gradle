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
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
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
        withProjectTypePlugins().prepareToExecute()

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
        def pluginBuilder = withProjectTypePlugins()
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

    /**
     * This test is not yet implemented because it requires a custom repository to be set up which is not possible yet with the declarative dsl.
     */
    def 'can declare and configure a custom project type from plugin published to a custom repository'() {
        given:
        def pluginBuilder = withProjectTypePlugins()
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
                foo = "test2"

                bar {
                    baz = "fizz"
                }
            }
        """
        run(":printAnotherProjectTypeDefinitionConfiguration")

        then:
        outputContains("Applying AnotherProjectTypeImplPlugin")
        outputContains("""foo = test2\nbaz = fizz""")
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
        withProjectTypePluginThatHasDifferentPublicAndImplementationModelTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        run(":printTestProjectTypeDefinitionImplConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        when:
        buildFile() << """
            testProjectType {
                nonPublic = "foo"
            }
        """
        fails(":printTestProjectTypeDefinitionImplConfiguration")

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
                foo = "test"
                bar {
                    baz = "fizz"
                }
            }
        """
        succeeds(":printAnotherProjectTypeDefinitionConfiguration")

        then:
        outputContains("""foo = test\nbaz = fizz""")
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
        withProjectTypePlugins().prepareToExecute()

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
        outputContains("""id = test\nbar = baz""")
    }
}
