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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import spock.lang.Ignore
import spock.lang.Issue

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class TestNGIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.noExtraLogging()
    }

    def "executes tests in correct environment"() {
        given:
        buildFile << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'org.testng:testng:6.3.1' }
            test {
                useTestNG()
                systemProperties.testSysProperty = 'value'
                systemProperties.testDir = projectDir
                environment.TEST_ENV_VAR = 'value'
            }
        '''.stripIndent()
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
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'org.testng:testng:6.3.1' }
            
            test {
                useTestNG()
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
        def result = succeeds 'test'

        then:
        result.output.contains "START [Gradle Test Run :test] [Gradle Test Run :test]\n"
        result.output.contains "FINISH [Gradle Test Run :test] [Gradle Test Run :test]\n"
        result.output.contains "START [Gradle Test Executor 1] [Gradle Test Executor 1]\n"
        result.output.contains "FINISH [Gradle Test Executor 1] [Gradle Test Executor 1]\n"
        result.output.contains "START [Test suite 'Gradle suite'] [Gradle suite]\n"
        result.output.contains "FINISH [Test suite 'Gradle suite'] [Gradle suite]\n"
        result.output.contains "START [Test suite 'Gradle test'] [Gradle test]\n"
        result.output.contains "FINISH [Test suite 'Gradle test'] [Gradle test]\n"
        result.output.contains "START [Test method pass(SomeTest)] [pass]\n"
        result.output.contains "FINISH [Test method pass(SomeTest)] [pass] [null]\n"
        result.output.contains "START [Test method fail(SomeTest)] [fail]\n"
        result.output.contains "FINISH [Test method fail(SomeTest)] [fail] [java.lang.AssertionError]\n"
        result.output.contains "START [Test method knownError(SomeTest)] [knownError]\n"
        result.output.contains "FINISH [Test method knownError(SomeTest)] [knownError] [java.lang.RuntimeException: message]\n"
        result.output.contains "START [Test method unknownError(SomeTest)] [unknownError]\n"
        result.output.contains "FINISH [Test method unknownError(SomeTest)] [unknownError] [AppException]\n"
    }

    @Issue("GRADLE-1532")
    def "supports thread pool size"() {
        given:
        buildFile << '''
            apply plugin: "java"
            repositories { mavenCentral() }
            dependencies { testCompile 'org.testng:testng:6.3.1' }
            
            test {
                useTestNG()
            }
        '''.stripIndent()
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
        buildFile << '''
            apply plugin: "java"
            repositories { mavenCentral() }
            dependencies { testCompile "org.testng:testng:6.3.1" }
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
        '''.stripIndent()
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
        buildFile << '''
            apply plugin: "java"
            repositories { mavenCentral() }
            dependencies { compile "org.testng:testng:6.3.1" }
            test {
                useTestNG()
            }
        '''.stripIndent()
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
        buildFile << '''
            apply plugin: "java"
            repositories { mavenCentral() }
            dependencies { testCompile 'org.testng:testng:6.3.1' }
            
            test {
                useTestNG()
            }
        '''.stripIndent()
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
