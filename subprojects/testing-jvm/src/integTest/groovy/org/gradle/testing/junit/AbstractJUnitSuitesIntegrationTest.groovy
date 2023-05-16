/*
 * Copyright 2023 the original author or authors.
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

abstract class AbstractJUnitSuitesIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    abstract String getTestFrameworkSuiteDependencies()
    abstract String getTestFrameworkSuiteImports()
    abstract String getTestFrameworkSuiteAnnotations(String classes)

    def "test classes can be shared by multiple suites"() {
        given:
        file('src/test/java/org/gradle/SomeTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class SomeTest {
                @Test
                public void ok() throws Exception {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTestSuite.java') << """
            package org.gradle;

            ${testFrameworkSuiteImports}

            ${getTestFrameworkSuiteAnnotations("SomeTest.class")}
            public class SomeTestSuite {
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeOtherTestSuite.java') << """
            package org.gradle;

            ${testFrameworkSuiteImports}

            ${getTestFrameworkSuiteAnnotations("SomeTest.class")}
            public class SomeOtherTestSuite {
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                ${testFrameworkSuiteDependencies}
            }
            test {
                ${configureTestFramework}
                include '**/*Suite.class'
                exclude '**/*Test.class'
            }
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass("org.gradle.SomeTest").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.SomeTest").assertTestsExecuted("ok", "ok")
    }
}
