/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.initialization.BuildCancellationToken
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.gradle.testing.fixture.JvmBlockingTestClassGenerator
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.getJUNIT_JUPITER
import static org.gradle.testing.fixture.JvmBlockingTestClassGenerator.OTHER_RESOURCE

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterTestCancellationBuildOperationAdapterIntegrationTest extends AbstractTestingMultiVersionIntegrationTest implements JUnitJupiterMultiVersionTest {
    def operations = new BuildOperationsFixture(executer, temporaryFolder)
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    JvmBlockingTestClassGenerator generator

    def setup() {
        server.start()
        generator = new JvmBlockingTestClassGenerator(testDirectory, server, testFrameworkImports, testFrameworkDependencies, configureTestFramework)
    }

    def "cancelling while tests are running doesn't result in dangling build operations"() {
        given:
        buildFile.text = generator.initBuildFile()
        settingsFile << """
            plugins {
                id('com.gradle.develocity').version("3.17.1")
            }
        """
        file('src/test/java/pkg/OtherTest.java') << """
            package pkg;
            $testFrameworkImports
            public class OtherTest {
                @Test
                public void passingTest() {
                    ${server.callFromBuild("$OTHER_RESOURCE")}
                    new java.math.BigInteger(5000, 999, new java.util.Random());
                }
            }
        """.stripIndent()
        file('src/test/java/pkg/OtherTest2.java') << """
            package pkg;
            $testFrameworkImports
            public class OtherTest2 {
                @Test
                public void passingTest() {
                    ${server.callFromBuild("$OTHER_RESOURCE")}
                    new java.math.BigInteger(5000, 999, new java.util.Random());
                }
            }
        """.stripIndent()

        buildFile << """
            tasks.withType(Test) {
                develocity.testDistribution {
                    enabled = true
                }
                maxParallelForks = 2
                def cancellationToken = gradle.services.get(${BuildCancellationToken.class.name})
                doFirst {
                    new Thread() {
                        @Override
                        void run() {
                            ${server.callFromBuild("ready-to-cancel")}
                            cancellationToken.cancel()
                        }
                    }.start()
                }
            }
        """

        when:
        def testExecution = server.expectConcurrentAndBlock(OTHER_RESOURCE, OTHER_RESOURCE, "ready-to-cancel")
        def gradleHandle = executer.withTasks("test", "--info", "-S").start()
        testExecution.waitForAllPendingCalls()
        testExecution.releaseAll()
        gradleHandle.waitForExit()
        Thread.sleep(2000)

        then:
//        operations.danglingChildren.empty
        operations.all(ExecuteTestBuildOperationType).size() == 7

        where:
        i << (1..10)
    }
}
