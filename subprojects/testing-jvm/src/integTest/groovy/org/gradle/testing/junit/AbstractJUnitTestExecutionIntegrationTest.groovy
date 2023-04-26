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

package org.gradle.testing.junit

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.AbstractJUnitMultiVersionIntegrationTest

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

abstract class AbstractJUnitTestExecutionIntegrationTest extends AbstractJUnitMultiVersionIntegrationTest {
    abstract String getJUnitVersionAssertion()
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
}
