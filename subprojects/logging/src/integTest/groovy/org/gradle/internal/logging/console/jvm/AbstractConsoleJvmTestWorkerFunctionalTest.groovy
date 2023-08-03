/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console.jvm

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf

import static org.gradle.internal.logging.console.jvm.TestedProjectFixture.JavaTestClass
import static org.gradle.internal.logging.console.jvm.TestedProjectFixture.containsTestExecutionWorkInProgressLine
import static org.gradle.internal.logging.console.jvm.TestedProjectFixture.testClass

@IgnoreIf({ GradleContextualExecuter.isParallel() })
abstract class AbstractConsoleJvmTestWorkerFunctionalTest extends AbstractIntegrationSpec {

    private static final int MAX_WORKERS = 2
    private static final String SERVER_RESOURCE_1 = 'test-1'
    private static final String SERVER_RESOURCE_2 = 'test-2'

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        executer.withArguments('--parallel', "--max-workers=$MAX_WORKERS")
        server.start()
    }

    def "shows test class execution #description test class name in work-in-progress area of console for single project build"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            tasks.withType(Test) {
                maxParallelForks = $MAX_WORKERS
            }

            ${testFrameworkConfiguration()}
        """

        file("src/test/java/${testClass1.fileRepresentation}") << testClass(testAnnotationClass(), testClass1.classNameWithoutPackage, SERVER_RESOURCE_1, server)
        file("src/test/java/${testClass2.fileRepresentation}") << testClass(testAnnotationClass(), testClass2.classNameWithoutPackage, SERVER_RESOURCE_2, server)
        def testExecution = server.expectConcurrentAndBlock(2, SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            containsTestExecutionWorkInProgressLine(gradleHandle, ':test', testClass1.renderedClassName)
            containsTestExecutionWorkInProgressLine(gradleHandle, ':test', testClass2.renderedClassName)
        }

        testExecution.release(2)
        gradleHandle.waitForFinish()

        where:
        testClass1                    | testClass2                    | description
        JavaTestClass.PRESERVED_TEST1 | JavaTestClass.PRESERVED_TEST2 | 'preserved'
        JavaTestClass.SHORTENED_TEST1 | JavaTestClass.SHORTENED_TEST2 | 'shortened'
    }

    def "shows test class execution #description test class name in work-in-progress area of console for multi-project build"() {
        given:
        settingsFile << "include 'project1', 'project2'"
        buildFile << """
            subprojects {
                apply plugin: 'java-library'

                ${RepoScriptBlockUtil.mavenCentralRepository()}

                tasks.withType(Test) {
                    maxParallelForks = $MAX_WORKERS
                }

                ${testFrameworkConfiguration()}
            }
        """
        file("project1/src/test/java/${testClass1.fileRepresentation}") << testClass(testAnnotationClass(), testClass1.classNameWithoutPackage, SERVER_RESOURCE_1, server)
        file("project2/src/test/java/${testClass2.fileRepresentation}") << testClass(testAnnotationClass(), testClass2.classNameWithoutPackage, SERVER_RESOURCE_2, server)
        def testExecution = server.expectConcurrentAndBlock(2, SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            containsTestExecutionWorkInProgressLine(gradleHandle, ':project1:test', testClass1.renderedClassName)
            containsTestExecutionWorkInProgressLine(gradleHandle, ':project2:test', testClass2.renderedClassName)
        }

        testExecution.release(2)
        gradleHandle.waitForFinish()

        where:
        testClass1                    | testClass2                    | description
        JavaTestClass.PRESERVED_TEST1 | JavaTestClass.PRESERVED_TEST2 | 'preserved'
        JavaTestClass.SHORTENED_TEST1 | JavaTestClass.SHORTENED_TEST2 | 'shortened'
    }

    abstract String testAnnotationClass()
    abstract String testFrameworkConfiguration()
}
