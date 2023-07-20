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

abstract class AbstractJUnitAssumptionsIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def "supports assumptions"() {
        given:
        executer.noExtraLogging()
        file('src/test/java/org/gradle/TestWithAssumptions.java').text = """
            package org.gradle;

            ${testFrameworkImports}

            public class TestWithAssumptions {
                @Test
                public void assumptionFailed() {
                    assumeTrue(false);
                }

                @Test
                public void assumptionSucceeded() {
                    assumeTrue(true);
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
        run('check')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.TestWithAssumptions')
        result.testClass('org.gradle.TestWithAssumptions')
                .assertTestCount(2, 0, 0)
                .assertTestsExecuted('assumptionSucceeded')
                .assertTestPassed('assumptionSucceeded')
                .assertTestsSkipped('assumptionFailed')
    }
}
