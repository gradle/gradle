/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.hamcrest.CoreMatchers
import spock.lang.Unroll

import static groovy.util.GroovyCollections.combinations

class RunTestsSortedByDurationIntegrationTest extends AbstractIntegrationSpec {

    private static final Map<Integer, Integer> TESTS_WITH_DURATION = [1: 10, 2: 20, 3: 40, 4: 60]
    private static final String FAILING_TESTS_PROPERTY = "failing.tests.indices"
    private static final List<List<Integer>> FAILING_TESTS_INDICES = combinations(TESTS_WITH_DURATION.keySet(), TESTS_WITH_DURATION.keySet()).findAll { i, j -> j > i }

    def setup() {
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.12'
            }
        """

        TESTS_WITH_DURATION.each { testIndex, duration ->
            testFile(testIndex) << """
                import org.junit.Test;
                import java.util.Arrays;
                public class ${testClassName(testIndex)} {
                    @Test
                    public void takingTimeTest() throws Exception {
                        System.out.println("Test index " + ${testIndex});
                        Thread.sleep(${duration});
                        String indices = System.getProperty("${FAILING_TESTS_PROPERTY}");
                        if(indices != null && Arrays.asList(indices.split(",")).contains("${testIndex}")) {
                            throw new RuntimeException();
                        }
                    }
                }
            """.stripIndent()
        }
    }

    @Unroll
    def 'subsequent execution runs succeeding tests by duration, then unknown test #unknownTestIndex'() {

        when:
        testFile(unknownTestIndex).delete()
        succeeds('test')

        then:
        def knownTestIndices = TESTS_WITH_DURATION.keySet() - unknownTestIndex
        testsSucceed(knownTestIndices)

        when:
        TESTS_WITH_DURATION.keySet().each {
            testFile(it).text = """
                import org.junit.Test;
                public class ${testClassName(it)} {
                    @Test
                    public void takingTimeTest() {
                        System.out.println("Test index " + ${it});
                    }
                }
            """.stripIndent()
        }
        succeeds('test', '--info')

        then:
        testsAreRunInOrder(sortedByDuration(knownTestIndices) + unknownTestIndex)

        where:
        unknownTestIndex << TESTS_WITH_DURATION.keySet()
    }


    @Unroll
    def 'subsequent execution runs failing tests #failingTestsIndices by duration, then succeeding test '() {

        given:
        letTestsFail(failingTestsIndices)

        when:
        fails('test')

        then:
        testFailed(failingTestsIndices)

        when:
        TESTS_WITH_DURATION.keySet().each {
            testFile(it).text = """
                import org.junit.Test;
                public class ${testClassName(it)} {
                    @Test
                    public void takingTimeTest() {
                        System.out.println("Test index " + ${it});
                    }
                }
            """.stripIndent()
        }
        succeeds('test', '--info')

        then:
        def succeedingTestIndices = TESTS_WITH_DURATION.keySet() - failingTestsIndices
        testsAreRunInOrder(sortedByDuration(failingTestsIndices) + sortedByDuration(succeedingTestIndices))

        where:
        failingTestsIndices << FAILING_TESTS_INDICES
    }

    def sortedByDuration(testIndices) {
        testIndices.sort(false, { -TESTS_WITH_DURATION.get(it) })
    }

    def letTestsFail(indices) {
        buildFile << """
        test {
            systemProperty('${FAILING_TESTS_PROPERTY}', '${indices.join(",")}')
        }
        """
    }

    void testsAreRunInOrder(List<Integer> testIndices) {
        List<String> lines = output.readLines()
        int nextIndex = 0
        for (String line in lines) {
            if (nextIndex < testIndices.size && line.contains("Test index ${testIndices[nextIndex]}")) {
                nextIndex++
            } else if (line.contains("Test index")) {
                assert false: "Got ${line.strip()}, expected ${testIndices[nextIndex]}"
            }
        }

        assert nextIndex == testIndices.size
    }

    void testsSucceed(Set<Integer> testIndices) {
        new DefaultTestExecutionResult(testDirectory).assertTestClassesExecuted(testIndices.collect { testClassName(it) } as String[])
    }

    void testFailed(indices) {
        indices.each {
            new DefaultTestExecutionResult(testDirectory)
                .testClass("${testClassName(it)}").assertTestFailed('takingTimeTest', CoreMatchers.anything())
        }
    }

    File testFile(int testIndex) {
        file("src/test/java/${testClassName(testIndex)}.java")
    }

    String testClassName(int testIndex) {
        "TestWithDuration_${testIndex}"
    }
}
