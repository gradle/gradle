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

class JUnit4FuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    protected isRerunsAllParameterizedIterations() {
        false
    }

    protected String initializationErrorSyntheticTestMethodName(String gradleVersion) {
        gradleVersion > "9.3" ?  "executionError" : "initializationError"
    }

    protected String afterClassErrorTestMethodName(String gradleVersion) {
        "classMethod"
    }

    protected String beforeClassErrorTestMethodName(String gradleVersion) {
        "classMethod"
    }

    def "handles failure in #lifecycle - exhaustive #exhaust (gradle version #gradleVersion)"(String gradleVersion, String lifecycle, boolean exhaust) {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        writeJavaTestSource """
            package acme;

            public class SuccessfulTests {
                @org.junit.${lifecycle}
                public ${lifecycle.contains('Class') ? 'static ' : ''}void lifecycle() {
                    ${flakyAssert("id", exhaust ? 3 : 2)}
                }

                @org.junit.Test
                public void successTest() {}
            }
        """

        when:
        def runner = gradleRunner(gradleVersion as String)
        def result = exhaust ? runner.buildAndFail() : runner.build()

        then:
        if (lifecycle == "BeforeClass") {
            with(result.output) {
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == (exhaust ? 3 : 2)
                it.count('successTest PASSED') == (exhaust ? 0 : 1)
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == (exhaust ? 0 : 1)
            }
        } else if (lifecycle == "AfterClass") {
            with(result.output) {
                it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == (exhaust ? 3 : 2)
                it.count('successTest PASSED') == 3
                it.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == (exhaust ? 0 : 1)
            }
        } else {
            with(result.output) {
                it.count('successTest FAILED') == (exhaust ? 3 : 2)
                it.count('successTest PASSED') == (exhaust ? 0 : 1)
            }
        }

        where:
        [gradleVersion, lifecycle, exhaust] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ['BeforeClass', 'Before', 'AfterClass', 'After'],
            [true, false]
        ])
    }

    def "handles parameterized test in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import static org.junit.Assert.assertTrue;

            import org.junit.Test;

            abstract class AbstractTest {
                private int input;
                private boolean expected;

                public AbstractTest(int input, boolean expected) {
                    this.input = input;
                    this.expected = expected;
                }

                @Test
                public void test() {
                    assertTrue(expected);
                }
            }
        """

        writeJavaTestSource """
            package acme;

            import java.util.Arrays;
            import java.util.Collection;

            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;

            @RunWith(Parameterized.class)
            public class ParameterTest extends AbstractTest {
                @Parameters(name = "{index}: test({0})={1}")
                public static Iterable<Object[]> data() {
                   return Arrays.asList(new Object[][] {
                         { 0, true }, { 1, false }
                   });
                }

                public ParameterTest(int input, boolean expected) {
                    super(input, expected);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:

        with(result.output) {
            it.count('test[0: test(0)=true]') == (isRerunsAllParameterizedIterations() ? 2 : 1)
            it.count('test[1: test(1)=false]') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun on failure in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            abstract class AbstractTest {
                @org.junit.Test
                public void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeJavaTestSource """
            package acme;

            public class FlakyTests extends AbstractTest {
                @org.junit.Test
                public void inherited() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:

        with(result.output) {
            it.count('parent FAILED') == 1
            it.count('parent PASSED') == 1
            it.count('inherited PASSED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles flaky runner (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.junit.runner.Runner;
            import org.junit.runners.BlockJUnit4ClassRunner;
            import org.junit.runner.RunWith;
            import org.junit.runner.notification.RunNotifier;

            @RunWith(FlakyTests.MyRunner.class)
            public class FlakyTests {

                public static class MyRunner extends BlockJUnit4ClassRunner {
                    public MyRunner(Class<?> type) throws Exception {
                        super(type);
                    }

                    public void run(RunNotifier notifier) {
                        ${flakyAssert()}
                        super.run(notifier);
                    }
                }

                @org.junit.Test
                public void someTest() {
                }
            }


        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:

        with(result.output) {
            it.count("FlakyTests > ${initializationErrorSyntheticTestMethodName(gradleVersion)} FAILED") == 1
            it.count('FlakyTests > someTest PASSED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles flaky static initializer (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            public class FlakyTests {
                static {
                    ${flakyAssert()}
                }

                @org.junit.Test
                public void someTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('FlakyTests > someTest FAILED') == 1
            it.count('java.lang.ExceptionInInitializerError') == 1
            it.count('FlakyTests > someTest PASSED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles parameterized tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import static org.junit.Assert.assertTrue;

            import java.util.Arrays;
            import java.util.Collection;

            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;

            @RunWith(Parameterized.class)
            public class ParameterTest {
                @Parameters(name = "{index}: test({0})={1}")
                public static Iterable<Object[]> data() {
                   return Arrays.asList(new Object[][] {
                         { 0, true }, { 1, false }
                   });
                }

                private int input;
                private boolean expected;

                public ParameterTest(int input, boolean expected) {
                    this.input = input;
                    this.expected = expected;
                }

                @Test
                public void test() {
                    assertTrue(expected);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        with(result.output) {
            it.count('test[0: test(0)=true] PASSED') == (isRerunsAllParameterizedIterations() ? 2 : 1)
            it.count('test[1: test(1)=false] FAILED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/52")
    def "test that is skipped after failure is considered to be still failing (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;
            import java.nio.file.*;

            public class FlakyTests {
                @org.junit.Test
                public void flakyAssumeTest() {
                   ${flakyAssert()};
                   org.junit.Assume.assumeFalse(${markerFileExistsCheck()});
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        with(result.output) {
            it.count('flakyAssumeTest FAILED') == 1
            it.count('flakyAssumeTest SKIPPED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "test that is skipped after failure with option 'failOnSkippedAfterRetry = false' is passes (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
            test.retry.failOnSkippedAfterRetry = false
        """

        writeJavaTestSource """
            package acme;
            import java.nio.file.*;

            public class FlakyTests {
                @org.junit.Test
                public void flakyAssumeTest() {
                   ${flakyAssert()};
                   org.junit.Assume.assumeFalse(${markerFileExistsCheck()});
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('flakyAssumeTest FAILED') == 1
            it.count('flakyAssumeTest SKIPPED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles failure in rule before = #failBefore (gradle version #gradleVersion)"(String gradleVersion, boolean failBefore) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;
            import java.nio.file.*;

            public class FlakyTests {

                public static class FailingRule implements org.junit.rules.TestRule {

                    @Override
                    public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description) {
                        return new org.junit.runners.model.Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                ${failBefore ? flakyAssert() : ""}
                                try {
                                    base.evaluate();
                                } finally {
                                    ${!failBefore ? flakyAssert() : ""}
                                }
                            }
                        };
                    }

                }

                @org.junit.Rule
                public FailingRule rule = new FailingRule();

                @org.junit.Test
                public void ruleTest() {

                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('ruleTest FAILED') == 1
            it.count("ruleTest PASSED") == 1
        }

        where:
        [gradleVersion, failBefore] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            [true, false]
        ])
    }

    def "handles failure in class rule before = #failBefore (gradle version #gradleVersion)"(String gradleVersion, boolean failBefore) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;
            import java.nio.file.*;

            public class FlakyTests {

                public static class FailingRule implements org.junit.rules.TestRule {

                    @Override
                    public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description) {
                        return new org.junit.runners.model.Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                ${failBefore ? flakyAssert() : ""}
                                try {
                                    base.evaluate();
                                } finally {
                                    ${!failBefore ? flakyAssert() : ""}
                                }
                            }
                        };
                    }

                }

                @org.junit.ClassRule
                public static FailingRule rule = new FailingRule();

                @org.junit.Test
                public void ruleTest() {

                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        if (failBefore) {
            with(result.output) {
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 1
                it.count("ruleTest PASSED") == 1
            }
        } else {
            with(result.output) {
                it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 1
                it.count("ruleTest PASSED") == 2
            }
        }

        where:
        [gradleVersion, failBefore] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            [true, false]
        ])
    }

    def "can rerun on whole class via className (gradle version #gradleVersion)"() {
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

            public class FlakyTests {
                @org.junit.Test
                public void a() {
                }

                @org.junit.Test
                public void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b FAILED') == 1
            it.count('b PASSED') == 1
            it.count('a PASSED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun on whole class via annotation (gradle version #gradleVersion and retry annotation #retryAnnotation)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'com.gradle:develocity-testing-annotations:2.0'
                testImplementation 'com.gradle:gradle-enterprise-testing-annotations:1.1.2'
            }
            test.retry {
                maxRetries = 1
                classRetry {
                    includeAnnotationClasses.add('*CustomClassRetry')
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
            public @interface CustomClassRetry {

            }
        """

        writeJavaTestSource """
            package acme;

            @$retryAnnotation
            public class FlakyTests {
                @org.junit.Test
                public void a() {
                }

                @org.junit.Test
                public void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b FAILED') == 1
            it.count('b PASSED') == 1
            it.count('a PASSED') == 2
        }

        where:
        [gradleVersion, retryAnnotation] << [
            GRADLE_VERSIONS_UNDER_TEST,
            ["acme.CustomClassRetry", "com.gradle.enterprise.testing.annotations.ClassRetry", "com.gradle.develocity.testing.annotations.ClassRetry"]
        ].combinations()
    }

    def "handles flaky setup that prevents the retries of initially failed methods (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        and:
        writeJavaTestSource """
            package acme;

            public class FlakySetupAndMethodTest {
                @org.junit.BeforeClass
                public static void setup() {
                    ${flakyAssertPassFailPass("setup")}
                }

                @org.junit.Test
                public void flakyTest() {
                    ${flakyAssert("method")}
                }

                @org.junit.Test
                public void successfulTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('flakyTest FAILED') == 1
            it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 1
            it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
            it.count('flakyTest PASSED') == 1
            it.count('successfulTest PASSED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles setup failure after cleanup failure (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        and:
        writeJavaTestSource """
            package acme;

            public class FlakySetupAndCleanupTest {
                @org.junit.BeforeClass
                public static void setup() {
                    ${flakyAssertPassFailPass("setup")}
                }

                @org.junit.AfterClass
                public static void cleanup() {
                    ${flakyAssert("cleanup")}
                }

                @org.junit.Test
                public void flakyTest() {
                    ${flakyAssert("method")}
                }

                @org.junit.Test
                public void successfulTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        def differentiatesBetweenSetupAndCleanupMethods = beforeClassErrorTestMethodName(gradleVersion) != afterClassErrorTestMethodName(gradleVersion)
        with(result.output) {
            it.count('flakyTest FAILED') == 1
            it.count('flakyTest PASSED') == 1
            it.count('successfulTest PASSED') == 2

            if (differentiatesBetweenSetupAndCleanupMethods) {
                it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 1
                it.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 1
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
            } else {
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
            }
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }
}
