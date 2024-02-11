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

package org.gradle.testing.junit.platform

import org.gradle.api.internal.tasks.testing.junit.JUnitSupport
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue
import spock.lang.Timeout

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_PLATFORM_VERSION
import static org.hamcrest.CoreMatchers.containsString

class JUnitPlatformIntegrationTest extends JUnitPlatformIntegrationSpec {

    def 'can work with junit-platform-runner'() {
        given:
        buildFile << """
        dependencies {
            testImplementation 'org.junit.platform:junit-platform-runner:1.0.3'
        }
        """
        createSimpleJupiterTest()

        expect:
        succeeds('test')
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
            .assertTestClassesExecutedJudgementByHtml('org.gradle.RepeatTest')
            .testClassByHtml('org.gradle.RepeatTest')
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
            .testClass(JUnitSupport.UNKNOWN_CLASS)
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
        createSimpleJupiterTests()

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
        'includeTags'    | '"good"'
        'excludeTags'    | '"bad"'
        'includeEngines' | '"junit-jupiter"'
        'excludeEngines' | '"junit-vintage-engine"'
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

    @Issue("https://github.com/junit-team/junit5/issues/2028 and https://github.com/gradle/gradle/issues/12073")
    def 'properly fails when engine fails during discovery #scenario'() {
        given:
        createSimpleJupiterTest()
        buildFile << """
            dependencies {
                testImplementation 'org.junit.platform:junit-platform-engine:${LATEST_PLATFORM_VERSION}'
            }
        """
        file('src/test/java/EngineFailingDiscovery.java') << '''
            import org.junit.platform.engine.*;
            public class EngineFailingDiscovery implements TestEngine {
                @Override
                public String getId() {
                    return "EngineFailingDiscovery";
                }

                @Override
                public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
                    throw new RuntimeException("oops");
                }

                @Override
                public void execute(ExecutionRequest request) {
                }
            }
        '''
        file('src/test/resources/META-INF/services/org.junit.platform.engine.TestEngine') << 'EngineFailingDiscovery'

        expect:
        fails('test', *extraArgs)
        failureCauseContains('There were failing tests.')

        where:
        scenario       | extraArgs
        "w/o filters"  | []
        "with filters" | ['--tests', 'JUnitJupiterTest']
    }

    @Issue("https://github.com/gradle/gradle/issues/23602")
    def "handles unserializable exception thrown from test"() {
        given:
        file('src/test/java/PoisonTest.java') << """
            import org.junit.jupiter.api.Test;

            public class PoisonTest {
                @Test
                public void passingTest() { }

                @Test
                public void testWithUnserializableException() {
                    throw new UnserializableException();
                }

                @Test
                public void normalFailingTest() {
                    assert false;
                }

                private static class WriteReplacer implements java.io.Serializable {
                    private Object readResolve() {
                        return new RuntimeException();
                    }
                }

                private static class UnserializableException extends RuntimeException {
                    private Object writeReplace() {
                        return new WriteReplacer();
                    }
                }
            }
        """

        when:
        fails("test")

        then:
        with(new DefaultTestExecutionResult(testDirectory).testClass("PoisonTest")) {
            assertTestPassed("passingTest")
            assertTestFailed("testWithUnserializableException", containsString("TestFailureSerializationException: An exception of type PoisonTest\$UnserializableException was thrown by the test, but Gradle was unable to recreate the exception in the build process"))
            assertTestFailed("normalFailingTest", containsString("AssertionError"))
        }
    }

    // When running embedded with test distribution, the remote distribution has a newer version of
    // junit-platform-launcher which is not compatible with the junit jupiter jars we test against.
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    // JUnitCoverage is quite limited and doesn't test older versions or the newest version.
    // Future work is planned to improve junit test rewriting, and at the same time should verify
    // greater ranges of junit platform testing. This is only reproducible with the newest version
    // of junit, so test that version explicitly here.
    @Issue("https://github.com/gradle/gradle/issues/24429")
    def "works with parameterized tests for larger version range"() {
        given:
        buildScriptWithJupiterDependencies("""
            test {
                useJUnitPlatform()
            }
        """, version)

        file("src/test/java/SomeTest.java") << """
            import java.util.stream.Stream;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.Arguments;
            import org.junit.jupiter.params.provider.MethodSource;

            class SomeTest {
                public static Stream<Arguments> args() {
                    return Stream.of(Arguments.of("blah"));
                }

                @ParameterizedTest
                @MethodSource("args")
                void someTest(String value) { }
            }
        """

        expect:
        succeeds "test"

        where:
        version << ["5.9.2", "5.6.3"]
    }

    def 'properly fails when engine fails during execution'() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.platform:junit-platform-engine:${LATEST_PLATFORM_VERSION}'
            }
            test {
                afterSuite { descriptor, result ->
                    println("afterSuite: \$descriptor -> \$result")
                }
            }
        """
        file('src/test/java/EngineFailingExecution.java') << '''
            import org.junit.platform.engine.*;
            import org.junit.platform.engine.support.descriptor.*;
            public class EngineFailingExecution implements TestEngine {
                @Override
                public String getId() {
                    return "EngineFailingExecution";
                }

                @Override
                public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
                    return new EngineDescriptor(uniqueId, getId()) {
                        @Override
                        public boolean mayRegisterTests() {
                            return true; // to avoid the engine from being skipped
                        }
                    };
                }

                @Override
                public void execute(ExecutionRequest request) {
                    throw new RuntimeException("oops");
                }
            }
        '''
        file('src/test/resources/META-INF/services/org.junit.platform.engine.TestEngine') << 'EngineFailingExecution'

        when:
        fails('test')

        then:
        failureCauseContains('There were failing tests.')
        outputContains("afterSuite: Test class UnknownClass -> FAILURE")
    }
}
