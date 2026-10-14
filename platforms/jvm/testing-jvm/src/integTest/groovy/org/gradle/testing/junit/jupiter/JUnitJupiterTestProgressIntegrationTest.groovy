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

    def "discovered tests lower the execution progress, which rises as tests complete"() {
        given:
        executer.withConsole(ConsoleOutput.Rich)
        buildFile.text = generator.initBuildFile(1)
        def resources = generator.withNonfailingTests(3)
        def testExecution = server.expectConcurrentAndBlock(1, resources as String[])

        when:
        def gradle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            def percentages = executingPercentages(gradle)
            assert percentages.last() < percentages.max()
        }

        when:
        int afterDiscovery = executingPercentages(gradle).last()
        testExecution.release(1)
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert executingPercentages(gradle).last() > afterDiscovery
        }

        when:
        testExecution.release(1)
        testExecution.waitForAllPendingCalls()
        testExecution.release(1)
        gradle.waitForFinish()

        then:
        !gradle.standardOutput.contains("More progress was logged than there should be")
    }

    private static List<Integer> executingPercentages(GradleHandle gradle) {
        gradle.standardOutput.findAll(/(\d+)% EXECUTING/) { String match, String percentage -> percentage as int }
    }
}
