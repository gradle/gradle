/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.junit.vintage

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.getJUNIT_VINTAGE

@TargetCoverage({ JUNIT_VINTAGE })
class JUnitVintageJUnit3IntegrationTest extends AbstractTestingMultiVersionIntegrationTest implements JUnitVintageMultiVersionTest {
    @Issue("https://github.com/gradle/gradle/issues/35868")
    def "can run JUnit3 TestCase in multiple suites"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                implementation("junit:junit:3.8.2")
            }
            test.${configureTestFramework}

            test.maxParallelForks = 1
        """.stripIndent()

        file("src/main/java/com/example/Innermost.java").java """
            package com.example;
            import junit.framework.*;

            public class Innermost extends TestCase {
                private static int count = 0;

                public void test() {
                    assert count == 0;
                    count++;
                }
            }
        """
        file("src/test/java/com/example/FooTest.java").java """
            package com.example;
            import junit.framework.*;

            public class FooTest extends TestCase {
                public static Test suite() {
                    TestSuite suite = new TestSuite("outer");
                    TestSuite inner1 = new TestSuite("inner1");
                    inner1.addTestSuite(com.example.Innermost.class);
                    TestSuite inner2 = new TestSuite("inner2");
                    inner2.addTestSuite(com.example.Innermost.class);
                    suite.addTest(inner1);
                    suite.addTest(inner2);
                    return suite;
                }
            }
        """.stripIndent()

        when:
        fails("test")
        then:
        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult1 = testResult.testClass("com.example.Innermost", 0)
        classResult1.assertTestCount(1, 0)
        def classResult2 = testResult.testClass("com.example.Innermost", 1)
        classResult2.assertTestCount(1, 1)
    }
}
