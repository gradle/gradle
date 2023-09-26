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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

import static org.hamcrest.CoreMatchers.containsString

abstract class AbstractJUnitSmokeMultiVersionIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    abstract void assertTestSkippedOrPassed(TestClassExecutionResult testClassResult, String testName)

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
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.Junit3Test', 'org.gradle.Junit4Test')
        def junit3TestClass = result.testClass('org.gradle.Junit3Test')
            .assertTestCount(2, 1, 0)
            .assertTestFailed('a test that renames itself', containsString("epic"))
        assertTestSkippedOrPassed(junit3TestClass, 'testRenamesItself')
        result.testClass('org.gradle.Junit4Test')
                .assertTestCount(2, 0, 0)
                .assertTestsExecuted('ok')
                .assertTestPassed('ok')
                .assertTestsSkipped('broken')
    }
}
