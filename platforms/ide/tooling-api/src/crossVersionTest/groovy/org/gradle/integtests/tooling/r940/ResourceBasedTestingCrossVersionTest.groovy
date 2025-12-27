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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.source.FileSource
import org.gradle.tooling.events.test.source.FilesystemSource
import org.gradle.tooling.model.gradle.GradleBuild

@TargetGradleVersion(">=9.4.0")
class  ResourceBasedTestingCrossVersionTest extends AbstractResourceBasedTestingCrossVersionTest {

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
            entryPointConfiguration(it).addProgressListener(events, OperationType.TASK, OperationType.TEST)."$execMethod"()
        }

        then:
        assertTestsFromAllDefinitionsExecuted()

        where:
        entryPoint             | entryPointConfiguration                                                    | execMethod
        'BuildLauncher'        | { ProjectConnection p -> p.newBuild().forTasks("test") }                   | 'run'
        'TestLauncher'         | { ProjectConnection p -> p.newTestLauncher().forTasks("test") }            | 'run'
        'ModelBuilder'         | { ProjectConnection p -> p.model(GradleBuild).forTasks("test") }           | 'get'
        'BuildActionExecuter'  | { ProjectConnection p -> p.action(new FetchIdeaModel()).forTasks("test") } | 'run'
    }

    def "can filter resource-based tests"() {
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
                         excludeEngines("junit-jupiter")
                     }
                 }
             }
         }

         // Ensure the definitions directory exists even if no definitions are added; otherwsie the task will fail with "Test definitions directory does not exist"
         project.layout.projectDirectory.file("$DEFAULT_DEFINITIONS_LOCATION").getAsFile().mkdirs()
     """
        writeTestDefinitions()

        when:
        withConnection {
            it.newTestLauncher()
                .addProgressListener(events, OperationType.TASK, OperationType.TEST)
                .withTestsFor { tests ->
                    tests.forTaskPath(':test').includePattern("$DEFAULT_DEFINITIONS_LOCATION/subSomeOtherTestSpec.rbt")
                }
                .run()
        }

        then:
        assertTestsFromSecondDefinitionsExecuted()
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
            it.newTestLauncher().addProgressListener(events, OperationType.TASK, OperationType.TEST).forTasks("test").run()
        }

        when:
        assertTestsFromAllDefinitionsExecuted()
        def test1 = events.operation('Test SomeTestSpec.rbt - foo')

        then:
        (test1.descriptor as JvmTestOperationDescriptor).source instanceof FilesystemSource

        when:
        events.clear()
        withConnection {
            it.newTestLauncher().addProgressListener(events, OperationType.TASK, OperationType.TEST).withTests(test1.descriptor as TestOperationDescriptor).run()
        }

        then:
        assertTestsFromAllDefinitionsExecuted()
        result.output.contains("Re-running resource-based tests is not supported via TestLauncher API. The ':test' task will be scheduled without further filtering.")
    }


    @ToolingApiVersion(">=8.0 <9.4.0")
    def "old client can run resource-based tests"() {
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
            it.newBuild().addProgressListener(events, OperationType.TASK, OperationType.TEST).forTasks("test").run()
        }

        then:
        assertTestsFromAllDefinitionsExecuted()
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
            it.newBuild().addProgressListener(events, OperationType.TASK, OperationType.TEST).forTasks("test").run()
        }

        then:
        assertTestsFromAllDefinitionsExecuted()
        def test1 = events.operation('Test SomeTestSpec.rbt - foo')
        def test2 = events.operation('Test SomeTestSpec.rbt - bar')
        def test3 = events.operation('Test subSomeOtherTestSpec.rbt - other')
        (test1.descriptor as JvmTestOperationDescriptor).source instanceof FileSource
        ((test1.descriptor as JvmTestOperationDescriptor).source as FileSource).file == file('src/test/definitions/SomeTestSpec.rbt')
        (test2.descriptor as JvmTestOperationDescriptor).source instanceof FileSource
        ((test2.descriptor as JvmTestOperationDescriptor).source as FileSource).file == file('src/test/definitions/SomeTestSpec.rbt')
        (test3.descriptor as JvmTestOperationDescriptor).source instanceof FileSource
        ((test3.descriptor as JvmTestOperationDescriptor).source as FileSource).file == file('src/test/definitions/subSomeOtherTestSpec.rbt')
    }
}
