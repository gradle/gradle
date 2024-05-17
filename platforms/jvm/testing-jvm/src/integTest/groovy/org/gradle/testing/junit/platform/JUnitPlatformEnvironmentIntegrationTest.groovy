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

package org.gradle.testing.junit.platform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_PLATFORM_VERSION
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
    private static final JUNIT_JUPITER_VERSION = '5.6.3'
    private static final JUNIT_PLATFORM_VERSION = '1.6.3'
    private static final OPENTEST4J_VERSION = '1.2.0'
    private static final API_GUARDIAN_VERSION = '1.1.0'

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
                testLogging.showStandardStreams = true
            }
        """
    }

    def "should prompt user to add dependencies when they are not in test runtime classpath"() {
        given:
        buildFile << """
            testing.suites.test.dependencies {
                compileOnly 'org.junit.jupiter:junit-jupiter:${JUNIT_JUPITER_VERSION}'
                runtimeOnly 'org.junit.platform:junit-platform-launcher:${LATEST_PLATFORM_VERSION}'
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

    // When running embedded with test distribution, the remote distribution has a newer version of
    // junit-platform-launcher which is not compatible with the junit jupiter jars we test against.
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "automatically loads framework dependencies from distribution"() {
        given:
        buildFile << """
            testing.suites.test.dependencies {
                implementation 'org.junit.jupiter:junit-jupiter:${JUNIT_JUPITER_VERSION}'
            }
        """

        addClasspathTest("""
            Set<String> jarSet = new HashSet<>(Arrays.asList(
                "gradle-worker.jar",
                "test",
                "junit-jupiter-params-${JUNIT_JUPITER_VERSION}.jar",
                "junit-jupiter-engine-${JUNIT_JUPITER_VERSION}.jar",
                "junit-jupiter-api-${JUNIT_JUPITER_VERSION}.jar",
                "junit-platform-engine-${JUNIT_PLATFORM_VERSION}.jar",
                "junit-platform-commons-${JUNIT_PLATFORM_VERSION}.jar",
                "junit-jupiter-${JUNIT_JUPITER_VERSION}.jar",
                "opentest4j-${OPENTEST4J_VERSION}.jar",
                "apiguardian-api-${API_GUARDIAN_VERSION}.jar"
            ));
            assertTrue(jars.containsAll(jarSet));
            jars.removeAll(jarSet);

            // The distribtuion-loaded jars can vary in version, since the GE test acceleration
            // plugins inject their own versions of these libraries during integration tests.
            // See: https://github.com/gradle/gradle/pull/21494
            assertEquals(jars.size(), 3);
            assertTrue(jars.removeIf(it -> it.startsWith("junit-platform-launcher-1.")));
            assertTrue(jars.removeIf(it -> it.startsWith("junit-platform-commons-1.")));
            assertTrue(jars.removeIf(it -> it.startsWith("junit-platform-engine-1.")));
            assertEquals(jars.size(), 0);
        """)

        expect:
        executer.expectDocumentedDeprecationWarning("The automatic loading of test framework implementation dependencies has been deprecated. This is scheduled to be removed in Gradle 9.0. Declare the desired test framework directly on the test suite or explicitly declare the test framework implementation dependencies on the test's runtime classpath. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_framework_implementation_dependencies")
        succeeds "test"
    }

    def "does not load framework dependencies from distribution when they are on the classpath already"() {
        given:
        buildFile << """
            testing.suites.test.dependencies {
                implementation 'org.junit.jupiter:junit-jupiter:${JUNIT_JUPITER_VERSION}'

                runtimeOnly 'org.junit.platform:junit-platform-launcher'
            }
        """

        addClasspathTest("""
            assertTrue(new HashSet<>(jars).equals(new HashSet<>(Arrays.asList(
                "gradle-worker.jar",
                "test",
                "junit-jupiter-params-${JUNIT_JUPITER_VERSION}.jar",
                "junit-jupiter-engine-${JUNIT_JUPITER_VERSION}.jar",
                "junit-jupiter-api-${JUNIT_JUPITER_VERSION}.jar",
                "junit-platform-launcher-${JUNIT_PLATFORM_VERSION}.jar",
                "junit-platform-engine-${JUNIT_PLATFORM_VERSION}.jar",
                "junit-platform-commons-${JUNIT_PLATFORM_VERSION}.jar",
                "junit-jupiter-${JUNIT_JUPITER_VERSION}.jar",
                "opentest4j-${OPENTEST4J_VERSION}.jar",
                "apiguardian-api-${API_GUARDIAN_VERSION}.jar"
            ))));
        """)

        expect:
        succeeds "test"

    }

    def "does not load framework dependencies even if they are on the classpath but have nonstandard-named jars"() {
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
            assertTrue(new HashSet<>(jars).equals(new HashSet<>(Arrays.asList(
                "gradle-worker.jar",
                "test",
                "renamed-junit-jupiter-api-${JUNIT_JUPITER_VERSION}.jar",
                "renamed-junit-platform-launcher-${JUNIT_PLATFORM_VERSION}.jar",
                "renamed-junit-jupiter-engine-${JUNIT_JUPITER_VERSION}.jar",
                "renamed-junit-platform-commons-${JUNIT_PLATFORM_VERSION}.jar",
                "renamed-junit-jupiter-${JUNIT_JUPITER_VERSION}.jar",
                "renamed-junit-platform-engine-${JUNIT_PLATFORM_VERSION}.jar",
                "renamed-junit-jupiter-params-${JUNIT_JUPITER_VERSION}.jar",
                "opentest4j-${OPENTEST4J_VERSION}.jar",
                "apiguardian-api-${API_GUARDIAN_VERSION}.jar"
            ))));
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
            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.HashSet;
            import java.util.List;
            import java.util.Set;
            import java.util.regex.Pattern;
            import java.util.stream.Collectors;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertTrue;

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
                        classpath = Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
                            .map(path -> new File(path).getName())
                            .collect(Collectors.toList());
                    } else {
                        classpath = Arrays.stream(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs())
                            .map(url -> new File(url.getPath()).getName())
                            .collect(Collectors.toList());
                    }

                    try {
                        // Any remaining jars should be verified by the individual test.
                        List<String> jars = new ArrayList<>(classpath);
                        ${testCode}
                    } catch (AssertionError e) {
                        System.err.println(e.getMessage() + "\\nActual Jars:\\n- " + String.join("\\n- ", classpath));
                        throw e;
                    }
                }
            }
        """
    }

}
