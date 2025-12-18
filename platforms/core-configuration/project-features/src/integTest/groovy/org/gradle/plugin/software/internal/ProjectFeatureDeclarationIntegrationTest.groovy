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
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.internal.declarativedsl.DeclarativeTestUtils
import org.gradle.internal.declarativedsl.settings.ProjectFeatureFixture
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.ToBeImplemented
import org.hamcrest.Matchers
import org.junit.Rule

@PolyglotDslTest
class ProjectFeatureDeclarationIntegrationTest extends AbstractIntegrationSpec implements ProjectFeatureFixture, PolyglotTestFixture {

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

    def 'can declare and configure a custom project feature from included build'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
    }

    def 'can declare and configure a custom project feature from published plugin'() {
        given:
        pluginPortal.start()
        PluginBuilder pluginBuilder = withProjectFeature()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        settingsFile() << """
            plugins {
                id("com.example.test-software-ecosystem").version("1.0")
            }
        """

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
    }

    def 'can declare and configure a custom project feature from plugin published to a custom repository'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeature()
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

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
    }

    @Requires(UnitTestPreconditions.Jdk23OrEarlier) // Because Kotlin does not support 24 yet and falls back to 23 causing inconsistent JVM targets
    def "can declare and configure a custom project feature in Kotlin"() {
        PluginBuilder pluginBuilder = withKotlinProjectFeaturePlugins()
        pluginBuilder.applyBuildScriptPlugin("org.jetbrains.kotlin.jvm", "2.2.20")
        pluginBuilder.addBuildScriptContent pluginBuildScriptForKotlin
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
    }

    def 'can apply multiple project features to a target receiver'() {
        given:
        PluginBuilder pluginBuilder = withMultipleProjectFeaturePlugins()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }

                feature {
                    text = "foo"
                    fizz {
                        buzz = "baz"
                    }
                }
                anotherFeature {
                    text = "bar"
                    fizz {
                        buzz = "baz"
                    }
                }
            }
        """ << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        when:
        run(":printAnotherFeatureDefinitionConfiguration")

        then:
        outputContains("definition text = bar")
        outputContains("definition fizz.buzz = baz")

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
        outputContains("Binding AnotherFeatureDefinition")
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy has no problem with finding non-public methods/types ...")
    def 'can declare and configure a custom project feature with a definition that has public and implementation types'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeatureDefinitionThatHasPublicAndImplementationTypes()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")

        when:
        buildFile().text =  """
            testProjectType {
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

    def 'sensible error when a project feature plugin is registered that does not expose a project feature'() {
        given:
        withProjectFeaturePluginThatDoesNotExposeProjectFeatures().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        when:
        fails(":help")

        then:
        failure.assertHasCause("Failed to apply plugin 'com.example.test-software-ecosystem'.")
        failure.assertHasCause("A problem was found with the NotAProjectFeaturePlugin plugin.")
        failure.assertHasCause("Type 'org.gradle.test.NotAProjectFeaturePlugin' is registered as a project feature plugin but does not expose a project feature.")
    }

    def 'sensible error when two plugins register features with the same name'() {
        given:
        PluginBuilder pluginBuilder = withTwoProjectFeaturesThatHaveTheSameName()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":help")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'feature' is registered by multiple plugins:\n" +
            "  - Project feature 'feature' is registered by both 'org.gradle.test.AnotherProjectFeatureImplPlugin' and 'org.gradle.test.ProjectFeatureImplPlugin'.\n" +
            "    \n" +
            "    Reason: A project feature or type with a given name can only be registered by a single plugin.\n" +
            "    \n" +
            "    Possible solution: Remove one of the plugins from the build."
        )
    }

    def 'can declare and configure a custom project feature that binds to a build model'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeatureThatBindsToBuildModel()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
    }

    def 'can declare and configure a custom project feature that has a build model with public and implementation class types'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeatureBuildModelThatHasPublicAndImplementationTypes()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeatureTextProperty << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printProjectTypeDefinitionConfiguration",":printFeatureDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("definition text = foo")
        outputContains("model text = foo")

        and:
        outputContains("Applying ProjectTypeImplPlugin")
        outputContains("Binding TestProjectTypeDefinition")
        outputContains("Binding FeatureDefinition")
        outputContains("feature model class: FeatureDefinition\$FeatureModelImpl")
    }

    def 'can declare and configure a custom feature that targets a nested definition of a project type'() {
        given:
        PluginBuilder pluginBuilder = withProjectTypeAndFeatureThatBindsToNestedDefinition()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatAppliesFeatureToNestedBlock << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printFeatureDefinitionConfiguration")

        then:
        outputContains("model text = foo BAR")
    }

    @ToBeImplemented
    def 'can declare a custom project feature with no build model'() {
        given:
        PluginBuilder pluginBuilder = withProjectFeatureThatHasNoBuildModel()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeatureTextProperty << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printProjectTypeDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure, "Cannot determine build model type for interface org.gradle.test.FeatureDefinition")
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

    static String getDeclarativeScriptThatConfiguresOnlyTestProjectFeature() {
        return """
            testProjectType {
                id = "test"

                foo {
                    bar = "baz"
                }

                feature {
                    text = "foo"
                    fizz {
                        buzz = "baz"
                    }
                }
            }
        """
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestProjectFeatureTextProperty() {
        return """
            testProjectType {
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
            testProjectType {
                id = "test"
                foo {
                    bar = "bar"
                    feature {
                        text = "foo"
                        fizz {
                            buzz = "baz"
                        }
                    }
                }
            }
        """
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("definition text = foo")
        outputContains("definition fizz.buzz = baz")
        outputContains("model id = test")
        outputContains("model text = foo")
    }

    void assertDescriptionOrCause(ExecutionFailure failure, String expectedMessage) {
        if (currentDsl() == GradleDsl.DECLARATIVE) {
            failure.assertHasDescription(expectedMessage)
        } else {
            failure.assertHasCause(expectedMessage)
        }
    }
}
