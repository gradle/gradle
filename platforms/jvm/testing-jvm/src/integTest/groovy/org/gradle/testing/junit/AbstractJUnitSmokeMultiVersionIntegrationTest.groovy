/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.tasks.testing.TestResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

import static org.hamcrest.CoreMatchers.containsString

abstract class AbstractJUnitSmokeMultiVersionIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def "can run tests using JUnit"() {
        given:
        file('src/test/java/org/gradle/Junit3Test.java') << """
            package org.gradle;

            import junit.framework.TestCase;

            public class Junit3Test extends TestCase {
                public void testRenamesItself() {
                    setName("a test that renames itself");
                    fail("epic");
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/Junit4Test.java') << """
            package org.gradle;

            import org.junit.Ignore;
            import org.junit.Test;

            public class Junit4Test {
                @Test
                public void ok() {
                }

                @Test
                @Ignore
                public void broken() {
                    throw new RuntimeException();
                }

                public void helpermethod() {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/NoTest.java') << """
            package org.gradle;

            public class NoTest {
                public void notATest() {
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        fails('test')

        then:
        def results = resultsFor(testDirectory)
        results.assertAtLeastTestPathsExecuted('org.gradle.Junit3Test', 'org.gradle.Junit4Test')
        results.testPath('org.gradle.Junit3Test').onlyRoot()
            .assertChildCount(2, 1) // One test fails, the other succeeds or is skipped
        results.testPath('org.gradle.Junit3Test', 'a test that renames itself').onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("epic"))
        results.testPath('org.gradle.Junit4Test').onlyRoot()
                .assertChildCount(2, 0)
        results.testPath('org.gradle.Junit4Test', 'ok').onlyRoot()
                .assertHasResult(TestResult.ResultType.SUCCESS)
        results.testPath('org.gradle.Junit4Test', 'broken').onlyRoot()
            .assertHasResult(TestResult.ResultType.SKIPPED)
    }
}
