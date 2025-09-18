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

package org.gradle.internal.declarativedsl.settings

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

import static org.gradle.internal.declarativedsl.DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl
import static org.gradle.util.Matchers.containsText

@PolyglotDslTest
class LegacySoftwareTypeDeclarationIntegrationTest extends AbstractIntegrationSpec implements LegacySoftwareTypeFixture, PolyglotTestFixture {
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
        withLegacySoftwareTypePlugins().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

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
        def pluginBuilder = withLegacySoftwareTypePlugins()
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        settingsFile() << """
            plugins {
                id("com.example.test-software-ecosystem").version("1.0")
            }
        """

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    def 'can declare and configure a custom software type from a parent class'() {
        given:
        withLegacySoftwareTypePluginThatExposesSoftwareTypeFromParentClass().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'can declare and configure a custom software type from a plugin with unannotated methods'() {
        given:
        withLegacySoftwareTypePluginThatHasUnannotatedMethods().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'sensible error when model types do not match in software type declaration'() {
        given:
        withLegacySoftwareTypePluginWithMismatchedModelTypes().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-ecosystem'.")
        failure.assertHasCause("A problem was found with the SoftwareTypeImplPlugin plugin.")
        failure.assertThatCause(containsText("Type 'org.gradle.test.SoftwareTypeImplPlugin' property 'testSoftwareTypeExtension' has @SoftwareType annotation with public type 'AnotherSoftwareTypeExtension' used on property of type 'TestSoftwareTypeExtension'."))
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "inaccessible type exposed in the DSL")
    def 'sensible error when a software type plugin exposes a private software type'() {
        given:
        withLegacySoftwareTypePluginThatExposesPrivateSoftwareType().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("Failed to apply plugin class 'org.gradle.test.SoftwareTypeImplPlugin'.")
        failure.assertHasCause("Could not create an instance of type org.gradle.test.SoftwareTypeImplPlugin\$AnotherSoftwareTypeExtension.")
        failure.assertHasCause("Class SoftwareTypeImplPlugin.AnotherSoftwareTypeExtension is private.")
    }

    def 'can declare a software type plugin that registers its own extension'() {
        given:
        withLegacySoftwareTypePluginThatRegistersItsOwnExtension().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "inaccessible type exposed in the DSL")
    def 'sensible error when software type plugin declares that it registers its own extension but does not'() {
        given:
        withLegacySoftwareTypePluginThatFailsToRegistersItsOwnExtension().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failure.assertHasCause("A problem was found with the SoftwareTypeImplPlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.SoftwareTypeImplPlugin' property 'testSoftwareTypeExtension' has @SoftwareType annotation with 'disableModelManagement' set to true, but no extension with name 'testSoftwareType' was registered.")
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "inaccessible type exposed in the DSL")
    def 'sensible error when software type plugin declares that it registers its own extension but registers the wrong object'() {
        given:
        withLegacySoftwareTypePluginThatRegistersTheWrongExtension().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareType << nonDeclarativeSuffixForKotlinDsl

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
        withLegacySoftwareTypePluginThatRegistersItsOwnExtension().prepareToExecute()

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
