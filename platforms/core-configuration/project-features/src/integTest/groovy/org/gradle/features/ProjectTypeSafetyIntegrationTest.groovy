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

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.features.registration.TaskRegistrar
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.features.internal.TestScenarioFixture
import org.gradle.test.fixtures.dsl.GradleDsl

import static org.gradle.features.internal.builders.TypeShape.ABSTRACT_CLASS

@PolyglotDslTest
@SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy DSL is not supported for declarative configuration")
class ProjectTypeSafetyIntegrationTest extends AbstractIntegrationSpec implements TestScenarioFixture, PolyglotTestFixture {
    def setup() {
        // enable DCL support to have KTS accessors generated
        propertiesFile << "org.gradle.kotlin.dsl.dcl=true"
    }

    def 'can declare and configure a custom project type with an unsafe definition'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                    shape ABSTRACT_CLASS
                }
                plugin {
                    unsafeDefinition()
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
        succeeds(":printTestProjectTypeDefinitionConfiguration")

        then:
        outputContains("definition id = test")
        outputContains("definition foo.bar = baz")
        outputContains("model id = test")
    }

    def 'sensible error when definition is declared safe but is not an interface'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                    shape ABSTRACT_CLASS
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: non-interface type\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
    }

    def 'sensible error when definition is declared safe but has an injected service'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                    injectedService "objects", ObjectFactory
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: injected service property\n" +
                "      in schema property 'objects: ObjectFactory'\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
    }

    def 'sensible error when definition is declared safe but has a nested property with an injected service'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        injectedService "objects", ObjectFactory
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: injected service property\n" +
                "      in schema property 'objects: ObjectFactory'\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition.Foo'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
    }

    def 'sensible error when definition is declared safe but has multiple properties with an injected service'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    injectedService "objects", ObjectFactory
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        injectedService "objects", ObjectFactory
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: injected service property\n" +
                "      in schema property 'objects: ObjectFactory'\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: injected service property\n" +
                "      in schema property 'objects: ObjectFactory'\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition.Foo'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
    }

    def 'sensible error when definition is declared safe but inherits an injected service'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                    parentDefinition {
                        injectedService "objects", ObjectFactory
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: injected service property\n" +
                "      in schema property 'objects: ObjectFactory'\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
    }

    def 'sensible error when definition is declared safe but has several different errors'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                    shape ABSTRACT_CLASS
                    injectedService "objects", ObjectFactory
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: non-interface type\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: non-abstract member\n" +
                "      in schema property 'foo: Foo'\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
        failure.assertHasErrorOutput(
            "Unsafe declaration in safe definition: non-interface type\n" +
                "      in schema type 'org.gradle.test.TestProjectTypeDefinition.Foo'\n" +
                "      in safe feature definition of 'testProjectType' (plugin 'com.example.test-software-ecosystem')"
        )
    }

    def 'can declare and configure a custom project type with an unsafe apply action'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                }
                plugin {
                    applyAction {
                        injectedService "project", Project
                    }
                    unsafeApplyAction()
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
    }

    def 'sensible error when a project type with an unsafe apply action is declared safe'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                }
                plugin {
                    applyAction {
                        injectedService "project", Project
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasCause(
            "Project feature 'testProjectType' has a safe apply action that attempts to inject an unsafe service with type 'org.gradle.api.Project'.\n" +
            "\n" +
            "Reason: Only the following services are available in safe apply actions:\n" +
            "  - TaskRegistrar\n" +
            "  - ProjectFeatureLayout\n" +
            "  - ConfigurationRegistrar\n" +
            "  - ObjectFactory\n" +
            "  - ProviderFactory\n" +
            "  - DependencyFactory.\n" +
            "\n" +
            "Possible solutions:\n" +
            "  1. Mark the apply action as unsafe.\n" +
            "  2. Remove the 'org.gradle.api.Project' injection from the apply action."
        )
    }

    def 'sensible error when a project type with an unsafe apply action attempts to use an unknown service'() {
        given:
        testScenario {
            projectType("testProjectType") {
                definition {
                    property "id", String
                    buildModel {
                        property "id", String
                    }
                    property("foo", "Foo") {
                        property "bar", String
                    }
                }
                plugin {
                    applyAction {
                        injectedService "unknown", TaskRegistrar
                    }
                    unsafeApplyAction()
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
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failure.assertHasCause(
            "Project feature 'testProjectType' has an apply action that attempts to inject an unknown service with type 'org.gradle.test.TestProjectTypeImplPlugin\$ApplyAction\$UnknownService'.\n" +
            "\n" +
            "Reason: Services of type org.gradle.test.TestProjectTypeImplPlugin\$ApplyAction\$UnknownService are not available for injection into project feature apply actions.\n" +
            "\n" +
            "Possible solution: Remove the 'org.gradle.test.TestProjectTypeImplPlugin\$ApplyAction\$UnknownService' injection from the apply action."
        )
    }
}
