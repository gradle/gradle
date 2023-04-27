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

package org.gradle.testing.junit4

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.testing.junit.AbstractJUnitIntegrationTest
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.CoreMatchers.containsString

abstract class AbstractJUnit4JUnitIntegrationTest extends AbstractJUnitIntegrationTest implements JUnit4CommonTestSources {
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
    String getAssertionError() {
        return "java.lang.AssertionError"
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
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.ASuite', 'org.gradle.OkTest', 'org.gradle.OtherTest')
        result.testClass('org.gradle.ASuite').assertStdout(containsString('suite class loaded'))
        result.testClass('org.gradle.ASuite').assertStdout(containsString('before suite class out'))
        result.testClass('org.gradle.ASuite').assertStdout(containsString('non-asci char: ż'))
        result.testClass('org.gradle.ASuite').assertStderr(containsString('before suite class err'))
        result.testClass('org.gradle.ASuite').assertStdout(containsString('after suite class out'))
        result.testClass('org.gradle.ASuite').assertStderr(containsString('after suite class err'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('This is test stderr'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('sys out from another test method'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('sys err from another test method'))
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('This is other stdout'))
    }

    @Issue("https://issues.gradle.org//browse/GRADLE-3114")
    def "creates runner before tests"() {
        given:
        file('src/test/java/org/gradle/CustomRunner.java') << """
            package org.gradle;

            import java.lang.reflect.Method;
            import org.junit.runner.notification.RunNotifier;
            import org.junit.runners.BlockJUnit4ClassRunner;
            import org.junit.runners.model.FrameworkMethod;
            import org.junit.runners.model.InitializationError;
            import org.junit.runners.model.Statement;

            public class CustomRunner extends BlockJUnit4ClassRunner {
                public static boolean isClassUnderTestLoaded;
                private final Class<?> bootstrappedTestClass;

                public CustomRunner(Class<?> clazz) throws Exception {
                    super(clazz);
                    bootstrappedTestClass = clazz;
                }

                @Override
                protected Statement methodBlock(final FrameworkMethod method) {
                    return new Statement() {
                        @Override
                        public void evaluate() throws Throwable {

                            if(isClassUnderTestLoaded){
                                throw new RuntimeException("Test Class should not be loaded");
                            }

                            final HelperTestRunner helperTestRunner = new HelperTestRunner(bootstrappedTestClass);
                            final Method bootstrappedMethod = bootstrappedTestClass.getMethod(method.getName());
                            final Statement statement = helperTestRunner.methodBlock(new FrameworkMethod(bootstrappedMethod));
                            statement.evaluate();
                        }
                    };
                }

                public class HelperTestRunner extends BlockJUnit4ClassRunner {
                    public HelperTestRunner(Class<?> testClass) throws InitializationError {
                        super(testClass);
                    }

                    @Override
                    protected Object createTest() throws Exception {
                        return super.createTest();
                    }

                    @Override
                    public Statement classBlock(RunNotifier notifier) {
                        return super.classBlock(notifier);
                    }

                    @Override
                    public Statement methodBlock(FrameworkMethod method) {
                        return super.methodBlock(method);
                    }
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/ExecutionOrderTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            ${getRunOrExtendWithAnnotation('CustomRunner.class')}
            public class ExecutionOrderTest {

                static{
                    CustomRunner.isClassUnderTestLoaded = true;
                }

                @Test
                public void classUnderTestIsLoadedOnlyByRunner(){
                    // The CustomRunner class will fail this test if this class is initialized before its
                    // run method is triggered.
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
            }
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.ExecutionOrderTest')
        result.testClass('org.gradle.ExecutionOrderTest').assertTestPassed('classUnderTestIsLoadedOnlyByRunner')
    }

    def "can listen for test results when JUnit3 is used"() {
        given:
        file('src/test/java/SomeTest.java') << """
            public class SomeTest extends junit.framework.TestCase {
                public void testPass() { }
                public void testFail() { junit.framework.Assert.fail(\"message\"); }
                public void testError() { throw new RuntimeException(\"message\"); }
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkJUnit3Dependencies}
            }
            def listener = new TestListenerImpl()
            test {
                ${configureTestFramework}
                addTestListener(listener)
                ignoreFailures = true
            }
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.exception]" }
            }
        """.stripIndent()

        when:
        ExecutionResult result = executer.withTasks("test").run()

        then:
        assert containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
        assert containsLine(result.getOutput(), "FINISH [Test class SomeTest] [SomeTest]")
        assert containsLine(result.getOutput(), "START [Test testPass(SomeTest)] [testPass]")
        assert containsLine(result.getOutput(), "FINISH [Test testPass(SomeTest)] [testPass] [null]")
        assert containsLine(result.getOutput(), "START [Test testFail(SomeTest)] [testFail]")
        assert containsLine(result.getOutput(), "FINISH [Test testFail(SomeTest)] [testFail] [junit.framework.AssertionFailedError: message]")
        assert containsLine(result.getOutput(), "START [Test testError(SomeTest)] [testError]")
        assert containsLine(result.getOutput(), "FINISH [Test testError(SomeTest)] [testError] [java.lang.RuntimeException: message]")
    }

    def "supports Junit3 suites"() {
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
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest1', 'org.gradle.SomeTest2', 'org.gradle.SomeSuite')
        result.testClass("org.gradle.SomeTest1").assertTestCount(1, 0, 0)
        result.testClass("org.gradle.SomeTest1").assertTestsExecuted("testOk1")
        result.testClass("org.gradle.SomeTest2").assertTestCount(1, 0, 0)
        result.testClass("org.gradle.SomeTest2").assertTestsExecuted("testOk2")
        result.testClass("org.gradle.SomeSuite").assertTestCount(0, 0, 0)
        if (supportsSuiteOutput()) {
            result.testClass("org.gradle.SomeSuite").assertStdout(containsString("stdout in TestSetup#setup"))
            result.testClass("org.gradle.SomeSuite").assertStderr(containsString("stderr in TestSetup#setup"))
            // JUnit3 suite teardown output does not seem to get captured with Vintage (even with 5.9.0)
            // TODO need to investigate whether this is a bug in JUnit or in Gradle testing or what
            //result.testClass("org.gradle.SomeSuite").assertStdout(containsString("stdout in TestSetup#teardown"))
            //result.testClass("org.gradle.SomeSuite").assertStderr(containsString("stderr in TestSetup#teardown"))
        }
    }
}
