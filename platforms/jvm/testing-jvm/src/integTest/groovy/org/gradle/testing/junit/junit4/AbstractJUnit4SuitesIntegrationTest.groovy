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

package org.gradle.testing.junit.junit4


import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.testing.junit.AbstractJUnitSuitesIntegrationTest
import org.junit.Assume

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo

abstract class AbstractJUnit4SuitesIntegrationTest extends AbstractJUnitSuitesIntegrationTest {
    abstract boolean supportsSuiteOutput()

    abstract String getTestFrameworkJUnit3Dependencies()

    @Override
    String getTestFrameworkSuiteImports() {
        return """
            import org.junit.runner.RunWith;
            import org.junit.runners.Suite;
        """.stripIndent()
    }

    @Override
    String getTestFrameworkSuiteAnnotations(String classes) {
        return """
            @RunWith(Suite.class)
            @Suite.SuiteClasses({ ${classes} })
        """.stripIndent()
    }

    @Override
    String getTestFrameworkSuiteDependencies() {
        return ""
    }

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT4
    }

    def "suite output is visible"() {
        Assume.assumeTrue(supportsSuiteOutput())

        given:
        file('src/test/java/org/gradle/ASuite.java') << """
            package org.gradle;

            ${testFrameworkImports}
            import org.junit.runner.RunWith;
            import org.junit.runners.Suite;

            @RunWith(Suite.class)
            @Suite.SuiteClasses({ OkTest.class, OtherTest.class })
            public class ASuite {
                static {
                    System.out.println("suite class loaded");
                }

                ${beforeClassAnnotation} public static void init() {
                    System.out.println("before suite class out");
                    System.out.println("non-asci char: ż");
                    System.err.println("before suite class err");
                }

                ${afterClassAnnotation} public static void end() {
                    System.out.println("after suite class out");
                    System.err.println("after suite class err");
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/OkTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class OkTest {

                ${beforeClassAnnotation} public static void init() {
                    System.out.println("before OkTest class out");
                    System.err.println("before OkTest class err");
                }

                ${afterClassAnnotation} public static void end() {
                    System.out.println("after OkTest class out");
                    System.err.println("after OkTest class err");
                }

                @Test
                public void ok() throws Exception {
                    System.err.println("This is test stderr");
                }

                @Test
                public void anotherOk() {
                    System.out.println("sys out from another test method");
                    System.err.println("sys err from another test method");
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/OtherTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class OtherTest {

                ${beforeClassAnnotation} public static void init() {
                    System.out.println("before OtherTest class out");
                    System.err.println("before OtherTest class err");
                }

                ${afterClassAnnotation} public static void end() {
                    System.out.println("after OtherTest class out");
                    System.err.println("after OtherTest class err");
                }

                @Test
                public void ok() throws Exception {
                    System.out.println("This is other stdout");
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                include '**/ASuite.class'
                exclude '**/*Test.class'
            }
        """.stripIndent()
        when:
        executer.withTasks('test').run()

        then:
        GenericHtmlTestExecutionResult result = new GenericHtmlTestExecutionResult(testDirectory, GenericTestExecutionResult.TestFramework.JUNIT4)
        result.assertTestPathsExecuted(
            ':org.gradle.ASuite:org.gradle.OkTest:ok',
            ':org.gradle.ASuite:org.gradle.OkTest:anotherOk',
            ':org.gradle.ASuite:org.gradle.OtherTest:ok'
        )
        result.testPath(':org.gradle.ASuite').onlyRoot().assertStdout(equalTo("suite class loaded\nbefore suite class out\nnon-asci char: ż\nafter suite class out\n"))
        result.testPath(':org.gradle.ASuite').onlyRoot().assertStderr(equalTo("before suite class err\nafter suite class err\n"))

        result.testPath(':org.gradle.ASuite:org.gradle.OkTest').onlyRoot().assertStdout(equalTo("before OkTest class out\nafter OkTest class out\n"))
        result.testPath(':org.gradle.ASuite:org.gradle.OkTest').onlyRoot().assertStderr(equalTo("before OkTest class err\nafter OkTest class err\n"))
        result.testPath(':org.gradle.ASuite:org.gradle.OkTest:ok').onlyRoot().assertStdout(equalTo(""))
        result.testPath(':org.gradle.ASuite:org.gradle.OkTest:ok').onlyRoot().assertStderr(equalTo("This is test stderr\n"))
        result.testPath(':org.gradle.ASuite:org.gradle.OkTest:anotherOk').onlyRoot().assertStdout(equalTo("sys out from another test method\n"))
        result.testPath(':org.gradle.ASuite:org.gradle.OkTest:anotherOk').onlyRoot().assertStderr(equalTo("sys err from another test method\n"))

        result.testPath(':org.gradle.ASuite:org.gradle.OtherTest').onlyRoot().assertStdout(equalTo("before OtherTest class out\nafter OtherTest class out\n"))
        result.testPath(':org.gradle.ASuite:org.gradle.OtherTest').onlyRoot().assertStderr(equalTo("before OtherTest class err\nafter OtherTest class err\n"))
        result.testPath(':org.gradle.ASuite:org.gradle.OtherTest:ok').onlyRoot().assertStdout(equalTo("This is other stdout\n"))
        result.testPath(':org.gradle.ASuite:org.gradle.OtherTest:ok').onlyRoot().assertStderr(equalTo(""))
    }

    def "supports JUnit3 suites"() {
        given:
        file('src/test/java/org/gradle/SomeSuite.java') << """
            package org.gradle;

            import junit.extensions.TestSetup;
            import junit.framework.Test;
            import junit.framework.TestCase;
            import junit.framework.TestSuite;

            public class SomeSuite extends TestCase {

                public static Test suite() {
                    final TestSuite suite = new TestSuite();
                    suite.addTestSuite(SomeTest1.class);
                    suite.addTestSuite(SomeTest2.class);
                    TestSetup wrappedSuite = new junit.extensions.TestSetup(suite) {

                        protected void setUp() {
                            System.out.println("stdout in TestSetup#setup");
                            System.err.println("stderr in TestSetup#setup");
                        }

                        protected void tearDown() {
                            System.out.println("stdout in TestSetup#teardown");
                            System.err.println("stderr in TestSetup#teardown");
                        }
                    };

                    return wrappedSuite;
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest1.java') << """
            package org.gradle;

            import junit.framework.TestCase;

            public class SomeTest1 extends TestCase {
                public void testOk1(){
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest2.java') << """
            package org.gradle;

            import junit.framework.TestCase;

            public class SomeTest2 extends TestCase {
                public void testOk2(){
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkJUnit3Dependencies}
            }
            test {
                ${configureTestFramework}
                include '**/*Suite.class'
                exclude '**/*Test.class'
            }
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        GenericTestExecutionResult result = resultsFor()
        result.assertTestPathsExecuted(
            ':org.gradle.SomeSuite:org.gradle.SomeTest1:testOk1',
            ':org.gradle.SomeSuite:org.gradle.SomeTest2:testOk2'
        )
        if (supportsSuiteOutput()) {
            result.testPath(":org.gradle.SomeSuite").onlyRoot().assertStdout(containsString("stdout in TestSetup#setup"))
            result.testPath(":org.gradle.SomeSuite").onlyRoot().assertStderr(containsString("stderr in TestSetup#setup"))
            // Due to the way JUnit 3 suites work, we cannot associate the output correctly, even in recent JUnit 4 and Vintage.
            result.testPath("org.gradle.SomeSuite:org.gradle.SomeTest2").onlyRoot().assertStdout(containsString("stdout in TestSetup#teardown"))
            result.testPath("org.gradle.SomeSuite:org.gradle.SomeTest2").onlyRoot().assertStderr(containsString("stderr in TestSetup#teardown"))
        }
    }

    def "supports JUnit3 suites run by JUnit 4 suites"() {
        given:
        file('src/test/java/org/gradle/JUnit3Test.java') << """
            package org.gradle;

            import junit.framework.TestCase;
            import junit.framework.TestSuite;
            import org.junit.runner.RunWith;
            import org.junit.runners.AllTests;

            @RunWith(AllTests.class)
            public class JUnit3Test extends TestCase {

                public JUnit3Test(String name) {
                    super(name);
                }

                public static TestSuite suite()
                {
                    var suite = new TestSuite("JUnit 3 style test suite");
                    suite.addTest(new JUnit3Test("JUnit 3 style test") {
                        @Override
                        protected void runTest() {
                            testSomeLibraryMethodReturnsTrue();
                        }
                    });
                    return suite;
                }

                public void testSomeLibraryMethodReturnsTrue() {
                    System.out.println("In JUnit 3 style test");
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/JUnit4Suite.java') << """
            package org.gradle;

            import org.junit.runner.RunWith;
            import org.junit.runners.Suite;

            @RunWith(Suite.class)
            @Suite.SuiteClasses(JUnit3Test.class)
            public class JUnit4Suite {
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                include '**/*Suite.class'
                exclude '**/*Test.class'
            }
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        GenericTestExecutionResult result = resultsFor()
        result.assertTestPathsExecuted(
            ':org.gradle.JUnit4Suite:JUnit 3 style test suite:JUnit 3 style test'
        )
        result.testPath(':org.gradle.JUnit4Suite:JUnit 3 style test suite:JUnit 3 style test').onlyRoot()
            .assertDisplayName(equalTo("JUnit 3 style test"))
            .assertStdout(containsString("In JUnit 3 style test"))
    }

    def "can run multiple parameterized tests via suite only"() {
        setupManyParameterizedTests()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                include '**/*Suite.class'
            }
        """.stripIndent()

        when:
        succeeds("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        List<String> expectedDuplicatesInSuite = expectedManyParameterizedTests().collect { path ->
            ":AllParamTestsSuite${path}".toString()
        }
        testResult.assertTestPathsExecuted(expectedDuplicatesInSuite as String[])
    }

    def "can run multiple parameterized tests via tests only"() {
        setupManyParameterizedTests()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                include '**/*Foo.class'
                include '**/*Bar.class'
            }
        """.stripIndent()

        when:
        succeeds("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        testResult.assertTestPathsExecuted(expectedManyParameterizedTests() as String[])
    }

    def "can run multiple parameterized tests via tests and suite in one execution"() {
        setupManyParameterizedTests()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
            }
        """.stripIndent()

        when:
        succeeds("test")

        then:
        GenericTestExecutionResult testResult = resultsFor("tests/test", testFramework)
        List<String> expectedDuplicatesInSuite = expectedManyParameterizedTests().collect { path ->
            ":AllParamTestsSuite${path}".toString()
        }
        testResult.assertTestPathsExecuted((expectedDuplicatesInSuite + expectedManyParameterizedTests()) as String[])
    }

    private setupManyParameterizedTests() {
        addParameterizedClass("Foo")
        addParameterizedClass("Bar")
        file("src/test/java/AllParamTestsSuite.java") << """
            ${testFrameworkImports}
            import org.junit.runners.Suite;
            import org.junit.runners.Suite.SuiteClasses;
            @RunWith(Suite.class)
            @SuiteClasses({ParameterizedFoo.class, ParameterizedBar.class})
            public class AllParamTestsSuite {
            }
        """
    }

    void addParameterizedClass(String suffix) {
        file("src/test/java/Parameterized${suffix}.java") << """
            ${testFrameworkImports}
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;
            import org.junit.runner.RunWith;
            import java.util.Collection;
            import java.util.List;

            @RunWith(Parameterized.class)
            public class Parameterized${suffix} {
                int index;
                public Parameterized${suffix}(int index) {
                    this.index = index;
                }

                @Parameters
                public static Collection data() {
                   return List.of(new Object[][] { {0}, {1} });
                }

                @Test public void pass() {}
                @Test public void fail() {}
            }
        """
    }

    private static expectedManyParameterizedTests() {
        ["ParameterizedFoo", "ParameterizedBar"].collectMany { className ->
            (0..1).collectMany { index ->
                [
                    ":${className}:[${index}]:pass[${index}]".toString(),
                    ":${className}:[${index}]:fail[${index}]".toString()
                ]
            }
        }
    }
}
