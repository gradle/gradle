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

package org.gradle.testing.testng

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.MultiJvmTestCompatibility
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Ignore
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

@TargetCoverage({ TestNGCoverage.SUPPORTS_ICLASS_LISTENER })
class TestNGIntegrationTest extends MultiVersionIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.TEST_NG
    }

    private testResults = resultsFor()

    def setup() {
        executer.noExtraLogging()
        TestNGCoverage.enableTestNG(buildFile, version)
    }

    def "executes tests in correct environment"() {
        given:
        buildFile << """
            test {
                systemProperties.testSysProperty = 'value'
                systemProperties.testDir = projectDir
                environment.TEST_ENV_VAR = 'value'
            }
        """.stripIndent()
        file('src/test/java/org/gradle/OkTest.java') << """
            package org.gradle;

            import static org.testng.Assert.*;

            public class OkTest {
                @org.testng.annotations.Test
                public void ok() throws Exception {
                    // check working dir
                    assertEquals(System.getProperty("testDir"), System.getProperty("user.dir"));

                    // check Gradle classes not visible
                    try {
                        getClass().getClassLoader().loadClass("org.gradle.api.Project");
                        fail();
                    } catch (ClassNotFoundException e) {
                        // Expected
                    }

                    // check context classloader
                    assertSame(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());

                    // check sys properties
                    assertEquals("value", System.getProperty("testSysProperty"));

                    // check env vars
                    assertEquals("value", System.getenv("TEST_ENV_VAR"));

                    // check other environmental stuff
                    assertEquals("Test worker", Thread.currentThread().getName());
                    ${MultiJvmTestCompatibility.CONSOLE_CHECK}
                }
            }
        """.stripIndent()

        when:
        succeeds 'test'

        then:
        testResults.testPath(':org.gradle.OkTest:ok').onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    def "can listen for test results"() {
        given:
        buildFile << """
            test {
                ignoreFailures = true
            }

            ${testListener()}
        """.stripIndent()
        file('src/test/java/AppException.java') << 'public class AppException extends Exception {}'
        file('src/test/java/SomeTest.java') << '''
            public class SomeTest {
                @org.testng.annotations.Test
                public void pass() {}

                @org.testng.annotations.Test
                public void fail() { assert false; }

                @org.testng.annotations.Test
                public void knownError() { throw new RuntimeException("message"); }

                @org.testng.annotations.Test
                public void unknownError() throws AppException { throw new AppException(); }
            }
        '''.stripIndent()

        when:
        succeeds 'test'

        then:
        outputContains "START [Gradle Test Run :test] [Gradle Test Run :test]\n"
        outputContains "FINISH [Gradle Test Run :test] [Gradle Test Run :test]\n"
        result.output.readLines().find { it.matches "START \\[Gradle Test Executor \\d+] \\[Gradle Test Executor \\d+]" }
        result.output.readLines().find { it.matches "FINISH \\[Gradle Test Executor \\d+] \\[Gradle Test Executor \\d+]" }
        outputContains "START [Test suite 'Gradle suite'] [Gradle suite]\n"
        outputContains "FINISH [Test suite 'Gradle suite'] [Gradle suite]\n"
        outputContains "START [Test suite 'Gradle test'] [Gradle test]\n"
        outputContains "FINISH [Test suite 'Gradle test'] [Gradle test]\n"
        outputContains "START [Test method pass(SomeTest)] [pass]\n"
        outputContains "FINISH [Test method pass(SomeTest)] [pass] [null]\n"
        outputContains "START [Test method fail(SomeTest)] [fail]\n"
        outputContains "FINISH [Test method fail(SomeTest)] [fail] [java.lang.AssertionError]\n"
        outputContains "START [Test method knownError(SomeTest)] [knownError]\n"
        outputContains "FINISH [Test method knownError(SomeTest)] [knownError] [java.lang.RuntimeException: message]\n"
        outputContains "START [Test method unknownError(SomeTest)] [unknownError]\n"
        outputContains "FINISH [Test method unknownError(SomeTest)] [unknownError] [AppException]\n"
    }

    @Issue("GRADLE-1532")
    def "supports thread pool size"() {
        given:
        file('src/test/java/SomeTest.java') << '''
            import org.testng.Assert;
            import org.testng.annotations.Test;

            public class SomeTest {
                @Test(invocationCount = 2, threadPoolSize = 2)
                public void someTest() { Assert.assertTrue(true); }
            }
        '''.stripIndent()

        expect:
        succeeds 'test'
    }

    def "supports test groups"() {
        buildFile << """
            ext {
                ngIncluded = "database"
                ngExcluded = "slow"
            }
            test {
                useTestNG {
                    includeGroups ngIncluded
                    excludeGroups ngExcluded
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/groups/SomeTest.java') << '''
            package org.gradle.groups;
            import org.testng.annotations.Test;

            public class SomeTest {
                @Test(groups = "web")
                public void webTest() {}

                @Test(groups = "database")
                public void databaseTest() {}

                @Test(groups = {"database", "slow"})
                public void slowDatabaseTest() {}
            }
        '''.stripIndent()

        when:
        succeeds 'test'

        then:
        testResults.assertAtLeastTestPathsExecuted(':org.gradle.groups.SomeTest:databaseTest')
        testResults.testPath(":org.gradle.groups.SomeTest:databaseTest").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    def "does not emit deprecation warning about no tests executed when groups are specified"() {
        buildFile << """
            test {
                useTestNG {
                    ${configureIncludeOrExcludeGroups}
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/groups/SomeTest.java') << '''
            package org.gradle.groups;
            import org.testng.annotations.Test;

            public class SomeTest {
                @Test(groups = "database")
                public void databaseTest() {}

                @Test(groups = {"database", "slow"})
                public void slowDatabaseTest() {}
            }
        '''.stripIndent()

        when:
        succeeds 'test'

        then:
        testResults.assertTestPathsNotExecuted("org.gradle.groups.SomeTest")

        where:
        configureIncludeOrExcludeGroups << ["includeGroups 'notDatabase'", "excludeGroups 'database'"]
    }

    def "supports test factory"() {
        given:
        file('src/test/java/org/gradle/factory/FactoryTest.java') << '''
            package org.gradle.factory;
            import org.testng.annotations.Test;

            public class FactoryTest {
                private final String name;
                public FactoryTest(String name) { this.name = name; }

                @Test
                public void printMethod(){ System.out.println("Testing" + name); }

                // DO NOT DELETE
                // Removing this may cause the test to work on some OSes and fail on others.
                // TestNG orders tests by the toString() of the instance.
                // If this is not overridden, then it will depend on the identity hashcode,
                // which IS STABLE across our test workers, but different on different OSes.
                // See https://bugs.openjdk.org/browse/JDK-8329968 for why the hashcode is stable.
                @Override
                public String toString() {
                    return "FactoryTest[name=" + name + "]";
                }
            }
        '''.stripIndent()
        file('src/test/java/org/gradle/factory/TestNGFactory.java') << '''
            package org.gradle.factory;
            import org.testng.annotations.Factory;

            public class TestNGFactory {
                @Factory
                public Object[] factory() {
                    return new Object[]{ new FactoryTest("First"), new FactoryTest("Second") };
                }
            }
        '''.stripIndent()

        when:
        succeeds 'test'

        then:
        testResults.assertTestPathsExecuted(
            ':org.gradle.factory.FactoryTest:printMethod on FactoryTest[name=First]',
            ':org.gradle.factory.FactoryTest:printMethod on FactoryTest[name=Second]',
        )
        testResults.testPath(':org.gradle.factory.FactoryTest:printMethod on FactoryTest[name=First]').onlyRoot()
            .assertStdout(containsString('TestingFirst'))
            .assertStdout(not(containsString('Default test name')))
        testResults.testPath(':org.gradle.factory.FactoryTest:printMethod on FactoryTest[name=Second]').onlyRoot()
            .assertStdout(containsString('TestingSecond'))
    }

    @Issue("GRADLE-3315")
    @Ignore("Not fixed yet.")
    def "picks up changes"() {
        given:
        file('src/test/java/SomeTest.java') << """
            import org.testng.Assert;
            import org.testng.annotations.Test;

            public class SomeTest {
                @Test(invocationCount = 2, threadPoolSize = 2)
                public void someTest() { Assert.assertTrue(true); }
            }
        """.stripIndent()

        expect:
        succeeds 'test'

        when:
        file('src/test/java/SomeTest.java') << """
            import org.testng.Assert;
            import org.testng.annotations.Test;

            public class SomeTest {
                @Test(invocationCount = 2, threadPoolSize = 2)
                public void someTest() { Assert.assertTrue(false); }
            }
        """.stripIndent()

        and:
        def result = fails 'test'

        then:
        result.assertTestsFailed()
    }

    def "tries to execute unparseable test classes"() {
        given:
        testDirectory.file('build/classes/java/test/com/example/Foo.class').text = "invalid class file"

        when:
        fails('test', '-x', 'compileTestJava')

        then:
        failure.assertHasCause("Test process encountered an unexpected problem.")
        failure.assertHasCause("Could not execute test class 'com.example.Foo'.")

        testResults.testPathPreNormalized(':').onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("Could not execute test class 'com.example.Foo'"))
    }

    @Issue("https://github.com/gradle/gradle/issues/7878")
    def "can concurrently execute the same test class multiple times"() {
        given:
        file('src/test/java/TestNG7878.java') << """
            import org.testng.annotations.Factory;
            import org.testng.annotations.Test;
            import org.testng.Assert;

            public class TestNG7878 {
                @Factory
                public static Object[] createTests() {
                    return new Object[]{
                            new TestNG7878(),
                            new TestNG7878()
                    };
                }

                @Test
                public void runFirst() {
                    System.out.println("runFirst");
                }

                @Test(dependsOnMethods = "runFirst")
                public void testGet2() {
                    System.out.println("testGet2");
                    Assert.assertEquals(true, true);
                }

                // DO NOT DELETE
                // Removing this may cause the test to work on some OSes and fail on others.
                // TestNG orders tests by the toString() of the instance.
                // If this is not overridden, then it will depend on the identity hashcode,
                // which IS STABLE across our test workers, but different on different OSes.
                // See https://bugs.openjdk.org/browse/JDK-8329968 for why the hashcode is stable.
                @Override
                public String toString() {
                    return "TestNG7878";
                }
            }
        """

        when:
        succeeds('test')

        then:
        testResults.assertTestPathsExecuted(
            ':TestNG7878:runFirst on TestNG7878',
            ':TestNG7878:testGet2 on TestNG7878',
        )
        testResults.testPath(':TestNG7878:runFirst on TestNG7878').singleRootWithRun(1)
            .assertHasResult(TestResult.ResultType.SUCCESS)
        testResults.testPath(':TestNG7878:runFirst on TestNG7878').singleRootWithRun(2)
            .assertHasResult(TestResult.ResultType.SUCCESS)
        testResults.testPath(':TestNG7878:testGet2 on TestNG7878').singleRootWithRun(1)
            .assertHasResult(TestResult.ResultType.SUCCESS)
        testResults.testPath(':TestNG7878:testGet2 on TestNG7878').singleRootWithRun(2)
            .assertHasResult(TestResult.ResultType.SUCCESS)
    }

    @Issue("https://github.com/gradle/gradle/issues/23602")
    def "handles unserializable exception thrown from test"() {
        given:
        file('src/test/java/PoisonTest.java') << """
            import org.testng.annotations.Test;

            public class PoisonTest {
                @Test public void passingTest() { }

                @Test public void testWithUnserializableException() {
                    if (true) {
                        throw new UnserializableException();
                    }
                }

                @Test public void normalFailingTest() {
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
        testResults.testPath(":PoisonTest:passingTest").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        testResults.testPath(":PoisonTest:testWithUnserializableException").onlyRoot().assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("TestFailureSerializationException: An exception of type PoisonTest\$UnserializableException was thrown by the test, but Gradle was unable to recreate the exception in the build process"))
        testResults.testPath(":PoisonTest:normalFailingTest").onlyRoot().assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString("AssertionError"))
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2313")
    def "can clean test after extracting class file"() {
        when:
        buildFile << """
            apply plugin: "java"
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:6.3.1'
            }
            test.useTestNG()
        """
        and:
        file("src/test/java/SomeTest.java") << """
            public class SomeTest extends org.testng.Converter {
                @org.testng.annotations.Test
                public void test() {}
            }
        """
        then:
        succeeds "clean", "test"

        and:
        file("build/tmp/test").exists() // ensure we extracted classes
    }

    private static String testListener() {
        return '''
            def listener = new TestListenerImpl()
            test {
                addTestListener(listener)
            }
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [$suite] [$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [$suite] [$suite.name]" }
                void beforeTest(TestDescriptor test) { println "START [$test] [$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [$test] [$test.name] [$result.exception]" }
            }
        '''.stripIndent()
    }
}
