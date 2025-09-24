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
import org.gradle.internal.declarativedsl.DeclarativeTestUtils
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.hamcrest.Matchers
import org.junit.Rule

@PolyglotDslTest
class SoftwareFeatureDeclarationIntegrationTest extends AbstractIntegrationSpec implements SoftwareFeatureFixture, PolyglotTestFixture {

    @Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Rule
    MavenHttpPluginRepository mavenHttpRepo = new MavenHttpPluginRepository(mavenRepo)

    def setup() {
        file("gradle.properties") << "org.gradle.kotlin.dsl.dcl=true"

        // We only need the test plugin portal for one test, but we need the actual plugin portal for
        // other tests, so we stop it by default and start it only when needed.
        pluginPortal.stop()
    }

    def 'can declare and configure a custom software feature from included build'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareFeaturePlugins()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
    }

    def 'can declare and configure a custom software feature from published plugin'() {
        given:
        pluginPortal.start()
        PluginBuilder pluginBuilder = withSoftwareFeaturePlugins()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        settingsFile() << """
            plugins {
                id("com.example.test-software-ecosystem").version("1.0")
            }
        """

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
    }

    def 'can declare and configure a custom software feature from plugin published to a custom repository'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareFeaturePlugins()
        pluginBuilder.publishAs("com", "example", "1.0", mavenHttpRepo, createExecuter()).allowAll()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava

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

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
    }

    @Requires(UnitTestPreconditions.Jdk23OrEarlier) // Because Kotlin does not support 24 yet and falls back to 23 causing inconsistent JVM targets
    def "can declare and configure a custom software feature in Kotlin"() {
        PluginBuilder pluginBuilder = withKotlinSoftwareFeaturePlugins()
        pluginBuilder.applyBuildScriptPlugin("org.jetbrains.kotlin.jvm", "2.1.0")
        pluginBuilder.addBuildScriptContent pluginBuildScriptForKotlin
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
    }

    def 'can apply multiple software features to a target receiver'() {
        given:
        PluginBuilder pluginBuilder = withMultipleSoftwareFeaturePlugins()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testSoftwareType {
                id = "test"

                foo {
                    bar = "baz"
                }

                feature {
                    text = "foo"
                }
                anotherFeature {
                    text = "bar"
                }
            }
        """ << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration",":printAnotherFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
        outputContains("anotherFeature text = bar")

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
        outputContains("Binding AnotherFeatureDefinition")
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy has no problem with finding non-public methods/types ...")
    def 'can declare and configure a custom software feature with a definition that has public and implementation types'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareFeatureDefinitionThatHasPublicAndImplementationTypes()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")

        when:
        buildFile().text =  """
            testSoftwareType {
                feature {
                    nonPublicProperty = "can be set"
                }
            }
        """ << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl
        fails(":printFeatureDefinitionImplConfiguration")

        then:
        if (GradleDsl.KOTLIN == currentDsl()) {
            failure.assertThatDescription(Matchers.containsString("Unresolved reference 'nonPublicProperty'"))
        } else if (GradleDsl.DECLARATIVE == currentDsl()) {
            failure.assertThatCause(Matchers.containsString("Failed to interpret the declarative DSL file"))
            failure.assertThatCause(Matchers.containsString("unresolved reference 'nonPublicProperty'"))
        } else {
            throw new RuntimeException("Test wasn't meant to be run with " + currentDsl().languageCodeName + " DSL")
        }
    }

    def 'sensible error when a software feature plugin is registered that does not expose a software feature'() {
        given:
        withSoftwareFeaturePluginThatDoesNotExposeSoftwareFeatures().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        when:
        fails(":help")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-ecosystem'.")
        failure.assertHasCause("A problem was found with the NotASoftwareFeaturePlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.NotASoftwareFeaturePlugin' is registered as a software feature plugin but does not expose a software feature.")
    }

    def 'can declare and configure a custom software feature that binds to a build model'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareFeatureThatBindsToBuildModel()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
    }

    def 'can declare and configure a custom software feature that has a build model with public and implementation class types'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareFeatureBuildModelThatHasPublicAndImplementationTypes()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
        outputContains("feature model class: FeatureDefinition\$FeatureModelImpl")
    }

    def 'can declare and configure a custom feature that targets a nested definition of a software type'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareTypeAndFeatureThatBindsToNestedDefinition()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatAppliesFeatureToNestedBlock << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printFeatureDefinitionConfiguration")

        then:
        outputContains("feature text = foo BAR")
    }


    private String getPluginBuildScriptForJava() {
        return """

            tasks.withType(JavaCompile).configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = "1.8"
            }
        """
    }

    private String getPluginBuildScriptForKotlin() {
        return """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget

            repositories {
                mavenCentral()
            }

            kotlin {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_1_8
                }
            }

            ${pluginBuildScriptForJava}
        """
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareFeature() {
        return """
            testSoftwareType {
                id = "test"

                foo {
                    bar = "baz"
                }

                feature {
                    text = "foo"
                }
            }
        """
    }

    static String getDeclarativeScriptThatAppliesFeatureToNestedBlock() {
        return """
            testSoftwareType {
                id = "test"
                foo {
                    bar = "bar"
                    feature {
                        text = "foo"
                    }
                }
            }
        """
    }


    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("""id = test\nbar = baz""")
        outputContains("feature text = foo")
    }
}
