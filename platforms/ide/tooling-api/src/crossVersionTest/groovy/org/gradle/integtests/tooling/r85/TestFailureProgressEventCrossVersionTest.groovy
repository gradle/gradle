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

package org.gradle.integtests.tooling.r85

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestFailureSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.FileComparisonTestAssertionFailure
import org.gradle.tooling.TestAssertionFailure

@ToolingApiVersion(">=8.5")
@TargetGradleVersion(">=8.5")
class TestFailureProgressEventCrossVersionTest extends TestFailureSpecification {

    def setup() {
        enableTestJvmDebugging = false
        enableStdoutProxying = true
    }

    def "Wrapped assertion errors are emitted as test failure events using JUnit 4"() {
        given:
        setupJUnit4()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.Test;

            public class JUnitTest {
                @Test
                public void test() {
                    throw new RuntimeException(
                        "This exception wraps an assertion error",
                        new AssertionError("This is a wrapped assertion error")
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof TestAssertionFailure

        TestAssertionFailure failure = collector.failures[0]
        failure.message == "This is a wrapped assertion error"
    }

    def "Wrapped assertion errors are emitted as test failure events using JUnit 5"() {
        given:
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class JUnitTest {
                @Test
                void test() {
                    throw new RuntimeException(
                        "This exception wraps an assertion error",
                        new AssertionError("This is a wrapped assertion error")
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof TestAssertionFailure

        TestAssertionFailure failure = collector.failures[0]
        failure.message == "This is a wrapped assertion error"
    }

    def "Test failure using standard AssertionError contains mapped causes using JUnit 5"() {
        given:
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class JUnitTest {
                @Test
                void test() {
                    throw new AssertionError(
                        "This exception wraps an assertion error",
                        new AssertionError("This is a wrapped assertion error")
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)

        // Extract and assert the wrapper failure
        collector.failures.size() == 1
        collector.failures[0] instanceof TestAssertionFailure
        def failure = collector.failures[0] as TestAssertionFailure
        failure.message == "This exception wraps an assertion error"

        // Extract and assert the wrapped failure
        failure.causes.size() == 1
        failure.causes[0] instanceof TestAssertionFailure
        def cause = failure.causes[0] as TestAssertionFailure
        cause.message == "This is a wrapped assertion error"
    }

    def "Test failure using standard AssertionError contains mapped causes using JUnit 4"() {
        given:
        setupJUnit4()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.Test;

            public class JUnitTest {
                @Test
                public void test() {
                    throw new AssertionError(
                        "This exception wraps an assertion error",
                        new AssertionError("This is a wrapped assertion error")
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)

        // Extract and assert the wrapper failure
        collector.failures.size() == 1
        collector.failures[0] instanceof TestAssertionFailure
        def failure = collector.failures[0] as TestAssertionFailure
        failure.message == "This exception wraps an assertion error"

        // Extract and assert the wrapped failure
        failure.causes.size() == 1
        failure.causes[0] instanceof TestAssertionFailure
        def cause = failure.causes[0] as TestAssertionFailure
        cause.message == "This is a wrapped assertion error"
    }

    def "Test failure using AssertionFailedError contains mapped causes using JUnit 5"() {
        given:
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import org.opentest4j.AssertionFailedError;

            public class JUnitTest {
                @Test
                void test() {
                    throw new AssertionError(
                        "This exception wraps an assertion error",
                        new AssertionFailedError("This is a wrapped assertion error", "expected", "actual")
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)

        // Extract and assert the wrapper failure
        collector.failures.size() == 1
        collector.failures[0] instanceof TestAssertionFailure
        def failure = collector.failures[0] as TestAssertionFailure
        failure.message == "This exception wraps an assertion error"

        // Extract and assert the wrapped failure
        failure.causes.size() == 1
        failure.causes[0] instanceof TestAssertionFailure
        def cause = failure.causes[0] as TestAssertionFailure
        cause.message == "This is a wrapped assertion error"
        cause.getExpected() == "expected"
        cause.getActual() == "actual"
    }

    def "Test failure using AssertionFailedError contains mapped causes using JUnit 4"() {
        given:
        setupJUnit4()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.Test;
            import org.opentest4j.AssertionFailedError;

            public class JUnitTest {
                @Test
                public void test() {
                    throw new AssertionError(
                        "This exception wraps an assertion error",
                        new AssertionFailedError("This is a wrapped assertion error", "expected", "actual")
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)

        // Extract and assert the wrapper failure
        collector.failures.size() == 1
        collector.failures[0] instanceof TestAssertionFailure
        def failure = collector.failures[0] as TestAssertionFailure
        failure.message == "This exception wraps an assertion error"

        // Extract and assert the wrapped failure
        failure.causes.size() == 1
        failure.causes[0] instanceof TestAssertionFailure
        def cause = failure.causes[0] as TestAssertionFailure
        cause.message == "This is a wrapped assertion error"
        cause.getExpected() == "expected"
        cause.getActual() == "actual"
    }

    def "Different expected and actual OpenTest4j types are mapped correctly using JUnit4"() {
        given:
        setupJUnit4()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.Test;
            import org.opentest4j.AssertionFailedError;
            import org.opentest4j.FileInfo;

            public class JUnitTest {
                @Test
                public void test() {
                    FileInfo expected = new FileInfo("/path/from", new byte[]{ 0x0 });
                    throw new AssertionFailedError(
                        "Asymmetric expected and actual objects",
                        expected,
                        "actual content"
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof FileComparisonTestAssertionFailure

        def failure = collector.failures[0] as FileComparisonTestAssertionFailure
        failure.expected == '/path/from'
        failure.expectedContent == new byte[]{0x0}
        failure.actual == 'actual content'
    }

    def "Different expected and actual OpenTest4j types are mapped correctly using JUnit5"() {
        given:
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import org.opentest4j.AssertionFailedError;
            import org.opentest4j.FileInfo;

            public class JUnitTest {
                @Test
                void test() {
                    FileInfo expected = new FileInfo("/path/from", new byte[]{ 0x0 });
                    throw new AssertionFailedError(
                        "Asymmetric expected and actual objects",
                        expected,
                        "actual content"
                    );
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof FileComparisonTestAssertionFailure

        def failure = collector.failures[0] as FileComparisonTestAssertionFailure
        failure.expected == '/path/from'
        failure.expectedContent == new byte[]{0x0}
        failure.actual == 'actual content'
    }

    def "Test failure mapping works with supported types"() {
        given:
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitTest.java') << """
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import org.opentest4j.AssertionFailedError;
            import org.opentest4j.FileInfo;
            import org.opentest4j.ValueWrapper;

            public class JUnitTest {
                @Test
                void test() {
                    throw new AssertionFailedError(
                        "Testing supported type",
                        ${typeInstantiation},
                        ${typeInstantiation}
                    );
                }
            }
        """

        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof TestAssertionFailure

        def failure = collector.failures[0] as TestAssertionFailure
        failure.expected == expectedActual

        where:
        typeInstantiation | expectedActual
        'new FileInfo("/path/from", new byte[]{ 0x0 })' | "/path/from"
        '"expected"' | "expected"
        'ValueWrapper.create("expected")' | "expected"
        '1' | "1"
        'ValueWrapper.create(1)' | "1"
    }

}
