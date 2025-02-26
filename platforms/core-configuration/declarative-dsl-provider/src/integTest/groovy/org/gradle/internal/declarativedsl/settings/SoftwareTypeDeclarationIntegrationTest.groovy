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

import static org.gradle.util.Matchers.containsText

@PolyglotDslTest
class SoftwareTypeDeclarationIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture, PolyglotTestFixture {
    @Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Rule
    MavenHttpPluginRepository mavenHttpRepo = new MavenHttpPluginRepository(mavenRepo)

    def setup() {
        // enable DCL support to have KTS accessors generated
        propertiesFile << "org.gradle.kotlin.dsl.dcl=true"
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
        def pluginBuilder = withSoftwareTypePlugins()
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        settingsFile() << """
            plugins {
                id("com.example.test-software-type").version("1.0")
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
                id("com.example.test-software-type").version("1.0")
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

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType + """
            anotherSoftwareType {
                foo = "test2"

                bar {
                    baz = "fizz"
                }
            }
        """

        when:
        run(":printTestSoftwareTypeExtensionConfiguration", ":printAnotherSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
        outputContains("""foo = test2\nbaz = fizz""")

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Applying AnotherSoftwareTypeImplPlugin")
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
            failure.assertThatDescription(Matchers.containsString("Unresolved reference: nonPublic"))
        } else if (GradleDsl.DECLARATIVE == currentDsl()) {
            failure.assertThatCause(Matchers.containsString("Failed to interpret the declarative DSL file"))
            failure.assertThatCause(Matchers.containsString("unresolved reference 'nonPublic'"))
        } else {
            throw new RuntimeException("Test wasn't meant to be run with " + currentDsl().languageCodeName + " DSL")
        }
    }

    def 'can declare and configure a custom software type from a parent class'() {
        given:
        withSoftwareTypePluginThatExposesSoftwareTypeFromParentClass().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'can declare and configure a custom software type from a plugin with unannotated methods'() {
        given:
        withSoftwareTypePluginThatHasUnannotatedMethods().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'sensible error when model types do not match in software type declaration'() {
        given:
        withSoftwareTypePluginWithMismatchedModelTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-type'.")
        failure.assertHasCause("A problem was found with the SoftwareTypeImplPlugin plugin.")
        failure.assertThatCause(containsText("Type 'org.gradle.test.SoftwareTypeImplPlugin' property 'testSoftwareTypeExtension' has @SoftwareType annotation with public type 'AnotherSoftwareTypeExtension' used on property of type 'TestSoftwareTypeExtension'."))
    }

    def 'sensible error when a software type plugin is registered that does not expose a software type'() {
        given:
        withSoftwareTypePluginThatDoesNotExposeSoftwareTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        when:
        fails(":help")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-type'.")
        failure.assertHasCause("A problem was found with the SoftwareTypeImplPlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.SoftwareTypeImplPlugin' is registered as a software type plugin but does not expose a software type.")
    }

    def 'sensible error when a software type plugin is registered that exposes multiple software types'() {
        given:
        withSoftwareTypePluginThatExposesMultipleSoftwareTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType + """
            anotherSoftwareType {
                id = "test2"

                foo {
                    bar = "fizz"
                }
            }
        """

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-type'.")
        failure.assertHasCause("A problem was found with the SoftwareTypeImplPlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.SoftwareTypeImplPlugin' is registered as a software type plugin, but it exposes multiple software types.")
    }

    def 'sensible error when a software type plugin exposes a private software type'() {
        given:
        withSoftwareTypePluginThatExposesPrivateSoftwareType().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("Failed to apply plugin class 'org.gradle.test.SoftwareTypeImplPlugin'.")
        failure.assertHasCause("Could not create an instance of type org.gradle.test.SoftwareTypeImplPlugin\$AnotherSoftwareTypeExtension.")
        failure.assertHasCause("Class SoftwareTypeImplPlugin.AnotherSoftwareTypeExtension is private.")
    }

    def 'can declare a software type plugin that registers its own extension'() {
        given:
        withSoftwareTypePluginThatRegistersItsOwnExtension().prepareToExecute()

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

    def 'sensible error when software type plugin declares that it registers its own extension but does not'() {
        given:
        withSoftwareTypePluginThatFailsToRegistersItsOwnExtension().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("A problem was found with the SoftwareTypeImplPlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.SoftwareTypeImplPlugin' property 'testSoftwareTypeExtension' has @SoftwareType annotation with 'disableModelManagement' set to true, but no extension with name 'testSoftwareType' was registered.")
    }

    def 'sensible error when software type plugin declares that it registers its own extension but registers the wrong object'() {
        given:
        withSoftwareTypePluginThatRegistersTheWrongExtension().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("A problem was found with the SoftwareTypeImplPlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.SoftwareTypeImplPlugin' property 'testSoftwareTypeExtension' has @SoftwareType annotation with 'disableModelManagement' set to true, but the extension with name 'testSoftwareType' does not match the value of the property.")
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy can use a property value on the assignment RHS")
    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Kotlin can use a property value on the assignment RHS")
    def 'sensible error when declarative script uses a property as value for another property'() {
        given:
        withSoftwareTypePluginThatRegistersItsOwnExtension().prepareToExecute()

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

    static String getPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
        """
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
