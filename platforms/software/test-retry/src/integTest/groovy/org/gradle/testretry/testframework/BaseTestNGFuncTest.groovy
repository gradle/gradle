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
package org.gradle.testretry.testframework

import org.gradle.testretry.AbstractFrameworkFuncTest
import spock.lang.Issue

import javax.annotation.Nullable
import java.util.regex.Pattern

import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_CLASS
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_METHOD
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.AFTER_TEST
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.BEFORE_CLASS
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.BEFORE_METHOD
import static org.gradle.testretry.testframework.BaseTestNGFuncTest.TestNGLifecycleType.BEFORE_TEST

abstract class BaseTestNGFuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    @Override
    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'org.testng:testng:7.5'
            }
        """
    }

    enum TestNGLifecycleType {
        BEFORE_SUITE('BeforeSuite'),
        BEFORE_TEST('BeforeTest'),
        BEFORE_CLASS('BeforeClass'),
        BEFORE_METHOD('BeforeMethod'),
        AFTER_METHOD('AfterMethod'),
        AFTER_CLASS('AfterClass'),
        AFTER_TEST('AfterTest'),
        AFTER_SUITE('AfterSuite')

        final String annotation

        TestNGLifecycleType(String annotation) {
            this.annotation = annotation
        }
    }

    abstract String reportedLifecycleMethodName(TestNGLifecycleType lifecycleType, String methodName)

    abstract String reportedParameterizedMethodName(String methodName, String paramType, int invocationNumber, @Nullable String paramValueRepresentation)

    abstract boolean reportsSuccessfulLifecycleExecutions(TestNGLifecycleType lifecycleType)

    def "handles failure in #lifecycle"(TestNGLifecycleType lifecycle) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            public class SuccessfulTests {
                @org.testng.annotations.${lifecycle.annotation}
                public ${lifecycle.annotation.contains('Class') ? 'static ' : ''}void lifecycle() {
                    ${flakyAssert()}
                }

                @org.testng.annotations.Test
                public void successTest() {}
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count("${reportedLifecycleMethodName(lifecycle, 'lifecycle')} FAILED") == 1
            assert it.count("${reportedLifecycleMethodName(lifecycle, 'lifecycle')} PASSED") == (reportsSuccessfulLifecycleExecutions(lifecycle) ? 1 : 0)
            assert !it.contains("The following test methods could not be retried")
        }

        where:
        lifecycle << [BEFORE_TEST, BEFORE_CLASS, BEFORE_METHOD, AFTER_METHOD, AFTER_CLASS, AFTER_TEST]
        // Note: we don't handle BeforeSuite AfterSuite
    }

    def "correctly reports exhausted retries on failures in #lifecycle"(TestNGLifecycleType lifecycle) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            public class AlwaysFailingLifecycle {
                @org.testng.annotations.${lifecycle.annotation}
                public ${lifecycle.annotation.contains('Class') ? 'static ' : ''}void lifecycle() {
                    throw new RuntimeException("Lifecycle goes boom!");
                }

                @org.testng.annotations.Test
                public void successTest() {}
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            // if BeforeTest fails, then methods won't be executed
            assert it.count('successTest SKIPPED') == (lifecycle.annotation.contains('Before') ? 2 : 0)
            assert it.count('successTest PASSED') == (lifecycle.annotation.contains('Before') ? 0 : 2)
            assert it.count("${reportedLifecycleMethodName(lifecycle, 'lifecycle')} FAILED") == 2
            assert !it.contains("The following test methods could not be retried")
        }

        where:
        lifecycle << [BEFORE_TEST, BEFORE_CLASS, BEFORE_METHOD, AFTER_METHOD, AFTER_CLASS, AFTER_TEST]
    }

    def "handles parameterized test in super class"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.testng.annotations.*;

            import static org.testng.AssertJUnit.assertEquals;

            abstract class AbstractTest {
                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{0, 1};
                }

                @Test(dataProvider = "parameters")
                public void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        writeJavaTestSource """
            package acme;

            public class ParameterTest extends AbstractTest {
            }
        """

        when:
        fails('test')

        then:
        // we can't rerun just the failed parameter
        with(output) {
            assert it.count("${reportedParameterizedMethodName('test', 'int', 0, '0')} PASSED") == 2
            assert it.count("${reportedParameterizedMethodName('test', 'int', 1, '1')} FAILED") == 2
        }
    }

    def "can rerun on failure in super class"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            abstract class AbstractTest {
                @org.testng.annotations.Test
                void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeJavaTestSource """
            package acme;

            public class FlakyTests extends AbstractTest {
                @org.testng.annotations.Test
                public void inherited() {
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('parent FAILED') == 1
            assert it.count('parent PASSED') == 1
            assert it.count('inherited PASSED') == 1
        }
    }

    def "handles test dependencies"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.testng.annotations.*;

            public class OrderedTests {
                @Test(dependsOnMethods = {"childTest"})
                public void grandChildTest() {}

                @Test(dependsOnMethods = {"parentTest"})
                public void childTest() {
                    ${flakyAssert()}
                }

                @Test
                public void parentTest() {}
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('parentTest PASSED') == 2

            assert it.count('childTest FAILED') == 1
            assert it.count('childTest PASSED') == 1

            // grandchildTest gets skipped initially because flaky childTest failed, but is ran as part of the retry
            assert it.count('grandChildTest SKIPPED') == 1
            assert it.count('grandChildTest PASSED') == 1
        }
    }

    def "handles parameterized tests"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.testng.annotations.*;

            import static org.testng.AssertJUnit.assertEquals;

            public class ParameterTest {
                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{0, 1};
                }

                @Test(dataProvider = "parameters")
                public void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        when:
        fails('test')

        then:
        // we can't rerun just the failed parameter
        with(output) {
            assert it.count("${reportedParameterizedMethodName('test', 'int', 0, '0')} PASSED") == 2
            assert it.count("${reportedParameterizedMethodName('test', 'int', 1, '1')} FAILED") == 2
        }
    }

    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/66")
    def "handles parameters with #parameterRepresentation.name() toString() representation"(ParameterExceptionString parameterRepresentation) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.testng.annotations.*;

            import static org.testng.AssertJUnit.assertEquals;

            public class ParameterTest {
                public class Foo {
                    final int value;

                    public Foo(int value) {
                        this.value = value;
                    }

                    public String toString() {
                        return ${parameterRepresentation.representation};
                    }
                }

                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{new Foo(0), new Foo(1)};
                }

                @Test(dataProvider = "parameters")
                public void test(Foo foo) {
                    assertEquals(0, foo.value);
                }
            }
        """

        when:
        fails('test')

        then:
        // we can't rerun just the failed parameter
        with(output.readLines()) {
            assert it.findAll { line -> line.matches(/.*${Pattern.quote(reportedParameterizedMethodName('test', 'acme.ParameterTest$Foo', 0, ''))}.* PASSED/) }.size() == 2
            assert it.findAll { line -> line.matches(/.*${Pattern.quote(reportedParameterizedMethodName('test', 'acme.ParameterTest$Foo', 1, ''))}.* FAILED/) }.size() == 2
        }

        where:
        parameterRepresentation << ParameterExceptionString.values()
    }

    def "uses configured test listeners for test retry"() {
        given:
        buildFile << """
            test {
                testLogging {
                    events "standard_out"
                }

                useTestNG {
                    listeners << "acme.LoggingTestListener"
                }
                retry.maxRetries = 1
            }
        """

        writeJavaTestSource """
            package acme;

            public class SomeTests {
                @org.testng.annotations.Test
                public void someTest() {
                    ${flakyAssert()}
                }
            }
        """

        writeJavaTestSource """
            package acme;

            public class LoggingTestListener extends org.testng.TestListenerAdapter {
                @Override
                public void onTestStart(org.testng.ITestResult result) {
                    System.out.println("[LoggingTestListener] Test started: " + result.getName());
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('someTest FAILED') == 1
            assert it.count('someTest PASSED') == 1
        }

        and:
        output.count('[LoggingTestListener] Test started: someTest') == 2
    }

    def "build failed if a test has failed once but never passed"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;
            import org.testng.annotations.*;
            import java.nio.file.*;

            public class FlakyTests {
                @Test
                public void flakyAssumeTest() {
                   ${flakyAssert()};
                   if (${markerFileExistsCheck()}) {
                       throw new org.testng.SkipException("Skip me");
                   }
                }
            }
        """

        when:
        fails('test')

        then:
        with(output) {
            assert it.count('flakyAssumeTest FAILED') == 1
            assert it.count('flakyAssumeTest SKIPPED') == 1
        }
    }

    def "can rerun on whole class via className"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 1
                classRetry {
                    includeClasses.add('*FlakyTests')
                }
            }
        """

        writeJavaTestSource """
            package acme;

            class FlakyTests {
                @org.testng.annotations.Test
                void a() {
                }

                @org.testng.annotations.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('b FAILED') == 1
            assert it.count('b PASSED') == 1
            assert it.count('a PASSED') == 2
        }
    }

    def "can rerun on whole class via annotation"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 1
                classRetry {
                    includeAnnotationClasses.add('*ClassRetry')
                }
            }
        """

        writeJavaTestSource """
            package acme;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface ClassRetry {

            }
        """

        writeJavaTestSource """
            package acme;

            @ClassRetry
            class FlakyTests {
                @org.testng.annotations.Test
                void a() {
                }

                @org.testng.annotations.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('b FAILED') == 1
            assert it.count('b PASSED') == 1
            assert it.count('a PASSED') == 2
        }
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation '${testNgDependency()}'
            }
            test {
                useTestNG()
            }
        """
    }

    enum ParameterExceptionString {
        EMPTY('""'),
        NULL('null'),
        MISSING('super.toString()')

        String representation

        ParameterExceptionString(String representation) {
            this.representation = representation
        }

        String getRepresentation() {
            return representation
        }
    }
}
