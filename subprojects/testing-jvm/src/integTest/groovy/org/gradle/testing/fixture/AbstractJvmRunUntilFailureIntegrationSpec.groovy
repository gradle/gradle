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
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

abstract class AbstractJvmRunUntilFailureIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    JvmBlockingTestClassGenerator generator

    def setup() {
        server.start()
        generator = new JvmBlockingTestClassGenerator(testDirectory, server, testAnnotationClass(), testDependency(), testFrameworkConfiguration())
    }

    def "runs tests #untilFailureRunCount times without failure"() {
        given:
        buildFile.text = initBuildFile()
        buildFile << "test { untilFailureRunCount = $untilFailureRunCount }"
        file('src/test/java/pkg/OtherTest.java') << """
            package pkg;
            import ${testAnnotationClass()};
            public class OtherTest {
                @Test
                public void passingTest() {
                    System.out.println("passingTest");
                }
            }
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('pkg.OtherTest').assertTestCount(untilFailureRunCount, 0, 0)

        where:
        untilFailureRunCount << [1, 5, 10]
    }

    def "stops running tests after first failure"() {
        given:
        File runCounter = temporaryFolder.createFile("run-count.txt")
        runCounter.text = "0"
        buildFile.text = initBuildFile()
        buildFile << "test { untilFailureRunCount = $untilFailureRunCount }"
        file('src/test/java/pkg/OtherTest.java') << """
            package pkg;
            import java.io.*;
            import java.nio.file.*;
            import ${testAnnotationClass()};
            public class OtherTest {
                @Test
                public void randomlyFailingTest() throws IOException {
                    String previousRun = Files.readAllLines(Paths.get("${runCounter.absolutePath}")).get(0);
                    int runNumber = Integer.parseInt(previousRun) + 1;
                    if (runNumber >= $failOnRun) {
                        throw new RuntimeException("failure");
                    }
                    Files.write(Paths.get("${runCounter.absolutePath}"), Integer.toString(runNumber).getBytes());
                    System.out.println("passingTest");
                }
            }
        """.stripIndent()

        when:
        fails('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('pkg.OtherTest').assertTestCount(failOnRun, 1, 0)

        where:
        untilFailureRunCount | failOnRun
        5                    | 1
        5                    | 3
        5                    | 5
    }

    String initBuildFile() {
        return """
            apply plugin: 'java'

            ${RepoScriptBlockUtil.mavenCentralRepository()}

            dependencies {
                testImplementation '${testDependency()}'
            }

            ${testFrameworkConfiguration()}
        """
    }

    abstract String testAnnotationClass()
    abstract String testDependency()
    abstract String testFrameworkConfiguration()
}
