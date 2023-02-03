/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.serialize

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

class ExceptionPlaceholderIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/1618")
    def "internal exception should not be thrown"() {
        given:
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
                testImplementation 'org.mockito:mockito-core:2.3.7'
            }
        """

        file('src/test/java/example/Issue1618Test.java') << '''
            package example;

            import org.junit.Test;

            import static org.mockito.Mockito.doThrow;
            import static org.mockito.Mockito.mock;

            public class Issue1618Test {

                public static class Bugger {
                    public void run() {
                    }
                }

                @Test
                public void thisTestShouldBeMarkedAsFailed() {
                    RuntimeException mockedException = mock(RuntimeException.class);
                    Bugger bugger = mock(Bugger.class);
                    doThrow(mockedException).when(bugger).run();
                    bugger.run();
                }
            }
        '''

        when:
        fails 'test'

        then:
        outputContains "example.Issue1618Test > thisTestShouldBeMarkedAsFailed FAILED"
    }

    @Issue("https://github.com/gradle/gradle/issues/9487")
    def "best effort to capture multi-cause exceptions"() {
        given:
        buildFile << """
            apply plugin: 'java-library'

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
                testImplementation 'org.opentest4j:opentest4j:1.2.0'
            }
        """

        file('src/test/java/example/Issue9487Test.java') << '''
            package example;

            import org.junit.Test;
            import org.opentest4j.MultipleFailuresError;
            import java.util.List;
            import java.util.ArrayList;

            public class Issue9487Test {

                @Test
                public void allCausesShouldBeCaptured() {
                    List<Throwable> errors = new ArrayList<Throwable>();
                    errors.add(new AssertionError("error 1"));
                    errors.add(new RuntimeException("error 2"));
                    throw new MultipleFailuresError("oh noes", errors);
                }
            }
        '''

        when:
        fails "test"

        then:
        def result = new HtmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("example.Issue9487Test")
        result.testClass("example.Issue9487Test")
            .assertTestFailed("allCausesShouldBeCaptured",
                containsString("oh noes (2 failures)"))
        result.testClass("example.Issue9487Test")
            .assertTestFailed("allCausesShouldBeCaptured",
                containsString("java.lang.AssertionError: error 1"))
        result.testClass("example.Issue9487Test")
            .assertTestFailed("allCausesShouldBeCaptured",
                containsString("java.lang.RuntimeException: error 2"))
    }

    @Issue("https://github.com/gradle/gradle/issues/9487")
    def "best effort to capture multi-cause exceptions using adhoc exception type (methodName=#methodName)"() {
        given:
        buildFile << """
            apply plugin: 'java-library'

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """

        file("src/test/java/example/AdhocError.java") << """
            package example;

            import java.util.List;

            public class AdhocError extends AssertionError {
                private final List<Throwable> causes;

                public AdhocError(List<Throwable> errors) {
                   this.causes = errors;
                }

                public List<Throwable> $methodName() {
                    return causes;
                }
            }
            """

        file('src/test/java/example/Issue9487Test.java') << '''
            package example;

            import org.junit.Test;
            import java.util.List;
            import java.util.ArrayList;

            public class Issue9487Test {

                @Test
                public void allCausesShouldBeCaptured() {
                    List<Throwable> errors = new ArrayList<Throwable>();
                    errors.add(new AssertionError("error 1"));
                    errors.add(new RuntimeException("error 2"));
                    throw new AdhocError(errors);
                }
            }


        '''

        when:
        fails "test"

        then:
        def result = new HtmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("example.Issue9487Test")
        result.testClass("example.Issue9487Test")
            .assertTestFailed("allCausesShouldBeCaptured",
                containsString("Cause 1: java.lang.AssertionError: error 1"))
        result.testClass("example.Issue9487Test")
            .assertTestFailed("allCausesShouldBeCaptured",
                containsString("Cause 2: java.lang.RuntimeException: error 2"))

        where:
        methodName << [
            'getFailures',
            'getCauses'
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/9487")
    def 'break cycles with suppressed and cause exceptions'() {
        given:
        buildFile << """
            task doIt {
                doLast {
                    RuntimeException custom = new CustomException("Dang!")
                    RuntimeException other = new IllegalStateException("Woops!", custom)
                    custom.addSuppressed(other)
                    throw new RuntimeException("Boom!", custom)
                }
            }

            class CustomException extends RuntimeException {
                CustomException(String msg) {
                    super(msg);
                }
            }
        """

        when:
        fails 'doIt', '-s'

        then:
        failureCauseContains('Boom!')
        failure.assertHasErrorOutput('Suppressed:')
        failure.assertHasErrorOutput('CIRCULAR REFERENCE:')
    }

    @Issue("https://github.com/gradle/gradle/issues/9487")
    def 'break cycles with suppressed exceptions'() {
        given:
        buildFile << """
            task doIt {
                doLast {
                    RuntimeException custom = new CustomException("Dang!")
                    RuntimeException other = new IllegalStateException("Woops!")
                    custom.addSuppressed(other)
                    other.addSuppressed(custom)
                    throw new RuntimeException("Boom!", custom)
                }
            }

            class CustomException extends RuntimeException {
                CustomException(String msg) {
                    super(msg);
                }
            }
        """

        when:
        fails 'doIt', '-s'

        then:
        failureCauseContains('Boom!')
        failure.assertHasErrorOutput('Suppressed:')
        failure.assertHasErrorOutput('CIRCULAR REFERENCE:')
    }
}
