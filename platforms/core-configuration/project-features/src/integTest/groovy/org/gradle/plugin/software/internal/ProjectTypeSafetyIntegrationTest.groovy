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
import org.gradle.internal.declarativedsl.settings.ProjectTypeFixture
import org.gradle.test.fixtures.dsl.GradleDsl

@PolyglotDslTest
class ProjectTypeSafetyIntegrationTest extends AbstractIntegrationSpec implements ProjectTypeFixture, PolyglotTestFixture {
    def setup() {
        // enable DCL support to have KTS accessors generated
        propertiesFile << "org.gradle.kotlin.dsl.dcl=true"
    }

    def 'can declare and configure a custom project type with an unsafe definition'() {
        given:
        withUnsafeProjectTypeDefinitionDeclaredUnsafe().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        succeeds(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'sensible error when definition is declared safe but is not an interface'() {
        given:
        withUnsafeProjectTypeDefinitionDeclaredSafe().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure, "Safe project feature 'testProjectType' must have an interface as definition type")
    }

    def 'sensible error when definition is declared safe but has an injected service'() {
        given:
        withSafeProjectTypeAndInjectableDefinition().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure, "Safe project feature 'testProjectType' definition type must not have @Inject annotated properties: objects in type TestProjectTypeDefinition")
    }

    def 'sensible error when definition is declared safe but has a nested property with an injected service'() {
        given:
        withSafeProjectTypeAndNestedInjectableDefinition().prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestProjectType

        when:
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        assertDescriptionOrCause(failure, "Safe project feature 'testProjectType' definition type must not have @Inject annotated properties: objects in type Foo")
    }

    void assertDescriptionOrCause(ExecutionFailure failure, String expectedMessage) {
        if (currentDsl() == GradleDsl.DECLARATIVE) {
            failure.assertHasDescription(expectedMessage)
        } else {
            failure.assertHasCause(expectedMessage)
        }
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
