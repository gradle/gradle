/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.tooling.r76

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.TestAssertionFailure
import org.gradle.tooling.TestFrameworkFailure
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.TestFailureResult
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOperationResult

@ToolingApiVersion(">=7.6")
@TargetGradleVersion(">=7.6")
class TestFailureProgressEventCrossVersionTest extends ToolingApiSpecification {

    ProgressEventCollector progressEventCollector

    def setup() {
        // Avoid mixing JUnit dependencies with the ones from the JVM running this test
        // For example, when using PTS/TD for running this test, the JUnit Platform Launcher classes from the GE plugin take precedence
        toolingApi.requireDaemons()
        progressEventCollector = new ProgressEventCollector()
    }

    def "Emits test failure events for Junit 3 tests"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testCompileOnly 'junit:junit:3.8.1'
                testRuntimeOnly 'junit:junit:4.13.2'
            }
        """
        file("src/test/java/FooTest.java") << """
            import junit.framework.*;

            public class FooTest extends TestCase {
                public void testFail() {
                    assertEquals("String are not equal:", "foo", "bar");
                }
            }
        """

        when:
        runTestTaskWithFailureCollection()

        then:
        thrown(BuildException)
        List<TestAssertionFailure> assertionFailures = failures.findAll { println it.stacktrace; it instanceof TestAssertionFailure }
        List<TestFrameworkFailure> frameworkFailures = failures.findAll { it instanceof TestFrameworkFailure }

        assertionFailures.size() == 1
        frameworkFailures.size() == 0
        failures.size() == assertionFailures.size() + frameworkFailures.size()

        assertionFailures[0].message == "String are not equal: expected:<[foo]> but was:<[bar]>"
        assertionFailures[0].description.length() > 100
        assertionFailures[0].description.contains('junit.framework.ComparisonFailure')
        assertionFailures[0].causes.empty
        assertionFailures[0].className == 'junit.framework.ComparisonFailure'
        assertionFailures[0].stacktrace == assertionFailures[0].description
        assertionFailures[0].expected == "foo"
        assertionFailures[0].actual == "bar"
    }

    def "Emits test failure events for Junit 4 tests"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """
        file('src/test/java/FailingTest.java') << '''
            import org.junit.ComparisonFailure;
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class FailingTest {

                @Test
                public void pass() {
                }

                @Test
                public void failWithAssertStatement() {
                    assert false;
                }

                @Test
                public void failWithUnexpectedRuntimeException() {
                    throw new IllegalStateException("Boom!");
                }

                @Test
                public void failWithBrokenAssertion() {
                    assertEquals("This should fail.", "myExpectedValue", "myActualValue");
                }

                @Test
                public void failWitCustomComparisonError() {
                    throw new ComparisonFailure("Custom ComparisonFailure.", "comparison expected value", "comparison actual value");
                }
            }
        '''

        when:
        runTestTaskWithFailureCollection()

        then:
        thrown(BuildException)
        List<TestAssertionFailure> assertionFailures = failures.findAll { it instanceof TestAssertionFailure }
        List<TestFrameworkFailure> frameworkFailures = failures.findAll { it instanceof TestFrameworkFailure }

        assertionFailures.size() == 3
        frameworkFailures.size() == 1
        failures.size() == assertionFailures.size() + frameworkFailures.size()

        assertionFailures[0].message == null
        assertionFailures[0].description.length() > 100
        assertionFailures[0].description.contains('java.lang.AssertionError')
        assertionFailures[0].causes.empty
        assertionFailures[0].className == 'java.lang.AssertionError'
        assertionFailures[0].stacktrace == assertionFailures[0].description
        assertionFailures[0].expected == null
        assertionFailures[0].actual == null

        assertionFailures[1].message == 'Custom ComparisonFailure. expected:<comparison [expected] value> but was:<comparison [actual] value>'
        assertionFailures[1].description.length() > 100
        assertionFailures[1].description.contains('org.junit.ComparisonFailure: Custom ComparisonFailure. expected:<comparison [expected] value> but was:<comparison [actual] value>')
        assertionFailures[1].causes.empty
        assertionFailures[1].className == 'org.junit.ComparisonFailure'
        assertionFailures[1].stacktrace == assertionFailures[1].description
        assertionFailures[1].expected == 'comparison expected value'
        assertionFailures[1].actual == 'comparison actual value'

        assertionFailures[2].message == 'This should fail. expected:<my[Expected]Value> but was:<my[Actual]Value>'
        assertionFailures[2].description.length() > 100
        assertionFailures[2].description.contains('org.junit.ComparisonFailure: This should fail. expected:<my[Expected]Value> but was:<my[Actual]Value>')
        assertionFailures[2].causes.empty
        assertionFailures[2].className == 'org.junit.ComparisonFailure'
        assertionFailures[2].stacktrace == assertionFailures[2].description
        assertionFailures[2].expected == 'myExpectedValue'
        assertionFailures[2].actual == 'myActualValue'

        frameworkFailures[0].message == 'Boom!'
        frameworkFailures[0].description.length() > 100
        frameworkFailures[0].description.contains('java.lang.IllegalStateException')
        frameworkFailures[0].causes.empty
        frameworkFailures[0].className == 'java.lang.IllegalStateException'
        frameworkFailures[0].stacktrace == frameworkFailures[0].description
    }

    def "Emits test failure events for Junit 5 tests"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13.2'
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.1'
                testImplementation 'org.junit.jupiter:junit-jupiter-engine:5.7.1'
            }

            test {
                useJUnitPlatform()
            }
        """

        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            public class JUnitJupiterTest {

                @Test
                public void pass() {
                }

                @Test
                public void failWithAssertStatement() {
                    assert false;
                }

                @Test
                public void failWithBrokenAssertion() {
                    assertEquals("myExpectedValue", "myActualValue", "JUnit 5 assertion failure");
                }

                @Test
                public void failWithUnexpectedRuntimeException() {
                    throw new IllegalStateException("Boom!");
                }
            }
        '''

        when:
        runTestTaskWithFailureCollection()

        then:
        thrown(BuildException)
        List<TestAssertionFailure> assertionFailures = failures.findAll { it instanceof TestAssertionFailure }
        List<TestFrameworkFailure> frameworkFailures = failures.findAll { it instanceof TestFrameworkFailure }

        assertionFailures.size() == 2
        frameworkFailures.size() == 1
        failures.size() == assertionFailures.size() + frameworkFailures.size()

        assertionFailures[0].message == null
        assertionFailures[0].description.length() > 100
        assertionFailures[0].description.contains('java.lang.AssertionError')
        assertionFailures[0].causes.empty
        assertionFailures[0].className == 'java.lang.AssertionError'
        assertionFailures[0].stacktrace == assertionFailures[0].description
        assertionFailures[0].expected == null
        assertionFailures[0].actual == null

        assertionFailures[1].message == 'JUnit 5 assertion failure ==> expected: <myExpectedValue> but was: <myActualValue>'
        assertionFailures[1].description.length() > 100
        assertionFailures[1].description.contains('org.opentest4j.AssertionFailedError: JUnit 5 assertion failure ==> expected: <myExpectedValue> but was: <myActualValue>')
        assertionFailures[1].causes.empty
        assertionFailures[1].className == 'org.opentest4j.AssertionFailedError'
        assertionFailures[1].stacktrace == assertionFailures[1].description
        assertionFailures[1].expected == 'myExpectedValue'
        assertionFailures[1].actual == 'myActualValue'

        frameworkFailures[0].message == 'Boom!'
        frameworkFailures[0].description.length() > 100
        frameworkFailures[0].description.contains('java.lang.IllegalStateException')
        frameworkFailures[0].causes.empty
        frameworkFailures[0].className == 'java.lang.IllegalStateException'
        frameworkFailures[0].stacktrace == frameworkFailures[0].description
    }

    def "Emits single failure with multiple causes for multiple failures error"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.1'
                testImplementation 'org.junit.jupiter:junit-jupiter-engine:5.7.1'
                testImplementation 'org.assertj:assertj-core:3.22.0'
            }

            test {
                useJUnitPlatform()
            }
        """

        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            public class JUnitJupiterTest {

                @Test
                public void failWithMultipleFailuresError() {
                    assertAll(() -> assertEquals("myExpected", "myActual"),
                              () -> assertEquals("myOtherExpected", "myOtherActual"));
                }
            }
        '''

        file('src/test/java/org/gradle/AssertjMultipleFailureException.java') << '''
            package org.gradle;

            import org.assertj.core.error.MultipleAssertionsError;
            import org.junit.jupiter.api.Test;

            import java.util.ArrayList;
            import java.util.List;

            import static org.assertj.core.api.Assertions.assertThat;
            import static org.junit.jupiter.api.Assertions.assertAll;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            public class AssertjMultipleFailureException {

                    @Test
                    void assertJ() {
                        List<AssertionError> errors = new ArrayList<>();
                        try {
                            assertThat("actual1").describedAs("message1").isEqualTo("expected1");
                        } catch (AssertionError e) {
                            errors.add(e);
                        }
                        try {
                            assertThat(23).describedAs("message2").isEqualTo(42);
                        } catch (AssertionError e) {
                            errors.add(e);
                        }
                        throw new MultipleAssertionsError(errors);
                    }
            }
        '''

        when:
        runTestTaskWithFailureCollection()

        then:
        thrown(BuildException)
        List<TestAssertionFailure> assertionFailures = failures.findAll { it instanceof TestAssertionFailure }
        List<TestFrameworkFailure> frameworkFailures = failures.findAll { it instanceof TestFrameworkFailure }

        assertionFailures.size() == 2
        frameworkFailures.size() == 0
        failures.size() == assertionFailures.size() + frameworkFailures.size()

        // org.assertj.core.error.MultipleAssertionsError
        assertionFailures[0].message.contains("The following 2 assertions failed:")
        assertionFailures[0].description.length() > 100
        assertionFailures[0].description.contains('org.assertj.core.error.MultipleAssertionsError')
        assertionFailures[0].description.contains('The following 2 assertions failed:')
        assertionFailures[0].causes.size() == 2
        assertionFailures[0].className == 'org.assertj.core.error.MultipleAssertionsError'
        assertionFailures[0].stacktrace == assertionFailures[0].description
        assertionFailures[0].stacktrace == assertionFailures[0].description
        assertionFailures[0].expected == null
        assertionFailures[0].actual == null

        assertionFailures[0].causes[0] instanceof TestAssertionFailure
        TextUtil.normaliseLineSeparators(assertionFailures[0].causes[0].message) == '[message1] \nexpected: "expected1"\n but was: "actual1"'
        assertionFailures[0].causes[0].description.length() > 100
        assertionFailures[0].causes[0].description.contains('org.opentest4j.AssertionFailedError')
        assertionFailures[0].causes[0].causes.empty
        assertionFailures[0].causes[0].className == 'org.opentest4j.AssertionFailedError'
        assertionFailures[0].causes[0].stacktrace == assertionFailures[0].causes[0].description
        assertionFailures[0].causes[0].expected == '"expected1"'
        assertionFailures[0].causes[0].actual == '"actual1"'

        assertionFailures[0].causes[1] instanceof TestAssertionFailure
        TextUtil.normaliseLineSeparators(assertionFailures[0].causes[1].message) == '[message2] \nexpected: 42\n but was: 23'
        assertionFailures[0].causes[1].description.length() > 100
        assertionFailures[0].causes[1].description.contains('org.opentest4j.AssertionFailedError')
        assertionFailures[0].causes[1].causes.empty
        assertionFailures[0].causes[1].className == 'org.opentest4j.AssertionFailedError'
        assertionFailures[0].causes[1].stacktrace == assertionFailures[0].causes[1].description
        assertionFailures[0].causes[1].expected == '42'
        assertionFailures[0].causes[1].actual == '23'

        and: // org.opentest4j.MultipleFailuresError
        assertionFailures[1].description.length() > 100
        assertionFailures[1].description.contains('Multiple Failures')
        assertionFailures[1].causes.size() == 2
        assertionFailures[1].className == 'org.opentest4j.MultipleFailuresError'
        assertionFailures[1].stacktrace == assertionFailures[1].description
        assertionFailures[1].expected == null
        assertionFailures[1].actual == null

        assertionFailures[1].causes[0] instanceof TestAssertionFailure
        assertionFailures[1].causes[0].message == "expected: <myExpected> but was: <myActual>"
        assertionFailures[1].causes[0].description.length() > 100
        assertionFailures[1].causes[0].description.contains('org.opentest4j.AssertionFailedError')
        assertionFailures[1].causes[0].causes.empty
        assertionFailures[1].causes[0].className == 'org.opentest4j.AssertionFailedError'
        assertionFailures[1].causes[0].stacktrace == assertionFailures[1].causes[0].description
        assertionFailures[1].causes[0].expected == 'myExpected'
        assertionFailures[1].causes[0].actual == 'myActual'

        assertionFailures[1].causes[1] instanceof TestAssertionFailure
        assertionFailures[1].causes[1].message == "expected: <myOtherExpected> but was: <myOtherActual>"
        assertionFailures[1].causes[1].description.length() > 100
        assertionFailures[1].causes[1].description.contains('org.opentest4j.AssertionFailedError')
        assertionFailures[1].causes[1].causes.empty
        assertionFailures[1].causes[1].className == 'org.opentest4j.AssertionFailedError'
        assertionFailures[1].causes[1].stacktrace == assertionFailures[1].causes[1].description
        assertionFailures[1].causes[1].expected == 'myOtherExpected'
        assertionFailures[1].causes[1].actual == 'myOtherActual'
    }

    def "Emits test failure events for TestNG tests"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            ${RepoScriptBlockUtil.mavenCentralRepository()}
            testing {
                suites {
                    test {
                        useTestNG('7.4.0')
                    }
                }
            }
        """

        file('src/test/java/AppException.java') << 'public class AppException extends Exception {}'
        file('src/test/java/SomeTest.java') << '''
            import org.testng.annotations.Test;
            import static org.testng.Assert.assertEquals;

            public class SomeTest {
                @Test
                public void pass() {}

                @Test
                public void failWithAssertStatement() {
                    assert false;
                }

                @Test
                public void failWithRuntimeException() {
                    throw new RuntimeException("TestNG runtime exception");
                }

                @Test
                public void failWithCustomRuntimeException() throws AppException {
                    throw new AppException();
                }

                @Test
                public void failWithBrokenAssertion() {
                    assertEquals("myActualValue", "myExpectedValue");
                }
            }
        '''

        when:
        runTestTaskWithFailureCollection()

        then:
        thrown(BuildException)
        List<TestAssertionFailure> assertionFailures = failures.findAll { it instanceof TestAssertionFailure }
        List<TestFrameworkFailure> frameworkFailures = failures.findAll { it instanceof TestFrameworkFailure }

        assertionFailures.size() == 2
        frameworkFailures.size() == 2
        failures.size() == assertionFailures.size() + frameworkFailures.size()

        assertionFailures[0].message == null
        assertionFailures[0].description.length() > 100
        assertionFailures[0].description.contains('java.lang.AssertionError')
        assertionFailures[0].causes.empty
        assertionFailures[0].className == 'java.lang.AssertionError'
        assertionFailures[0].stacktrace == assertionFailures[0].description
        assertionFailures[0].expected == null
        assertionFailures[0].actual == null

        assertionFailures[1].message == 'expected [myExpectedValue] but found [myActualValue]'
        assertionFailures[1].description.length() > 100
        assertionFailures[1].description.contains("java.lang.AssertionError: expected [myExpectedValue] but found [myActualValue]")
        assertionFailures[1].causes.empty
        assertionFailures[1].className == 'java.lang.AssertionError'
        assertionFailures[1].stacktrace == assertionFailures[1].description
        assertionFailures[1].expected == null
        assertionFailures[1].actual == null

        frameworkFailures[0].message == null
        frameworkFailures[0].description.length() > 100
        frameworkFailures[0].description.contains('AppException')
        frameworkFailures[0].causes.empty
        frameworkFailures[0].className == 'AppException'
        frameworkFailures[0].stacktrace == frameworkFailures[0].description

        frameworkFailures[1].message == 'TestNG runtime exception'
        frameworkFailures[1].description.length() > 100
        frameworkFailures[1].description.contains('java.lang.RuntimeException: TestNG runtime exception')
        frameworkFailures[1].causes.empty
        frameworkFailures[1].className == 'java.lang.RuntimeException'
        frameworkFailures[1].stacktrace == frameworkFailures[1].description
    }

    List<Failure> getFailures() {
        progressEventCollector.failures
    }

    private def runTestTaskWithFailureCollection() {
        withConnection { connection ->
            connection.newBuild()
                .addProgressListener(progressEventCollector)
                .forTasks('test')
                .run()
        }
    }

    private static class ProgressEventCollector implements ProgressListener {

        public List<Failure> failures = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof TestFinishEvent) {
                TestOperationResult result = ((TestFinishEvent) event).getResult();
                if (result instanceof TestFailureResult) {
                    failures += ((TestFailureResult)result).failures
                }
            }
        }
    }
}
