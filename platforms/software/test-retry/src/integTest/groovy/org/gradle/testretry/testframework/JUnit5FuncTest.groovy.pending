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
import org.gradle.util.GradleVersion

import javax.annotation.Nullable

class JUnit5FuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    private static String afterClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "executionError"
    }

    private static String beforeClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "initializationError"
    }

    private static String classAndMethodForSuite(String className, String testName, String gradleVersion) {
        gradleVersion == "5.0" ? "${className}.${testName}" : "${className} > ${testName}"
    }

    private static String classAndMethodForNested(String parentClassName, @Nullable String nestedClassName, String testName, String gradleVersion) {
        if (nestedClassName == null) {
            "${parentClassName} > ${testName}"
        } else {
            gradleVersion == "5.0"
                ? "${nestedClassName}.${testName}"
                : "${nestedClassName} > ${testName}"
        }
    }

    def "handles failure in #lifecycle - exhaustive #exhaust (gradle version #gradleVersion)"(String gradleVersion, String lifecycle, boolean exhaust) {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        writeJavaTestSource """
            package acme;

            class SuccessfulTests {
                @org.junit.jupiter.api.${lifecycle}
                ${lifecycle.contains('All') ? 'static ' : ''}void lifecycle() {
                    ${flakyAssert("id", exhaust ? 3 : 2)}
                }

                @org.junit.jupiter.api.Test
                void successTest() {}
            }
        """

        when:
        def runner = gradleRunner(gradleVersion as String)
        def result = exhaust ? runner.buildAndFail() : runner.build()

        then:
        if (exhaust) {
            if (lifecycle == "BeforeAll") {
                with(result.output) {
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 3
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 0
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 0
                }
            } else if (lifecycle == "BeforeEach" || lifecycle == "AfterEach") {
                with(result.output) {
                    it.count('initializationError FAILED') == 0
                    it.count('initializationError PASSED') == 0
                    it.count('successTest() FAILED') == 3
                    it.count('successTest() PASSED') == 0
                }
            } else if (lifecycle == "AfterAll") {
                with(result.output) {
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 3
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 0
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 3
                }
            }
        } else {
            if (lifecycle == "BeforeAll") {
                with(result.output) {
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 1
                }
            } else if (lifecycle == "BeforeEach" || lifecycle == "AfterEach") {
                with(result.output) {
                    it.count('initializationError FAILED') == 0
                    it.count('initializationError PASSED') == 0
                    it.count('successTest() FAILED') == 2
                    it.count('successTest() PASSED') == 1
                }
            } else if (lifecycle == "AfterAll") {
                with(result.output) {
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 3
                }
            }
        }

        where:
        [gradleVersion, lifecycle, exhaust] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ['BeforeAll', 'BeforeEach', 'AfterAll', 'AfterEach'],
            [true, false]
        ])
    }

    def "handles flaky static initializers (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            class SomeTests {
                static {
                    ${flakyAssert()}
                }

                @org.junit.jupiter.api.Test
                void someTest() {}
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).build()

        then:
        with(result.output) {
            it.count('SomeTests > someTest() PASSED') == 1
            it.count('SomeTests > someTest() FAILED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles parameterized test in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            abstract class AbstractTest {
                @ParameterizedTest(name = "test(int)[{index}]")
                @ValueSource(ints = {0, 1})
                void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        writeJavaTestSource """
            package acme;

            class ParameterTest extends AbstractTest {
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        with(result.output) {
            it.count('test(int)[1] PASSED') == 2
            it.count('test(int)[2] FAILED') == 2
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
                @org.junit.jupiter.api.Test
                void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeJavaTestSource """
            package acme;

            class FlakyTests extends AbstractTest {
                @org.junit.jupiter.api.Test
                void inherited() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('parent() FAILED') == 1
            it.count('parent() PASSED') == 1
            it.count('inherited() PASSED') == 1
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

            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            class ParameterTest {
                @ParameterizedTest(name = "test(int)[{index}]")
                @ValueSource(ints = {0, 1})
                void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        result.output.count('test(int)[1] PASSED') == 2
        result.output.count('test(int)[2] FAILED') == 2

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

            import org.junit.jupiter.api.*;
            import java.nio.file.*;

            import static org.junit.jupiter.api.Assumptions.assumeFalse;

            class FlakyTests {
                @Test
                void flakyAssumeTest() {
                    ${flakyAssert()}
                    Assumptions.assumeFalse(${markerFileExistsCheck()});
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('flakyAssumeTest() FAILED') == 1
            it.count('flakyAssumeTest() SKIPPED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
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

            class FlakyTests {
                @org.junit.jupiter.api.Test
                void a() {
                }

                @org.junit.jupiter.api.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b() FAILED') == 1
            it.count('b() PASSED') == 1
            it.count('a() PASSED') == 2
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
            class FlakyTests {
                @org.junit.jupiter.api.Test
                void a() {
                }

                @org.junit.jupiter.api.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b() FAILED') == 1
            it.count('b() PASSED') == 1
            it.count('a() PASSED') == 2
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
                @org.junit.jupiter.api.BeforeAll
                public static void setup() {
                    ${flakyAssertPassFailPass("setup")}
                }

                @org.junit.jupiter.api.Test
                public void flakyTest() {
                    ${flakyAssert("method")}
                }

                @org.junit.jupiter.api.Test
                public void successfulTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('flakyTest() FAILED') == 1
            it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 1
            it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
            it.count('flakyTest() PASSED') == 1
            it.count('successfulTest() PASSED') == 2
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

            public class FlakySetupAndMethodTest {
                @org.junit.jupiter.api.BeforeAll
                public static void setup() {
                    ${flakyAssertPassFailPass("setup")}
                }

                @org.junit.jupiter.api.AfterAll
                public static void cleanup() {
                    ${flakyAssert("cleanup")}
                }

                @org.junit.jupiter.api.Test
                public void flakyTest() {
                    ${flakyAssert("method")}
                }

                @org.junit.jupiter.api.Test
                public void successfulTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        def differentiatesBetweenSetupAndCleanupMethods = beforeClassErrorTestMethodName(gradleVersion) != afterClassErrorTestMethodName(gradleVersion)
        with(result.output) {
            it.count('flakyTest() FAILED') == 1
            it.count('flakyTest() PASSED') == 1
            it.count('successfulTest() PASSED') == 2

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

    def "handles setup failure caused by errors in discovery (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
            
            dependencies {
                testCompileOnly gradleApi()
            }
        """

        and:
        writeJavaTestSource """
            package acme;

            import org.gradle.api.Task;

            public class DiscoveryClassLoadingErrorTest {
                // Add a reliance on class that won't be present at runtime,
                // but also won't fail immediately at link time.
                // It needs to fail specifically during discovery.
                public Task dummyTaskMethod() {
                    return null;
                }

                @org.junit.jupiter.api.Test
                public void unimportantTestMethod() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        with(result.output) {
            it.contains("> There were failing tests.")
            it.count("initializationError FAILED") == 3
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun the whole class in JUnit5's Suite via className (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation('${junitPlatformSuiteEngineDependency()}')
            }
            test {
                useJUnitPlatform {
                    excludeEngines('junit-jupiter')
                }
                filter {
                    includeTestsMatching('*TestSuite')
                }
                retry {
                    maxRetries = 1
                    classRetry {
                        includeClasses.add('*Test1')
                    }
                }
            }
        """

        (1..2).collect {
            writeJavaTestSource """
                package acme;

                import org.junit.jupiter.api.*;

                public class Test${it} {
                    @Test
                    void testOk() {
                    }

                    @Test
                    void testFlaky() {
                        ${flakyAssert("${it}")}
                    }

                }
            """
        }

        writeJavaTestSource """
            package acme;

            import org.junit.jupiter.api.*;
            import org.junit.platform.suite.api.*;

            @Suite
            @SelectClasses({Test1.class,Test2.class})
            public class TestSuite {
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count("${classAndMethodForSuite("Test1", "testOk()", gradleVersion)} PASSED") == 2
            it.count("${classAndMethodForSuite("Test1", "testFlaky()", gradleVersion)} FAILED") == 1
            it.count("${classAndMethodForSuite("Test1", "testFlaky()", gradleVersion)} PASSED") == 1

            // Test2 is retried on method level
            it.count("${classAndMethodForSuite("Test2", "testOk()", gradleVersion)} PASSED") == 1
            it.count("${classAndMethodForSuite("Test2", "testFlaky()", gradleVersion)} FAILED") == 1
            it.count("${classAndMethodForSuite("Test2", "testFlaky()", gradleVersion)} PASSED") == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun the whole @Nested class via className (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test {
                retry {
                    maxRetries = 1
                    classRetry {
                        includeClasses.add('*NestedTest')
                    }
                }
            }
        """

        writeJavaTestSource """
            package acme;

            import org.junit.jupiter.api.*;

            public class TopLevelTest {
                @Test
                void testOk() {
                }

                @Test
                void testFlaky() {
                    ${flakyAssert("topLevel")}
                }

                @Nested
                class NestedTest {
                    @Test
                    void testOk() {
                    }

                    @Test
                    void testFlaky() {
                        ${flakyAssert("nested1")}
                    }
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            // only failing methods of TopLevelTest should be retried
            it.count("${classAndMethodForNested('TopLevelTest', null, 'testOk()', gradleVersion)} PASSED") == 1
            it.count("${classAndMethodForNested('TopLevelTest', null, 'testFlaky()', gradleVersion)} FAILED") == 1
            it.count("${classAndMethodForNested('TopLevelTest', null, 'testFlaky()', gradleVersion)} PASSED") == 1

            // all methods of NestedTest1 should be retried
            it.count("${classAndMethodForNested('TopLevelTest', 'NestedTest', 'testOk()', gradleVersion)} PASSED") == 2
            it.count("${classAndMethodForNested('TopLevelTest', 'NestedTest', 'testFlaky()', gradleVersion)} FAILED") == 1
            it.count("${classAndMethodForNested('TopLevelTest', 'NestedTest', 'testFlaky()', gradleVersion)} PASSED") == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun whole class including all @Nested classes via className (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test {
                retry {
                    maxRetries = 1
                    classRetry {
                        includeClasses.add('*TopLevelTest')
                    }
                }
            }
        """

        writeJavaTestSource """
            package acme;

            import org.junit.jupiter.api.*;

            public class TopLevelTest {
                @Test
                void testOk() {
                }

                @Nested
                class NestedTest1 {
                    @Test
                    void testOk() {
                    }

                    @Test
                    void testFlaky() {
                        ${flakyAssert("topLevel")}
                    }
                }

                @Nested
                class NestedTest2 {
                    @Test
                    void testOk() {
                    }
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            // all methods of TopLevelTest are rerun
            it.count("${classAndMethodForNested('TopLevelTest', null, 'testOk()', gradleVersion)} PASSED") == 2

            // all methods of nested classes are retried
            it.count("${classAndMethodForNested('TopLevelTest', 'NestedTest1', 'testOk()', gradleVersion)} PASSED") == 2
            it.count("${classAndMethodForNested('TopLevelTest', 'NestedTest1', 'testFlaky()', gradleVersion)} FAILED") == 1
            it.count("${classAndMethodForNested('TopLevelTest', 'NestedTest1', 'testFlaky()', gradleVersion)} PASSED") == 1

            it.count("${classAndMethodForNested('TopLevelTest', 'NestedTest2', 'testOk()', gradleVersion)} PASSED") == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "supports dynamic tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test {
                retry {
                    maxRetries = 1
                }
            }
        """

        writeJavaTestSource """
            package acme;

            import org.junit.jupiter.api.*;
            import java.util.stream.Stream;

            class MyTest {
                @TestFactory
                DynamicContainer dynamicContainerTest() {
                    return DynamicContainer.dynamicContainer("container", Stream.of(
                        DynamicTest.dynamicTest("test name 1", () -> {
                            ${flakyAssert()}
                        })
                    ));
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        def gv = GradleVersion.version(gradleVersion)
        def testname = dynamicTestName(gv)

        with(result.output) {
            it.count("${testname} FAILED") == 1
            it.count("${testname} PASSED") == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private static String dynamicTestName(GradleVersion gv) {
        if (gv >= GradleVersion.version("8.8")) {
            return 'MyTest > dynamicContainerTest() > container > test name 1'
        } else if ((gv >= GradleVersion.version("8.1") && gv < GradleVersion.version("8.8")) || (gv >= GradleVersion.version("7.6") && gv < GradleVersion.version("8.0"))) {
            return 'MyTest > dynamicContainerTest() > container > acme.MyTest.test name 1'
        } else if ((gv >= GradleVersion.version("7.0") && gv < GradleVersion.version("7.6")) || (gv >= GradleVersion.version("8.0") && gv < GradleVersion.version("8.1"))) {
            return 'MyTest > dynamicContainerTest() > container > acme.MyTest.dynamicContainerTest()[1][1]'
        } else if ((gv >= GradleVersion.version("6.7") && gv < GradleVersion.version("7.0")) || (gv >= GradleVersion.version("6.1") && gv < GradleVersion.version("6.6"))) {
            return 'MyTest > test name 1'
        } else if (gv >= GradleVersion.version("6.6") && gv < GradleVersion.version("6.7")) {
            return 'acme.MyTest > test name 1'
        } else {
            return 'acme.MyTest > dynamicContainerTest()[1][1]'
        }
    }

    String reportedTestName(String testName) {
        testName + "()"
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation '${jupiterApiDependency()}'
                testImplementation '${jupiterParamsDependency()}'
                testRuntimeOnly '${jupiterEngineDependency()}'
                testRuntimeOnly '${junitPlatformLauncherDependency()}'
            }
            test {
                useJUnitPlatform()
            }
        """
    }
}
