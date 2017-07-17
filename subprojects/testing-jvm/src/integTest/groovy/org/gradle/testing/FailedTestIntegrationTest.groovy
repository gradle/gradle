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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.ONLINE)
class FailedTestIntegrationTest extends AbstractIntegrationSpec {
    public static final String RUN_FAILING_TEST = "Test failingTest(org.gradle.FailingTest)"
    public static final String RUN_PASSING_TEST = "Test passingTest(org.gradle.PassingTest)"
    def failingTest = file("src/test/java/org/gradle/FailingTest.java")

    def setup() {
        buildFile << """
            apply plugin: 'java'
            repositories { jcenter() }
            dependencies { testCompile 'junit:junit:4.12' }
            
            task validate() {
                doLast {
                    def expectedFilters = project.findProperty("expectedFilters") 
                    if (expectedFilters) {
                        assert test.filter.includePatterns == project.expectedFilters.split(",") as Set
                    } else {
                        assert test.filter.includePatterns.isEmpty()
                    }
                }
            }

            test {
                dependsOn validate
                beforeTest { logger.lifecycle(it.toString()) }
            }
        """.stripIndent()

        failingTest << """
            package org.gradle;
            
            import org.junit.Test;
            import org.junit.Assert;
            
            public class FailingTest {
                @Test
                public void failingTest() {
                    Assert.assertTrue(false);
                }
            }
        """
        file("src/test/java/org/gradle/PassingTest.java") << """
            package org.gradle;
            
            import org.junit.Test;
            import org.junit.Assert;
            
            public class PassingTest {
                @Test
                public void passingTest() {
                    Assert.assertTrue(true);
                }
            }
        """
    }

    def "--failed runs failed tests only"() {
        when:
        fails("test")
        then:
        result.assertOutputContains(RUN_FAILING_TEST)
        result.assertOutputContains(RUN_PASSING_TEST)

        when:
        fails("test", "--failed", "-PexpectedFilters=org.gradle.FailingTest")
        then:
        result.assertOutputContains(RUN_FAILING_TEST)
        !result.output.contains(RUN_PASSING_TEST)

        when:
        makeFailingTestPass()
        and:
        succeeds("test", "--failed", "-PexpectedFilters=org.gradle.FailingTest")
        then:
        result.assertOutputContains(RUN_FAILING_TEST)
        !result.output.contains(RUN_PASSING_TEST)

        when:
        succeeds("test")
        then:
        result.assertOutputContains(RUN_FAILING_TEST)
        result.assertOutputContains(RUN_PASSING_TEST)
    }

    def "--failed when there were no previous failures runs all tests"() {
        when:
        makeFailingTestPass()
        and:
        succeeds("test", "--failed")
        then:
        result.assertOutputContains("No tests failed previously, running all tests.")
        result.assertOutputContains(RUN_FAILING_TEST)
        result.assertOutputContains(RUN_PASSING_TEST)
    }

    def "--failed when there is a problem reading previous results runs all tests"() {
        when:
        makeFailingTestPass()
        and:
        succeeds("test")
        then:
        result.assertOutputContains(RUN_FAILING_TEST)
        result.assertOutputContains(RUN_PASSING_TEST)

        when:
        file("build/test-results/test/binary/results.bin").text = "corrupted"
        and:
        succeeds("test", "--failed")
        then:
        result.assertOutputContains("No tests failed previously, running all tests.")
        result.assertOutputContains(RUN_FAILING_TEST)
        result.assertOutputContains(RUN_PASSING_TEST)
    }

    void makeFailingTestPass() {
        failingTest.text = failingTest.text.replace("false", "true")
    }
}
