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

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

abstract class AbstractJvmRetryUntilStoppedIntegrationSpec extends AbstractIntegrationSpec {

    def "runs tests #retryUntilStoppedCount times without failure"() {
        given:
        buildFile.text = initBuildFile()
        buildFile << "test { retryUntilStoppedCount.set(${retryUntilStoppedCount}L) }"
        def testClassName = generateAlwaysPassingTestClass()

        when:
        succeeds('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass(testClassName).assertTestCount(retryUntilStoppedCount, 0, 0)

        where:
        retryUntilStoppedCount << [1, 5, 10]
    }

    def "runs tests #retryUntilStoppedCount with retry-until-stopped=#retryUntilStoppedCount parameter"() {
        given:
        buildFile.text = initBuildFile()
        def testClassName = generateAlwaysPassingTestClass()

        when:
        run 'test', "--retry-until-stopped", "$retryUntilStoppedCount"

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass(testClassName).assertTestCount(retryUntilStoppedCount, 0, 0)

        where:
        retryUntilStoppedCount << [1, 5, 10]
    }

    def "runs tests #retryUntilStoppedCount times with failure"() {
        given:
        buildFile.text = initBuildFile()
        buildFile << "test { retryUntilStoppedCount.set(${retryUntilStoppedCount}L) }"
        def failingClassName = generateFailingTestClass(failOnRun)

        when:
        fails('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass(failingClassName).assertTestCount(retryUntilStoppedCount, retryUntilStoppedCount - failOnRun + 1, 0)

        where:
        retryUntilStoppedCount | failOnRun
        10                     | 1
        10                     | 3
        10                     | 5
        10                     | 10
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

    private String generateFailingTestClass(int failOnRun) {
        File runCounter = temporaryFolder.createFile("run-count.txt")
        def runCounterPath = normaliseFileSeparators(runCounter.absolutePath)
        runCounter.text = "0"
        file('src/test/java/pkg/FailingTest.java') << """
            package pkg;
            import java.io.*;
            import java.nio.file.*;
            import ${testAnnotationClass()};
            public class FailingTest {
                @Test
                public void failingTest() throws IOException {
                    String previousRun = Files.readAllLines(Paths.get("${runCounterPath}")).get(0);
                    int runNumber = Integer.parseInt(previousRun) + 1;
                    if (runNumber >= $failOnRun) {
                        throw new RuntimeException("failure");
                    }
                    Files.write(Paths.get("${runCounterPath}"), Integer.toString(runNumber).getBytes());
                    System.out.println("passingTest");
                }
            }
        """.stripIndent()
        return "pkg.FailingTest"
    }

    private String generateAlwaysPassingTestClass() {
        file('src/test/java/pkg/AlwaysPassingTest.java') << """
            package pkg;
            import java.io.*;
            import java.nio.file.*;
            import ${testAnnotationClass()};
            public class AlwaysPassingTest {
                @Test
                public void passingTest() {
                    System.out.println("passingTest");
                }
            }
        """.stripIndent()
        return "pkg.AlwaysPassingTest"
    }

    abstract String testAnnotationClass()
    abstract String testDependency()
    abstract String testFrameworkConfiguration()
}
