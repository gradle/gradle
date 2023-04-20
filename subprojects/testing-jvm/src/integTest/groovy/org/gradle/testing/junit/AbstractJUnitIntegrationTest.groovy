/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testing.junit

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.AbstractJUnitMultiVersionIntegrationTest
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.matchesRegexp
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

// TODO Make another pass through this class and see what can be extracted into function-specific classes
// Ideally, this class either goes away or is reduced to only a few test cases.
abstract class AbstractJUnitIntegrationTest extends AbstractJUnitMultiVersionIntegrationTest {
    def setup() {
        executer.noExtraLogging()
    }

    abstract String getJUnitVersionAssertion()
    abstract String getTestFrameworkSuiteDependencies()
    abstract String getTestFrameworkSuiteImports()
    abstract String getTestFrameworkSuiteAnnotations(String classes)
    abstract String getAssertionError()
    abstract TestClassExecutionResult assertFailedToExecute(TestExecutionResult testResult, String testClassName)

    def "executes tests in the correct environment"() {
        given:
        file('src/test/java/org/gradle/OkTest.java') << """
            package org.gradle;

            import java.io.File;
            import java.io.PrintStream;
            import java.net.MalformedURLException;
            import java.net.URL;
            import java.net.URLClassLoader;
            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.List;
            import java.util.logging.Logger;
            import java.util.regex.Pattern;

            ${testFrameworkImports}

            public class OkTest {
                @Test
                public void ok() throws Exception {
                    // check versions of dependencies
                    ${JUnitVersionAssertion}
                    assertTrue(org.apache.tools.ant.Main.getAntVersion().contains("1.6.1"));

                    // check working dir
                    assertEquals(System.getProperty("projectDir"), System.getProperty("user.dir"));

                    // check sys properties
                    assertEquals("value", System.getProperty("testSysProperty"));

                    // check env vars
                    assertEquals("value", System.getenv("TEST_ENV_VAR"));

                    // check classloader and classpath
                    assertSame(ClassLoader.getSystemClassLoader(), getClass().getClassLoader());
                    assertSame(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
                    boolean isJava9 = Boolean.parseBoolean(System.getProperty("isJava9"));
                    String[] splitTestRuntimeClasspath = splitClasspath(System.getProperty("testRuntimeClasspath"));
                    if (isJava9) {
                        String[] splitCliClasspath = splitClasspath(System.getProperty("java.class.path"));

                        // The worker jar is first on the classpath.
                        String workerJar = splitCliClasspath[0];
                        assertTrue(workerJar.contains("gradle-worker.jar"));

                        // After, we expect the test runtime classpath.
                        String[] filteredCliClasspath = Arrays.copyOfRange(splitCliClasspath, 1, splitCliClasspath.length);
                        assertArrayEquals(splitTestRuntimeClasspath, filteredCliClasspath);
                    } else {
                        List<URL> systemClasspath = Arrays.asList(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs());

                        // The worker jar is first on the classpath.
                        String workerJar = systemClasspath.get(0).getPath();
                        assertTrue(workerJar.endsWith("gradle-worker.jar"));

                        // After, we expect the test runtime classpath.
                        List<URL> filteredSystemClasspath = systemClasspath.subList(1, systemClasspath.size());
                        List<URL> testRuntimeClasspath = getTestRuntimeClasspath(splitTestRuntimeClasspath);
                        assertEquals(testRuntimeClasspath, filteredSystemClasspath);
                    }

                    // check Gradle and impl classes not visible
                    try {
                        getClass().getClassLoader().loadClass("org.gradle.api.Project");
                        fail();
                    } catch (ClassNotFoundException e) {
                    }
                    try {
                        getClass().getClassLoader().loadClass("org.slf4j.Logger");
                        fail();
                    } catch (ClassNotFoundException e) {
                    }

                    // check other environmental stuff
                    assertEquals("Test worker", Thread.currentThread().getName());
                    assertNull(System.console());

                    final PrintStream out = System.out;
                    // logging from a shutdown hook
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        @Override
                        public void run() {
                            out.println("stdout from a shutdown hook.");
                            Logger.getLogger("test-logger").info("info from a shutdown hook.");
                        }
                    });
                }

                public List<URL> getTestRuntimeClasspath(String[] splitTestRuntimeClasspath) throws MalformedURLException {
                    List<URL> urls = new ArrayList<URL>();
                    for (String path : splitTestRuntimeClasspath) {
                        urls.add(new File(path).toURI().toURL());
                    }
                    return urls;
                }

                private String[] splitClasspath(String classpath) {
                    return classpath.split(Pattern.quote(File.pathSeparator));
                }

                @Test
                public void anotherOk() {
                }
            }
        """.stripIndent()

        file('src/test/java/org/gradle/OtherTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class OtherTest {
                @Test
                public void ok() throws Exception {
                }
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                testImplementation 'ant:ant:1.6.1', 'ant:ant-launcher:1.6.1'
            }
            test {
                ${configureTestFramework}
                systemProperties.isJava9 = ${JavaVersion.current().isJava9Compatible()}
                systemProperties.testSysProperty = 'value'
                systemProperties.projectDir = projectDir
                jvmArgumentProviders.add(new TestClassPathProvider(testClasspath: sourceSets.test.runtimeClasspath))
                environment.TEST_ENV_VAR = 'value'
            }

            class TestClassPathProvider implements CommandLineArgumentProvider {
                @Classpath
                FileCollection testClasspath

                @Override
                List<String> asArguments() {
                    FileCollection filteredTestClasspath = testClasspath.filter { f -> f.exists() || ("*".equals(f.getName()) && f.getParentFile() != null && f.getParentFile().exists()) }
                    ["-DtestRuntimeClasspath=\${filteredTestClasspath.asPath}".toString()]
                }
            }
        """.stripIndent()

        when:
        executer.withTasks('build').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.OkTest', 'org.gradle.OtherTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('ok')
        result.testClass('org.gradle.OtherTest').assertTestPassed('ok')
    }

    def "test classes can be shared by multiple suites"() {
        given:
        file('src/test/java/org/gradle/SomeTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class SomeTest {
                @Test
                public void ok() throws Exception {
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTestSuite.java') << """
            package org.gradle;

            ${testFrameworkSuiteImports}

            ${getTestFrameworkSuiteAnnotations("SomeTest.class")}
            public class SomeTestSuite {
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeOtherTestSuite.java') << """
            package org.gradle;

            ${testFrameworkSuiteImports}

            ${getTestFrameworkSuiteAnnotations("SomeTest.class")}
            public class SomeOtherTestSuite {
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                ${testFrameworkSuiteDependencies}
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
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass("org.gradle.SomeTest").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.SomeTest").assertTestsExecuted("ok", "ok")
    }

    def "can run single tests"() {
        given:
        file('src/test/java/NotATest.java') << """
            public class NotATest {
            }
        """.stripIndent()
        file('src/test/java/Ok.java') << """
            ${testFrameworkImports}
            public class Ok {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        file('src/test/java/Ok2.java') << """
            ${testFrameworkImports}
            public class Ok2 {
                @Test
                public void ok() {
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        succeeds("test", "--tests=Ok2*")

        then:
        def testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted('Ok2')

        when:
        succeeds("cleanTest", "test", "--tests=Ok*")

        then:
        testResult.assertTestClassesExecuted('Ok', 'Ok2')

        when:
        fails("test", "--tests=DoesNotMatchAClass*")

        then:
        result.assertHasCause('No tests found for given includes: [DoesNotMatchAClass*](--tests filter)')

        when:
        fails("test", "--tests=NotATest*")
        then:
        result.assertHasCause('No tests found for given includes: [NotATest*](--tests filter)')
    }

    def "can use test super classes from another project"() {
        given:
        file('settings.gradle').write("include 'a', 'b'")
        file('b/build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${getTestFrameworkDependencies('main')}
            }
            test.${configureTestFramework}
        """.stripIndent()
        file('b/src/main/java/org/gradle/AbstractTest.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public abstract class AbstractTest {
                @Test public void ok() { }
            }
        """.stripIndent()
        file('a/build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
                testImplementation project(':b')
            }
            test.${configureTestFramework}
        """.stripIndent()
        file('a/src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        '''.stripIndent()

        when:
        executer.withTasks('a:test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory.file('a'))
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    def "can exclude super classes from execution"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                exclude '**/BaseTest.*'
            }
        """.stripIndent()
        file('src/test/java/org/gradle/BaseTest.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class BaseTest {
                @Test public void ok() { }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends BaseTest {
            }
        '''.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    def "runs all tests in the same forked jvm"() {
        given:
        file('src/test/java/org/gradle/AbstractTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public abstract class AbstractTest {
                @Test public void ok() {
                    long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
                    System.out.println(String.format(\"VM START TIME = %s\", time));
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest.java') << """
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest2.java') << """
            package org.gradle;
            public class SomeTest2 extends AbstractTest {
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
        TestFile results1 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest.xml')
        TestFile results2 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest2.xml')
        results1.assertIsFile()
        results2.assertIsFile()
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), equalTo(results2.linesThat(containsString('VM START TIME =')).get(0)))
    }

    def "can specify maximum number of test classes to execute in a forked jvm"() {
        given:
        file('src/test/java/org/gradle/AbstractTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public abstract class AbstractTest {
                @Test public void ok() {
                    long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
                    System.out.println(String.format(\"VM START TIME = %s\", time));
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest.java') << """
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        """.stripIndent()
        file('src/test/java/org/gradle/SomeTest2.java') << """
            package org.gradle;
            public class SomeTest2 extends AbstractTest {
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
                forkEvery = 1
            }
        """.stripIndent()

        when:
        executer.withTasks('test').run()

        then:
        TestFile results1 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest.xml')
        TestFile results2 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest2.xml')
        results1.assertIsFile()
        results2.assertIsFile()
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), not(equalTo(results2.linesThat(
            containsString('VM START TIME =')).get(0))))
    }

    def "can listen for test results"() {
        given:
        file('src/main/java/AppException.java') << """
            public class AppException extends Exception { }
        """.stripIndent()
        file('src/test/java/SomeTest.java') << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void failing() { fail(\"message\"); }
                @Test public void knownError() { throw new RuntimeException(\"message\"); }
                @Test public void unknownError() throws AppException { throw new AppException(); }
            }
        """.stripIndent()
        file('src/test/java/SomeOtherTest.java') << """
            ${testFrameworkImports}

            public class SomeOtherTest {
                @Test public void pass() { }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }

            def listener = new TestListenerImpl()
            test {
                ${configureTestFramework}
                addTestListener(listener)
                ignoreFailures = true
            }

            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.resultType] [\$result.testCount] [\$result.exception]" }
            }
        """.stripIndent()

        when:
        def result = executer.withTasks("test").run()

        then:
        containsLine(result.getOutput(), "START [Gradle Test Run :test] [Gradle Test Run :test]")
        containsLine(result.getOutput(), "FINISH [Gradle Test Run :test] [Gradle Test Run :test] [FAILURE] [4]")

        containsLine(result.getOutput(), matchesRegexp("START \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\]"))
        containsLine(result.getOutput(), matchesRegexp("FINISH \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\] \\[FAILURE\\] \\[4\\]"))

        containsLine(result.getOutput(), "START [Test class SomeOtherTest] [SomeOtherTest]")
        containsLine(result.getOutput(), "FINISH [Test class SomeOtherTest] [SomeOtherTest] [SUCCESS] [1]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('pass')}(SomeOtherTest)] [${maybeParentheses('pass')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('pass')}(SomeOtherTest)] [${maybeParentheses('pass')}] [SUCCESS] [1] [null]")

        containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
        containsLine(result.getOutput(), "FINISH [Test class SomeTest] [SomeTest] [FAILURE] [3]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('failing')}(SomeTest)] [${maybeParentheses('failing')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('failing')}(SomeTest)] [${maybeParentheses('failing')}] [FAILURE] [1] [${assertionError}: message]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('knownError')}(SomeTest)] [${maybeParentheses('knownError')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('knownError')}(SomeTest)] [${maybeParentheses('knownError')}] [FAILURE] [1] [java.lang.RuntimeException: message]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('unknownError')}(SomeTest)] [${maybeParentheses('unknownError')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('unknownError')}(SomeTest)] [${maybeParentheses('unknownError')}] [FAILURE] [1] [AppException]")

        when:
        testDirectory.file('src/test/java/SomeOtherTest.java').delete()
        result = executer.withTasks("test").run()

        then:
        result.assertNotOutput("SomeOtherTest")
        containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "can have multiple test task instances"() {
        given:
        file('src/test/java/org/gradle/Test1.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class Test1 {
                @Test public void ok() { }
            }
        """.stripIndent()
        file('src/test2/java/org/gradle/Test2.java') << """
            package org.gradle;
            ${testFrameworkImports}
            public class Test2 {
                @Test public void ok() { }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            sourceSets {
                test2
            }

            test.${configureTestFramework}

            task test2(type: Test) {
                ${configureTestFramework}
                classpath = sourceSets.test2.runtimeClasspath
                testClassesDirs = sourceSets.test2.output.classesDirs
            }

            check {
                dependsOn test2
            }

            dependencies {
                ${getTestFrameworkDependencies('test')}
                ${getTestFrameworkDependencies('test2')}
            }
        """.stripIndent()

        when:
        executer.withTasks('check').run()

        then:
        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted('org.gradle.Test1')
        testResult.testClass('org.gradle.Test1').assertTestPassed('ok')

        def test2Result = new JUnitXmlTestExecutionResult(testDirectory, 'build/test-results/test2')
        test2Result.assertTestClassesExecuted('org.gradle.Test2')
        test2Result.testClass('org.gradle.Test2').assertTestPassed('ok')
    }

    def "tries to execute unparseable test classes"() {
        given:
        file('build/classes/java/test/com/example/Foo.class').text = "invalid class file"
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """

        when:
        fails('test', '-x', 'compileTestJava')

        then:
        failureCauseContains("There were failing tests")
        DefaultTestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        assertFailedToExecute(testResult, 'com.example.Foo').assertTestCount(1, 1, 0)
    }

    @Issue("https://github.com/gradle/gradle/issues/23602")
    def "handles unserializable exception thrown from test"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """

        file('src/test/java/PoisonTest.java') << """
            ${testFrameworkImports}

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
        with(new DefaultTestExecutionResult(testDirectory).testClass("PoisonTest")) {
            assertTestPassed("passingTest")
            assertTestFailed("testWithUnserializableException", containsString("TestFailureSerializationException: An exception of type PoisonTest\$UnserializableException was thrown by the test, but Gradle was unable to recreate the exception in the build process"))
            assertTestFailed("normalFailingTest", containsString("AssertionError"))
        }
    }
}
