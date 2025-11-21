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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.TestSpecs
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.FileSource
import org.gradle.tooling.events.test.ResourceBasedTestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.model.gradle.GradleBuild
import testengines.TestEnginesFixture

@TargetGradleVersion(">=9.4.0")
class ResourceBasedTestingCrossVersionTest extends AbstractResourceBasedTestingCrossVersionTest {

    public static final DEFAULT_DEFINITIONS_LOCATION = "src/test/definitions"

    ProgressEvents events = ProgressEvents.create()
    @Override
    List<TestEnginesFixture.TestEngines> getEnginesToSetup() {
        return [TestEnginesFixture.TestEngines.BASIC_RESOURCE_BASED]
    }

    def "can launch resource-based test with #entryPoint"() {
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
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        withConnection {
            entryPointConfiguration(it).addProgressListener(events, OperationType.TEST)."$execMethod"()
        }

        then:
        events.tests.size() == 5 // task + executor + 3 tests
        events.operation('Test SomeTestSpec.rbt : foo')
        events.operation('Test SomeTestSpec.rbt : bar')
        events.operation('Test subSomeOtherTestSpec.rbt : other')

        where:
        entryPoint             | entryPointConfiguration                                                    | execMethod
        'BuildLauncher'        | { ProjectConnection p -> p.newBuild().forTasks("test") }                   | 'run'
        'TestLauncher'         | { ProjectConnection p -> p.newTestLauncher().forTasks("test") }            | 'run'
        'ModelBuilder'         | { ProjectConnection p -> p.model(GradleBuild).forTasks("test") }           | 'get'
        'BuildActionExecuter'  | { ProjectConnection p -> p.action(new FetchIdeaModel()).forTasks("test") } | 'run'
    }

    def "filtering class-based filters with #filterType in TestLauncher will prevent scanning for resource-based tests"() {
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
                    }
                }
            }
        """
        writeTestDefinitions()
        writeTestClasses()

        when:
        withConnection {
            TestLauncher testLauncher = it.newTestLauncher().addProgressListener(events, OperationType.TEST)
            testConfiguration(testLauncher)
            testLauncher.run()
        }

        then:
        events.tests.size() == 4 // task + executor + 1 test
        events.operation('Test class SomeTest')
        events.operation('Test testMethod()(SomeTest)')

        where:
        filterType               | testConfiguration
        'withJvmTestClasses'     | { TestLauncher tl -> tl.withJvmTestClasses('SomeTest') }
        'withJvmTestMethods'     | { TestLauncher tl -> tl.withJvmTestMethods('SomeTest', 'testMethod') }
        'withTaskAndTestClasses' | { TestLauncher tl -> tl.withTaskAndTestClasses(':test', ['SomeTest']) }
        'withTestsFor'           | { TestLauncher tl -> tl.withTestsFor { TestSpecs spec -> spec.forTaskPath(':test').includeMethod('SomeTest', 'testMethod') } }
    }

    @ToolingApiVersion(">=9.4.0")
    def "can rerun resource-based test task based on descriptors but cannot filter for individual scenarios"() {
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
                    }
                }
            }
        """

        writeTestDefinitions()

        withConnection {
            it.newTestLauncher().addProgressListener(events, OperationType.TEST).forTasks("test").run()
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
        result.output.contains("Re-running resource-based tests is not supported via TestLauncher API. The ':test' task will be scheduled without further filtering.")
    }


    @ToolingApiVersion(">=8.0 <9.4.0")
    def "old TAPI client can run resource-based tests"() {
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
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        withConnection {
            it.newBuild().addProgressListener(events, OperationType.TEST).forTasks("test").run()
        }

        then:
        events.tests.size() == 5 // task + executor + 3 tests
        def test1 = events.operation('Test SomeTestSpec.rbt : foo')
        def test2 = events.operation('Test SomeTestSpec.rbt : bar')
        def test3 = events.operation('Test subSomeOtherTestSpec.rbt : other')

        test1.assertIsTest()
        test2.assertIsTest()
        test3.assertIsTest()
    }

    @ToolingApiVersion(">=9.4.0")
    def "receives detailed information about resource-based test execution"() {
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
                    }
                }
            }
        """

        writeTestDefinitions()

        when:
        withConnection {
            it.newBuild().addProgressListener(events, OperationType.TEST).forTasks("test").run()
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

        test1.descriptor instanceof ResourceBasedTestOperationDescriptor
        test2.descriptor instanceof ResourceBasedTestOperationDescriptor
        test3.descriptor instanceof ResourceBasedTestOperationDescriptor

        (test1.descriptor as ResourceBasedTestOperationDescriptor).testSource instanceof FileSource  // == file('src/test/definitions/SomeTestSpec.rbt')
        ((test1.descriptor as ResourceBasedTestOperationDescriptor).testSource as FileSource).file == file('src/test/definitions/SomeTestSpec.rbt')
//        (test2.descriptor as ResourceBasedTestOperationDescriptor).file == file('src/test/definitions/SomeTestSpec.rbt')
//        (test3.descriptor as ResourceBasedTestOperationDescriptor).file == file('src/test/definitions/subSomeOtherTestSpec.rbt')
    }
}
