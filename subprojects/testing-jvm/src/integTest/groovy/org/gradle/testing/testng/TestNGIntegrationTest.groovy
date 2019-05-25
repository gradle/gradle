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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Ignore
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

@TargetCoverage({ TestNGCoverage.STANDARD_COVERAGE_WITH_INITIAL_ICLASS_LISTENER })
class TestNGIntegrationTest extends MultiVersionIntegrationSpec {

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
        file('src/test/java/org/gradle/OkTest.java') << '''
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
                    assertNull(System.getSecurityManager());
                }
            }
        '''.stripIndent()

        when:
        succeeds 'test'

        then:
        new DefaultTestExecutionResult(testDirectory).testClass('org.gradle.OkTest').assertTestPassed('ok')
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
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.groups.SomeTest')
        result.testClass('org.gradle.groups.SomeTest').assertTestsExecuted("databaseTest")
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
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.factory.FactoryTest')
        result.testClass('org.gradle.factory.FactoryTest').assertTestCount(2, 0, 0)
        result.testClass('org.gradle.factory.FactoryTest').assertStdout(containsString('TestingFirst'))
        result.testClass('org.gradle.factory.FactoryTest').assertStdout(containsString('TestingSecond'))
        result.testClass('org.gradle.factory.FactoryTest').assertStdout(not(containsString('Default test name')))
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
        failureCauseContains("There were failing tests")
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.testClassStartsWith('Gradle Test Executor')
            .assertTestCount(1, 1, 0)
            .assertTestFailed("failed to execute tests", containsString("Could not execute test class 'com.example.Foo'"))
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
                public void runFirst() {}
                
                @Test(dependsOnMethods = "runFirst")
                public void testGet2() {
                    Assert.assertEquals(true, true);
                }
            }
        """

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted('TestNG7878')
            .testClass('TestNG7878').assertTestCount(4, 0, 0)
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
