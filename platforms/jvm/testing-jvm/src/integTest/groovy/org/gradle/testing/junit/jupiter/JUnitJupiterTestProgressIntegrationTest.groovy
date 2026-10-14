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

package org.gradle.testing.junit.jupiter

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.fixture.JvmBlockingTestClassGenerator
import org.junit.Rule

@TargetCoverage({ JUnitCoverage.JUNIT_JUPITER })
class JUnitJupiterTestProgressIntegrationTest extends AbstractTestingMultiVersionIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    JvmBlockingTestClassGenerator generator

    def setup() {
        server.start()
        generator = new JvmBlockingTestClassGenerator(testDirectory, server, testFrameworkImports, testFrameworkDependencies, configureTestFramework)
    }

    def "counts discovered tests as queued and running tests as running"() {
        given:
        executer.withConsole(ConsoleOutput.Rich)
        buildFile.text = generator.initBuildFile(1)
        def resources = generator.withNonfailingTests(3)
        def testExecution = server.expectConcurrentAndBlock(1, resources as String[])

        when:
        def gradle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        // The test task and the first test run, the other discovered tests are queued
        assertLastExecutingCounts(gradle, "EXECUTING ▶ 2 … 2")

        when:
        testExecution.release(1)
        testExecution.waitForAllPendingCalls()

        then:
        assertLastExecutingCounts(gradle, "EXECUTING ▶ 2 … 1")

        when:
        testExecution.release(1)
        testExecution.waitForAllPendingCalls()

        then:
        assertLastExecutingCounts(gradle, "EXECUTING ▶ 2 … 0")

        when:
        testExecution.release(1)
        gradle.waitForFinish()

        then:
        !gradle.standardOutput.contains("More progress was logged than there should be")
    }

    private static void assertLastExecutingCounts(GradleHandle gradle, String expected) {
        ConcurrentTestUtil.poll {
            def plain = gradle.standardOutput.replaceAll(/\u001B\[[0-9;?]*[A-Za-z]|\u001B\][^\u0007]*\u0007/, '')
            def counts = plain.findAll(/EXECUTING\s+▶\s+[\d,]+\s+…\s+[\d,]+/)
            assert !counts.empty
            assert counts.last().replaceAll(/\s+/, ' ') == expected
        }
    }
}
