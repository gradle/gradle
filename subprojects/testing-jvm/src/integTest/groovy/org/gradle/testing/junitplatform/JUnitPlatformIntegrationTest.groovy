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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION
import static org.hamcrest.Matchers.containsString

@Requires(TestPrecondition.JDK8_OR_LATER)
class JUnitPlatformIntegrationTest extends JUnitPlatformIntegrationSpec {
    void createSimpleJupiterTest() {
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class JUnitJupiterTest {
                @Test
                public void ok() { }
            }
            '''
    }

    def 'can work with junit-platform-runner'() {
        given:
        buildFile << """
        dependencies {
            testCompile 'org.junit.platform:junit-platform-runner:1.0.3'
        }
        """
        createSimpleJupiterTest()

        expect:
        succeeds('test')
    }

    def 'should prompt user to add dependencies when they are not in test runtime classpath'() {
        given:
        buildFile.text = """ 
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { 
                testCompileOnly 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'
            }
            
            test { useJUnitPlatform() }
            """
        createSimpleJupiterTest()

        when:
        fails('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.testClassStartsWith('Gradle Test Executor').assertExecutionFailedWithCause(containsString('consider adding an engine implementation JAR to the classpath'))
    }

    def 'can handle class level ignored tests'() {
        given:
        file('src/test/java/org/gradle/IgnoredTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.*;

            @Disabled
            public class IgnoredTest {
                @Test
                public void testIgnored1() {
                    throw new RuntimeException();
                }
            }
        '''

        when:
        run('check')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.IgnoredTest')
        result.testClass('org.gradle.IgnoredTest').assertTestCount(1, 0, 0).assertTestsSkipped("testIgnored1")
    }

    @Unroll
    def 'can handle class level error in #location'() {
        given:
        file('src/test/java/org/gradle/ClassErrorTest.java') << """ 
            package org.gradle;
            
            import org.junit.jupiter.api.*;

            public class ClassErrorTest {
                @Test
                public void ok() {
                }

                @BeforeAll
                public static void before() {
                    $beforeStatement;
                }
                
                @AfterAll
                public static void after() {
                    $afterStatement;
                }
            }
        """

        when:
        fails('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.ClassErrorTest')
        result.testClass('org.gradle.ClassErrorTest').assertTestCount(successCount + failureCount, failureCount, 0)

        where:
        location    | beforeStatement                | afterStatement                 | successCount | failureCount
        'beforeAll' | 'throw new RuntimeException()' | ''                             | 0            | 1
        'afterAll'  | ''                             | 'throw new RuntimeException()' | 1            | 1
    }

    def 'can handle class level assumption'() {
        given:
        file('src/test/java/org/gradle/ClassAssumeTest.java') << '''
        package org.gradle;

        import org.junit.jupiter.api.*;
        import org.junit.jupiter.api.Assumptions;

        public class ClassAssumeTest {
            @Test
            public void ok() {
            }

            @BeforeAll
            public static void before() {
                Assumptions.assumeTrue(false);
            }
        }
        '''

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory).testClass('org.gradle.ClassAssumeTest').assertTestCount(1, 0, 0)
    }

    def 'can handle repeated tests'() {
        given:
        file('src/test/java/org/gradle/RepeatTest.java') << '''
        package org.gradle;

        import org.junit.jupiter.api.*;
        import org.junit.jupiter.api.Assumptions;

        public class RepeatTest {
            @RepeatedTest(value = 3, name = "ok {currentRepetition}/{totalRepetitions}")
            public void ok() {
            }

            @RepeatedTest(value = 3, name = "partialFail {currentRepetition}/{totalRepetitions}")
            public void partialFail(RepetitionInfo repetitionInfo) {
                if (repetitionInfo.getCurrentRepetition() == 2) {
                    throw new RuntimeException();
                }
            }

            @RepeatedTest(value = 3, name = "partialSkip {currentRepetition}/{totalRepetitions}")
            public void partialSkip(RepetitionInfo repetitionInfo) {
                if (repetitionInfo.getCurrentRepetition() == 2) {
                    Assumptions.assumeTrue(false);
                }
            }
        }
        '''

        when:
        fails('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.RepeatTest')
        result.testClass('org.gradle.RepeatTest').assertTestCount(9, 1, 0)
            .assertTestPassed('ok 1/3')
            .assertTestPassed('ok 2/3')
            .assertTestPassed('ok 3/3')
            .assertTestPassed('partialFail 1/3')
            .assertTestFailed('partialFail 2/3', containsString('java.lang.RuntimeException'))
            .assertTestPassed('partialFail 3/3')
            .assertTestPassed('partialSkip 1/3')
            .assertTestsSkipped('partialSkip 2/3')
            .assertTestPassed('partialSkip 3/3')
    }

    def 'can filter nested tests'() {
        given:
        file('src/test/java/org/gradle/NestedTest.java') << '''
package org.gradle;
import static org.junit.jupiter.api.Assertions.*;

import java.util.EmptyStackException;
import java.util.Stack;

import org.junit.jupiter.api.*;

class NestedTest {
    @Test
    void outerTest() {
    }

    @Nested
    class Inner {
        @Test
        void innerTest() {
        }
    }
}
'''
        buildFile << '''
test {
    filter {
        includeTestsMatching "*innerTest*"
    }
}
'''
        when:
        succeeds('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.NestedTest$Inner')
        result.testClass('org.gradle.NestedTest$Inner').assertTestCount(1, 0, 0)
            .assertTestPassed('innerTest')
    }

    @Issue('https://github.com/gradle/gradle/issues/4476')
    def 'can handle test engine failure'() {
        given:
        createSimpleJupiterTest()
        file('src/test/java/UninstantiatableExtension.java') << '''
import org.junit.jupiter.api.extension.*;
public class UninstantiatableExtension implements BeforeEachCallback {
  private UninstantiatableExtension(){}

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
  }
}
'''
        file('src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension') << 'UninstantiatableExtension'
        buildFile << '''
            test {
                systemProperty('junit.jupiter.extensions.autodetection.enabled', 'true')
            }
        '''

        when:
        fails('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('UnknownClass')
            .assertTestFailed('initializationError', containsString('UninstantiatableExtension'))
    }
}
