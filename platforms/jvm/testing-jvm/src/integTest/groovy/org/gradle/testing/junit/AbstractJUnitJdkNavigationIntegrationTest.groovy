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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

@Issue("GRADLE-1682")
abstract class AbstractJUnitJdkNavigationIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def shouldNotNavigateToJdkClasses() {
        given:
        file('src/test/java/org/gradle/AbstractTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public abstract class AbstractTest {

                protected int value = 0;

                ${beforeTestAnnotation}
                public void before() {
                    value = 1;
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/Test1.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class Test1 extends AbstractTest {

                @Test
                public void shouldPass() {
                    assertEquals(1, value);
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                testImplementation 'org.apache.felix:org.osgi.foundation:1.2.0'
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        succeeds('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('org.gradle.Test1').assertTestPassed('shouldPass')
    }

}
