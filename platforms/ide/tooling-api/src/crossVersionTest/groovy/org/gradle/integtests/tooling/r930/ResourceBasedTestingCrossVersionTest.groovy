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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.ResourceBasedTestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor

@TargetGradleVersion(">=9.3.0")
class ResourceBasedTestingCrossVersionTest extends AbstractResourceBasedTestingCrossVersionTest {

    public static final DEFAULT_DEFINITIONS_LOCATION = "src/test/definitions"

    ProgressEvents events = ProgressEvents.create()
    @Override
    List<TestEngines> getEnginesToSetup() {
        // TODO (donat) the test engine should be reused from jvm-testing
        return [TestEngines.BASIC_RESOURCE_BASED]
    }

    def "Can launch non-class-based test with BuildLauncher"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")

                        options {
                            if ($excludingJupiter) {
                                excludeEngines("junit-jupiter")
                            }
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        withConnection {
            it.newBuild().addProgressListener(events, OperationType.TEST).forTasks("test").withArguments("--info").run()
        }

        then:
        events.tests.size() == 5 // task + executor + 3 tests
        def test1 = events.operation('Test SomeTestSpec.rbt : foo')
        def test2 = events.operation('Test SomeTestSpec.rbt : bar')
        def test3 = events.operation('Test subSomeOtherTestSpec.rbt : other')

        where:
        excludingJupiter << [true, false]
    }

    def "Can launch non-class-based test with TestLauncher"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")

                        options {
                            if ($excludingJupiter) {
                                excludeEngines("junit-jupiter")
                            }
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        withConnection {
            it.newTestLauncher().addProgressListener(events, OperationType.TEST).forTasks("test").withArguments("--info").run()
        }

        then:
        events.tests.size() == 5 // task + executor + 3 tests
        def test1 = events.operation('Test SomeTestSpec.rbt : foo')
        def test2 = events.operation('Test SomeTestSpec.rbt : bar')
        def test3 = events.operation('Test subSomeOtherTestSpec.rbt : other')

        where:
        excludingJupiter << [true, false]
    }

    @ToolingApiVersion(">=9.3.0")
    def "Can rerun resource-based test task based on descriptors but cannot filter for individual scenarios"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")

                        options {
                            if ($excludingJupiter) {
                                excludeEngines("junit-jupiter")
                            }
                        }
                    }
                }
            }
        """

        writeTestDefinitions()
        withConnection {
            it.newTestLauncher().addProgressListener(events, OperationType.TEST).forTasks("test").withArguments("--info").run()
        }

        when:
        def test1 = events.operation('Test SomeTestSpec.rbt : foo')

        then:
        test1.descriptor instanceof ResourceBasedTestOperationDescriptor

        when:
        events.clear()
        withConnection {
            it.newTestLauncher().addProgressListener(events, OperationType.TEST).withTests(test1.descriptor as TestOperationDescriptor).run()
        }

        then:
        events.tests.size() == 5 // task + executor + 3 test
        result.output.contains("Warning: Re-running resource-based tests is not supported via TestLauncher API. The ':test' task will be scheduled without further filtering.")

        where:
        excludingJupiter << [true, false]
    }


    @ToolingApiVersion(">=9.3.0")
    def "Receives detailed information about non-class-based test execution (excluding jupiter engine = #excludingJupiter)"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")

                        options {
                            if ($excludingJupiter) {
                                excludeEngines("junit-jupiter")
                            }
                        }
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        withConnection {
            it.newBuild().addProgressListener(events, OperationType.TEST).forTasks("test").withArguments("--info").run()
        }

        then:
        events.tests.size() == 5 // task + executor + 3 tests
        def test1 = events.operation('Test SomeTestSpec.rbt : foo')
        def test2 = events.operation('Test SomeTestSpec.rbt : bar')
        def test3 = events.operation('Test subSomeOtherTestSpec.rbt : other')

        test1.assertIsTest()
        test2.assertIsTest()
        test3.assertIsTest()

        test1.parent.descriptor.displayName.startsWith('Gradle Test Executor')
        test2.parent.descriptor.displayName.startsWith('Gradle Test Executor')
        test3.parent.descriptor.displayName.startsWith('Gradle Test Executor')
        test1.parent.parent == events.operation('Gradle Test Run :test')

        // TODO (donat) test what happens when an old TAPI version is used.
        test1.descriptor instanceof ResourceBasedTestOperationDescriptor
        test2.descriptor instanceof ResourceBasedTestOperationDescriptor
        test3.descriptor instanceof ResourceBasedTestOperationDescriptor

        (test1.descriptor as ResourceBasedTestOperationDescriptor).resourcePath == file('src/test/definitions/SomeTestSpec.rbt').absolutePath
        (test2.descriptor as ResourceBasedTestOperationDescriptor).resourcePath == file('src/test/definitions/SomeTestSpec.rbt').absolutePath
        (test3.descriptor as ResourceBasedTestOperationDescriptor).resourcePath == file('src/test/definitions/subSomeOtherTestSpec.rbt').absolutePath

        where:
        excludingJupiter << [true, false]
    }
}
