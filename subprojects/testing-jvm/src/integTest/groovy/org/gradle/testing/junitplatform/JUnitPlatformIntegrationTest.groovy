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
import spock.lang.Timeout
import spock.lang.Unroll

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION
import static org.hamcrest.CoreMatchers.containsString

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
        new DefaultTestExecutionResult(testDirectory)
            .testClassStartsWith('Gradle Test Executor')
            .assertExecutionFailedWithCause(containsString('consider adding an engine implementation JAR to the classpath'))
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
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('org.gradle.IgnoredTest')
            .testClass('org.gradle.IgnoredTest').assertTestCount(1, 0, 0).assertTestsSkipped("testIgnored1()")
    }

    @Unroll
    def 'can handle class-level error in #location method'() {
        given:
        file('src/test/java/org/gradle/ClassErrorTest.java') << """ 
            package org.gradle;
            
            import org.junit.jupiter.api.*;
            import static org.junit.jupiter.api.Assertions.*;

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
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('org.gradle.ClassErrorTest')
            .testClass('org.gradle.ClassErrorTest')
            .assertTestCount(successCount + 1, 1, 0)
            .assertTestFailed(failedTestName, containsString(location))

        where:
        location     | beforeStatement      | afterStatement      | successCount | failedTestName
        '@BeforeAll' | 'fail("@BeforeAll")' | ''                  | 0            | "initializationError"
        '@AfterAll'  | ''                   | 'fail("@AfterAll")' | 1            | "executionError"
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
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('org.gradle.RepeatTest')
            .testClass('org.gradle.RepeatTest')
            .assertTestCount(9, 1, 0)
            .assertTestPassed('ok()[1]', 'ok 1/3')
            .assertTestPassed('ok()[2]', 'ok 2/3')
            .assertTestPassed('ok()[3]', 'ok 3/3')
            .assertTestPassed('partialFail(RepetitionInfo)[1]', 'partialFail 1/3')
            .assertTestFailed('partialFail(RepetitionInfo)[2]', 'partialFail 2/3', containsString('java.lang.RuntimeException'))
            .assertTestPassed('partialFail(RepetitionInfo)[3]', 'partialFail 3/3')
            .assertTestPassed('partialSkip(RepetitionInfo)[1]', 'partialSkip 1/3')
            .assertTestSkipped('partialSkip(RepetitionInfo)[2]', 'partialSkip 2/3')
            .assertTestPassed('partialSkip(RepetitionInfo)[3]', 'partialSkip 3/3')
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
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('org.gradle.NestedTest$Inner')
            .testClass('org.gradle.NestedTest$Inner').assertTestCount(1, 0, 0)
            .assertTestPassed('innerTest()')
    }

    @Issue('https://github.com/gradle/gradle/issues/4476')
    def 'can handle test engine failure'() {
        given:
        createSimpleJupiterTest()
        file('src/test/java/UninstantiableExtension.java') << '''
import org.junit.jupiter.api.extension.*;
public class UninstantiableExtension implements BeforeEachCallback {
  private UninstantiableExtension(){}

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
  }
}
'''
        file('src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension') << 'UninstantiableExtension'
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
            .assertTestFailed('initializationError', containsString('UninstantiableExtension'))
    }

    @Issue('https://github.com/gradle/gradle/issues/4427')
    def 'can run tests in static nested class'() {
        given:
        file('src/test/java/org/gradle/StaticInnerTest.java') << '''
package org.gradle;
import org.junit.jupiter.api.*;
public class StaticInnerTest {
    public static class Nested {
        @Test
        public void inside() {
        }
        
        public static class Nested2 {
            @Test
            public void inside() {
            }
        }
    }

    @Test
    public void outside() {
    }
}
'''
        when:
        succeeds('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.StaticInnerTest', 'org.gradle.StaticInnerTest$Nested', 'org.gradle.StaticInnerTest$Nested$Nested2')
        result.testClass('org.gradle.StaticInnerTest').assertTestCount(1, 0, 0)
            .assertTestPassed('outside')
        result.testClass('org.gradle.StaticInnerTest$Nested').assertTestCount(1, 0, 0)
            .assertTestPassed('inside')
        result.testClass('org.gradle.StaticInnerTest$Nested$Nested2').assertTestCount(1, 0, 0)
            .assertTestPassed('inside')
    }

    @Unroll
    @Issue('https://github.com/gradle/gradle/issues/4924')
    def "re-executes test when #key is changed"() {
        given:
        buildScriptWithJupiterDependencies("""
            test {
                useJUnitPlatform {
                    ${key} ${value}
                }
            }
        """)
        createSimpleJupiterTest()

        when:
        succeeds ':test'

        then:
        executedAndNotSkipped ':test'

        when:
        buildScriptWithJupiterDependencies("""
            test {
                useJUnitPlatform()
            }
        """)

        and:
        succeeds ':test'

        then:
        executedAndNotSkipped ':test'

        where:
        key              | value
        'includeTags'    | '"ok"'
        'excludeTags'    | '"ok"'
        'includeEngines' | '"junit-jupiter"'
        'excludeEngines' | '"junit-jupiter"'
    }

    @Timeout(60)
    @Issue('https://github.com/gradle/gradle/issues/6453')
    def "can handle parallel test execution"() {
        given:
        def numTestClasses = 32
        buildScriptWithJupiterDependencies("""
            test {
                useJUnitPlatform()
                systemProperty('junit.jupiter.execution.parallel.enabled', 'true')
                systemProperty('junit.jupiter.execution.parallel.config.strategy', 'fixed')
                systemProperty('junit.jupiter.execution.parallel.config.fixed.parallelism', '$numTestClasses')
            }
        """)
        file('src/test/java/org/gradle/Tests.java') << """
            package org.gradle;

            import java.util.concurrent.*;
            import org.junit.jupiter.api.*;
            import org.junit.jupiter.api.parallel.*;
            import static org.junit.jupiter.api.Assertions.*;
            import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

            @Execution(CONCURRENT)
            class Sync {
                static CountDownLatch LATCH = new CountDownLatch($numTestClasses);
            }

            ${(1..numTestClasses).collect { classNumber -> """
                class Test$classNumber extends Sync {
                    @Test
                    public void test() throws Exception {
                        LATCH.countDown();
                        LATCH.await();
                    }
                }
            """ }.join("") }
        """

        when:
        succeeds(':test')

        then:
        with(new DefaultTestExecutionResult(testDirectory)) {
            (1..numTestClasses).every { classNumber ->
                testClass("org.gradle.Test$classNumber").assertTestCount(1, 0, 0)
            }
        }
    }
}
