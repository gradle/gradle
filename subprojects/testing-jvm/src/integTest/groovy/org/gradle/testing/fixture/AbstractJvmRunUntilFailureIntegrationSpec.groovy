/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.fixture

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

import static org.gradle.testing.fixture.JvmBlockingTestClassGenerator.OTHER_RESOURCE

abstract class AbstractJvmRunUntilFailureIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    JvmBlockingTestClassGenerator generator

    def setup() {
        server.start()
        generator = new JvmBlockingTestClassGenerator(testDirectory, server, testAnnotationClass(), testDependency(), testFrameworkConfiguration())
    }

    def "runs tests n times without failure"() {
        given:
        buildFile.text = generator.initBuildFile()
        buildFile << buildConfig
//        generator.withFailingTest()
        generator.withNonfailingTest()
        def testExecution = server.expectAndBlock(OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks(taskList).start()
        testExecution.waitForAllPendingCalls()

        then:
//        testExecution.release(FAILED_RESOURCE)
        for (int i = 0; i < 5; i++) {
            testExecution.release(OTHER_RESOURCE)
        }
        gradleHandle.waitForExit()
        def result = new DefaultTestExecutionResult(testDirectory)
//        result.testClass('pkg.FailedTest').assertTestFailed('failTest', CoreMatchers.anything())
        result.testClass('pkg.OtherTest').assertTestCount(5, 0, 0)


        where:
        description         | taskList | buildConfig
        'run test 5 times'  | ['test'] | 'test { untilFailureRunCount = 5 }'
//        'run test 10 times' | ['test'] | 'test { untilFailureRunCount = 10 }'
    }

    abstract String testAnnotationClass()
    abstract String testDependency()
    abstract String testFrameworkConfiguration()
}
