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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

import static org.gradle.internal.logging.console.jvm.TestedProjectFixture.testClass

class ConsoleTestNGUnsupportedTestWorkerFunctionalTest extends AbstractIntegrationSpec {

    private static final int MAX_WORKERS = 2
    private static final String SERVER_RESOURCE_1 = 'test-1'
    private static final String SERVER_RESOURCE_2 = 'test-2'
    private static final String TESTNG_VERSION = '6.3.1'
    private static final String TESTNG_ANNOTATION = 'org.testng.annotations.Test'
    private static final String TEST_CLASS_1 = 'Test1'
    private static final String TEST_CLASS_2 = 'Test2'

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        executer.withConsole(ConsoleOutput.Rich)
        executer.withArguments('--parallel', "--max-workers=$MAX_WORKERS")
        server.start()
    }

    def "omits parallel test execution if TestNG version does not emit class listener events"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useTestNG('$TESTNG_VERSION')
                targets.test.testTask.configure {
                    maxParallelForks = $MAX_WORKERS
                }
            }
        """
        file("src/test/java/${TEST_CLASS_1}.java") << testClass(TESTNG_ANNOTATION, TEST_CLASS_1, SERVER_RESOURCE_1, server)
        file("src/test/java/${TEST_CLASS_2}.java") << testClass(TESTNG_ANNOTATION, TEST_CLASS_2, SERVER_RESOURCE_2, server)
        server.expectConcurrent(SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        run('test')

        then:
        outputDoesNotContain(TEST_CLASS_1)
        outputDoesNotContain(TEST_CLASS_2)
    }
}
