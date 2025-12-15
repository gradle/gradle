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
import org.gradle.internal.declarativedsl.DeclarativeTestUtils
import org.gradle.internal.declarativedsl.settings.ProjectFeatureFixture
import org.gradle.test.fixtures.dsl.GradleDsl

@PolyglotDslTest
class ProjectFeatureSafetyIntegrationTest extends AbstractIntegrationSpec implements ProjectFeatureFixture, PolyglotTestFixture {
    def setup() {
        file("gradle.properties") << "org.gradle.kotlin.dsl.dcl=true"
    }

    def 'can declare and configure a custom project feature with an unsafe definition'() {
        given:
        def pluginBuilder = withUnsafeProjectFeatureDefinitionDeclaredUnsafe()
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        succeeds(":printProjectTypeDefinitionConfiguration", ":printFeatureDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'sensible error when definition is declared safe but is not an interface'() {
        given:
        def pluginBuilder = withUnsafeProjectFeatureDefinitionDeclaredSafe()
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printFeatureDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'feature' has a definition type which was declared safe but has the following issues:\n" +
            "  - Project feature 'feature' has a definition with type 'FeatureDefinition' which was declared safe but is not an interface.\n" +
            "    \n" +
            "    Reason: Safe definition types must be an interface.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Refactor the type as an interface."
        )
    }

    def 'sensible error when definition is declared safe but has an injected service'() {
        given:
        def pluginBuilder = withProjectFeatureAndInjectableDefinitionDeclaredSafe()
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printFeatureDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'feature' has a definition type which was declared safe but has the following issues:\n" +
            "  - The definition type has @Inject annotated property 'objects' in type 'FeatureDefinition'.\n" +
            "    \n" +
            "    Reason: Safe definition types cannot inject services.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Remove the @Inject annotation from the 'objects' property."
        )
    }

    def 'sensible error when definition is declared safe but has a nested property with an injected service'() {
        given:
        def pluginBuilder = withProjectFeatureAndNestedInjectableDefinitionDeclaredSafe()
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printFeatureDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'feature' has a definition type which was declared safe but has the following issues:\n" +
            "  - The definition type has @Inject annotated property 'objects' in type 'Fizz'.\n" +
            "    \n" +
            "    Reason: Safe definition types cannot inject services.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Remove the @Inject annotation from the 'objects' property."
        )
    }

    def 'sensible error when definition is declared safe but has multiple properties with an injected service'() {
        given:
        def pluginBuilder = withProjectFeatureAndMultipleInjectableDefinition()
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printFeatureDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'feature' has a definition type which was declared safe but has the following issues:\n" +
            "  - The definition type has @Inject annotated property 'objects' in type 'Fizz'.\n" +
            "    \n" +
            "    Reason: Safe definition types cannot inject services.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Remove the @Inject annotation from the 'objects' property.\n" +
            "  - The definition type has @Inject annotated property 'objects' in type 'FeatureDefinition'.\n" +
            "    \n" +
            "    Reason: Safe definition types cannot inject services.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Remove the @Inject annotation from the 'objects' property."
        )
    }

    def 'sensible error when definition is declared safe but inherits an injected service'() {
        given:
        def pluginBuilder = withProjectFeatureAndInjectableParentDefinitionDeclaredSafe()
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printFeatureDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'feature' has a definition type which was declared safe but has the following issues:\n" +
            "  - The definition type has @Inject annotated property 'objects' in type 'FeatureDefinition'.\n" +
            "    \n" +
            "    Reason: Safe definition types cannot inject services.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Remove the @Inject annotation from the 'objects' property."
        )
    }

    def 'sensible error when definition is declared safe but has several different errors'() {
        given:
        def pluginBuilder = withPolyUnsafeProjectFeatureDefinitionDeclaredSafe()
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        fails(":printFeatureDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure,
            "Project feature 'feature' has a definition type which was declared safe but has the following issues:\n" +
            "  - Project feature 'feature' has a definition with type 'FeatureDefinition' which was declared safe but is not an interface.\n" +
            "    \n" +
            "    Reason: Safe definition types must be an interface.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Refactor the type as an interface.\n" +
            "  - The definition type has @Inject annotated property 'objects' in type 'FeatureDefinition'.\n" +
            "    \n" +
            "    Reason: Safe definition types cannot inject services.\n" +
            "    \n" +
            "    Possible solutions:\n" +
            "      1. Mark the definition as unsafe.\n" +
            "      2. Remove the @Inject annotation from the 'objects' property."
        )
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
                }
            }
        """
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("definition text = foo")
        outputContains("model text = foo")
    }

    private String getPluginBuildScriptForJava() {
        return """

            tasks.withType(JavaCompile).configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = "1.8"
            }
        """
    }

    void assertDescriptionOrCause(ExecutionFailure failure, String expectedMessage) {
        if (currentDsl() == GradleDsl.DECLARATIVE) {
            failure.assertHasDescription(expectedMessage)
        } else {
            failure.assertHasCause(expectedMessage)
        }
    }
}
