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
class SoftwareTypeDeclarationIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture, PolyglotTestFixture {
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

    def 'can declare and configure a custom software type from included build'() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    def 'can declare and configure a custom software type from published plugin'() {
        given:
        pluginPortal.start()
        def pluginBuilder = withSoftwareTypePlugins()
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        settingsFile() << """
            plugins {
                id("com.example.test-software-ecosystem").version("1.0")
            }
        """

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    /**
     * This test is not yet implemented because it requires a custom repository to be set up which is not possible yet with the declarative dsl.
     */
    def 'can declare and configure a custom software type from plugin published to a custom repository'() {
        given:
        def pluginBuilder = withSoftwareTypePlugins()
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

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        succeeds(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    def 'can declare multiple custom software types from a single settings plugin'() {
        given:
        withSettingsPluginThatExposesMultipleSoftwareTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        outputContains("Applying SoftwareTypeImplPlugin")
        assertThatDeclaredValuesAreSetProperly()

        when:
        buildFile().text = """
            anotherSoftwareType {
                foo = "test2"

                bar {
                    baz = "fizz"
                }
            }
        """
        run(":printAnotherSoftwareTypeExtensionConfiguration")

        then:
        outputContains("Applying AnotherSoftwareTypeImplPlugin")
        outputContains("""foo = test2\nbaz = fizz""")
    }

    def 'can declare multiple custom software types from a single settings plugin but apply only one'() {
        given:
        withSettingsPluginThatExposesMultipleSoftwareTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy has no problem with finding non-public methods/types ...")
    def 'can declare and configure a custom software type with different public and implementation model types'() {
        given:
        withSoftwareTypePluginThatHasDifferentPublicAndImplementationModelTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionImplConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        when:
        buildFile() << """
            testSoftwareType {
                nonPublic = "foo"
            }
        """
        fails(":printTestSoftwareTypeExtensionImplConfiguration")

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

    def 'sensible error when a software type plugin is registered that does not expose a software type'() {
        given:
        withSoftwareTypePluginThatDoesNotExposeSoftwareTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        when:
        fails(":help")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-ecosystem'.")
        failure.assertHasCause("A problem was found with the NotASoftwareTypePlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.NotASoftwareTypePlugin' is registered as a software feature plugin but does not expose a software feature.")
    }

    def 'a software type plugin can declare multiple software types'() {
        given:
        withSoftwareTypePluginThatExposesMultipleSoftwareTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        succeeds(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        when:
        buildFile().text = """
            anotherSoftwareType {
                foo = "test"
                bar {
                    baz = "fizz"
                }
            }
        """
        succeeds(":printAnotherSoftwareTypeExtensionConfiguration")

        then:
        outputContains("""foo = test\nbaz = fizz""")
    }

    def 'sensible error when a script applies multiple software types'() {
        given:
        withSoftwareTypePluginThatExposesMultipleSoftwareTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType + """
            anotherSoftwareType {
                bar {
                    baz = "fizz"
                }
            }
        """

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("The project has already applied the 'testSoftwareType' software type and is also attempting to apply the 'anotherSoftwareType' software type.  Only one software type can be applied to a project.")
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy can use a property value on the assignment RHS")
    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Kotlin can use a property value on the assignment RHS")
    def 'sensible error when declarative script uses a property as value for another property'() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testSoftwareType {
                id = "test"

                foo {
                    bar = id
                }
            }
        """

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        errorOutput.contains(
            "6:27: property cannot be used as a value: 'id'"
        )
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareType() {
        return """
            testSoftwareType {
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
