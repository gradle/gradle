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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.JUnitCoverage

import static org.hamcrest.CoreMatchers.containsString

/**
 * Tests the state of the application classpath in the forked test process to ensure the correct
 * test framework dependencies are exposed to the user's test code. Additionally tests environmental
 * state like system properties, and environment variables.
 *
 * <p>This test intentionally does not extend {@link JUnitPlatformIntegrationSpec} in order to have
 * complete control over the configuration of the test setup</p>
 */
class JUnitPlatformEnvironmentIntegrationTest extends AbstractIntegrationSpec {

    // The versions tested against here are intentionally different than the version of junit-platform-launcher
    // that Gradle will load from the distribution. This way, we can use the version on the application classpath
    // to determine whether the launcher was loaded from the distribution or from the test runtime classpath.
    // The version which Gradle loads from the distribution should be the same as `JUnitCoverage#LATEST_PLATFORM_VERSION`
    private static final JUNIT_JUPITER_VERSION = '5.8.1'
    private static final JUNIT_PLATFORM_VERSION = '1.8.1'
    private static final OPENTEST4J_VERSION = '1.2.0'

    def setup() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            test {
                useJUnitPlatform()

                systemProperties.isJava9 = "\${JavaVersion.current().isJava9Compatible()}"
                systemProperties.testSysProperty = 'value'
                systemProperties.projectDir = projectDir
                environment.TEST_ENV_VAR = 'value'
            }
        """
    }

    def "should prompt user to add dependencies when they are not in test runtime classpath"() {
        given:
        buildFile << """
            testing.suites.test.dependencies {
                compileOnly 'org.junit.jupiter:junit-jupiter:${JUNIT_JUPITER_VERSION}'
            }
        """
        file('src/test/java/org/example/ExampleTest.java') << """
            package org.example;
            public class ExampleTest {
                @org.junit.jupiter.api.Test
                public void ok() { }
            }
        """

        when:
        fails('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClassStartsWith('Gradle Test Executor')
            .assertExecutionFailedWithCause(containsString('consider adding an engine implementation JAR to the classpath'))
    }

    def "automatically loads junit-platform-launcher from distribution"() {
        given:
        buildFile << """
            testing.suites.test.dependencies {
                implementation 'org.junit.jupiter:junit-jupiter:${JUNIT_JUPITER_VERSION}'
            }
        """

        addClasspathTest("""
            assert jars.get(0).endsWith("junit-jupiter-params-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(1).endsWith("junit-jupiter-engine-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(2).endsWith("junit-jupiter-api-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(3).endsWith("junit-platform-engine-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(4).endsWith("junit-platform-commons-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(5).endsWith("junit-jupiter-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(6).endsWith("opentest4j-${OPENTEST4J_VERSION}.jar");

            // And then the distribution-loaded launcher
            assert jars.get(7).endsWith("junit-platform-launcher-${JUnitCoverage.LATEST_PLATFORM_VERSION}.jar");
            assert jars.size() == 8;
        """)

        expect:
        succeeds "test"
    }

    def "does not load junit-platform-launcher from distribution when it is on the classpath already"() {
        given:
        buildFile << """
            testing.suites.test.dependencies {
                implementation 'org.junit.jupiter:junit-jupiter:${JUNIT_JUPITER_VERSION}'

                runtimeOnly 'org.junit.platform:junit-platform-launcher'
            }
        """

        addClasspathTest("""
            assert jars.get(0).endsWith("junit-jupiter-params-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(1).endsWith("junit-jupiter-engine-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(2).endsWith("junit-jupiter-api-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(3).endsWith("junit-platform-launcher-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(4).endsWith("junit-platform-engine-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(5).endsWith("junit-platform-commons-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(6).endsWith("junit-jupiter-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(7).endsWith("opentest4j-${OPENTEST4J_VERSION}.jar");
            assert jars.size() == 8;
        """)

        expect:
        succeeds "test"

    }

    def "does not load junit-platform-launcher even if it is on the classpath but has a nonstandard-named jar"() {
        given:
        buildFile << """
            testing.suites.test.dependencies {
                implementation 'org.junit.jupiter:junit-jupiter:${JUNIT_JUPITER_VERSION}'

                runtimeOnly 'org.junit.platform:junit-platform-launcher'
            }

            task renameJUnitJars(type: Copy) {
                from configurations.testRuntimeClasspath
                into file('build/renamed-classpath')
                rename { String fileName ->
                    if (fileName.startsWith('junit')) {
                        return fileName.replace('junit', 'renamed-junit')
                    }
                }
            }

            testing.suites.test.sources.runtimeClasspath =
                testing.suites.test.sources.output.plus(
                    renameJUnitJars.outputs.files.asFileTree.matching {
                        include '**/*.jar'
                    }
                )
        """

        addClasspathTest("""
            assert jars.get(0).endsWith("renamed-junit-jupiter-api-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(1).endsWith("renamed-junit-platform-launcher-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(2).endsWith("renamed-junit-jupiter-engine-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(3).endsWith("renamed-junit-platform-commons-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(4).endsWith("renamed-junit-jupiter-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.get(5).endsWith("renamed-junit-platform-engine-${JUNIT_PLATFORM_VERSION}.jar");
            assert jars.get(6).endsWith("opentest4j-${OPENTEST4J_VERSION}.jar");
            assert jars.get(7).endsWith("renamed-junit-jupiter-params-${JUNIT_JUPITER_VERSION}.jar");
            assert jars.size() == 8;
        """)

        expect:
        succeeds "test"
    }

    def addClasspathTest(String testCode) {
        file("src/test/java/org/example/ClasspathCheckingTest.java") << """
            package org.example;

            import java.io.File;
            import java.net.URL;
            import java.net.URLClassLoader;
            import java.util.Arrays;
            import java.util.List;
            import java.util.regex.Pattern;
            import java.util.stream.Collectors;

            public class ClasspathCheckingTest {
                @org.junit.jupiter.api.Test
                public void checkEnvironment() {
                    assert System.getProperty("projectDir").equals(System.getProperty("user.dir"));
                    assert "value".equals(System.getProperty("testSysProperty"));
                    assert "value".equals(System.getenv("TEST_ENV_VAR"));

                    assert ClassLoader.getSystemClassLoader() == getClass().getClassLoader();
                    assert getClass().getClassLoader() == Thread.currentThread().getContextClassLoader();

                    boolean isJava9 = Boolean.parseBoolean(System.getProperty("isJava9"));

                    List<String> classpath;
                    if (isJava9) {
                        classpath = Arrays.asList(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)));
                    } else {
                        classpath = Arrays.stream(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs())
                            .map(URL::getPath)
                            .collect(Collectors.toList());
                    }

                    try {
                        // The worker jar is first on the classpath.
                        String workerJar = classpath.get(0);
                        assert workerJar.endsWith("gradle-worker.jar");

                        // After, we expect the test runtime classpath.
                        String testClasses = classpath.get(1);
                        assert testClasses.endsWith("test") || testClasses.endsWith("test/");

                        // Any remaining jars should be verified by the individual test.
                        List<String> jars = classpath.subList(2, classpath.size());

                        ${testCode}
                    } catch (AssertionError e) {
                        String output = "Expectation failed. Actual Jars:\\n" + String.join("\\n", classpath);
                        throw new RuntimeException(output, e);
                    }
                }
            }
        """
    }

}
