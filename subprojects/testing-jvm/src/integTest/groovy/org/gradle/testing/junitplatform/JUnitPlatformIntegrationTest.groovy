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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.LATEST_JUPITER_VERSION
import static org.hamcrest.Matchers.containsString

@Requires(TestPrecondition.JDK8_OR_LATER)
class JUnitPlatformIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.noExtraLogging()
    }

    def 'should prompt user to add dependencies when they are not in test runtime classpath'() {
        given:
        buildFile << """ 
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { 
                testCompileOnly 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'
            }
            
            test { useJUnitPlatform() }
            """
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class JUnitJupiterTest {
                @Test
                public void ok() { }
            }
            '''

        when:
        fails('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('Gradle Test Executor 1').assertExecutionFailedWithCause(containsString('consider adding an engine implementation JAR to the classpath'))
    }

    def 'can handle class level ignored tests'() {
        buildFile << """
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }
            dependencies { 
                testCompile 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'
            }

            test {
                useJUnitPlatform()
            }
        """

        file('src/test/java/org/gradle/IgnoredTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Disabled;
            import org.junit.jupiter.api.Test;

            @Disabled
            public class IgnoredTest {
                @Test
                public void testIgnored1() {
                    throw new RuntimeException();
                }

                @Test
                public void testIgnored2() {
                }
            }
        '''

        when:
        run('check')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.IgnoredTest')
        result.testClass('org.gradle.IgnoredTest').assertTestCount(2, 0, 0).assertTestsSkipped("testIgnored1", "testIgnored2")
    }
}
