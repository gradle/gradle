/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.gradle.util.Matchers
import org.junit.Assume

abstract class AbstractTestEnvironmentIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    abstract String getModuleName()
    abstract boolean isFrameworkSupportsModularJava()

    def setup() {
        buildFile << """
            allprojects {
                apply plugin: 'java'

                repositories {
                    mavenCentral()
                }

                dependencies {
                    ${testFrameworkDependencies}
                }

                test.${configureTestFramework}
            }
        """.stripIndent()
    }

    def "can run tests with custom system classloader"() {
        given:
        file('src/test/java/org/gradle/MySystemClassLoader.java') << customSystemClassLoaderClass
        file('src/test/java/org/gradle/JUnitTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class JUnitTest {
                @Test
                public void mySystemClassLoaderIsUsed() throws ClassNotFoundException {
                    assertTrue(ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader);
                    assertEquals(getClass().getClassLoader(), ClassLoader.getSystemClassLoader().getParent());
                }
            }
        """.stripIndent()
        buildFile << """
            test {
                systemProperties 'java.system.class.loader':'org.gradle.MySystemClassLoader'
            }
        """.stripIndent()

        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySystemClassLoaderIsUsed')
    }

    def "can run tests referencing slf4j with custom system classloader"() {
        given:
        file('src/test/java/org/gradle/MySystemClassLoader.java') << customSystemClassLoaderClass
        file('src/test/java/org/gradle/TestUsingSlf4j.java') << """
            package org.gradle;

            ${testFrameworkImports}

            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class TestUsingSlf4j {
                @Test
                public void mySystemClassLoaderIsUsed() throws ClassNotFoundException {
                    assertTrue(ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader);
                    assertEquals(getClass().getClassLoader(), ClassLoader.getSystemClassLoader().getParent());

                    Logger logger = LoggerFactory.getLogger(TestUsingSlf4j.class);
                    logger.info("INFO via slf4j");
                    logger.warn("WARN via slf4j");
                    logger.error("ERROR via slf4j");
                }
            }
        """.stripIndent()
        buildFile << """
            dependencies {
                implementation 'org.slf4j:slf4j-api:1.7.30'
                testRuntimeOnly 'org.slf4j:slf4j-simple:1.7.30'
            }

            test {
                systemProperties 'java.system.class.loader':'org.gradle.MySystemClassLoader'
            }
        """.stripIndent()

        when:
        run 'test'

        then:
        def testResults = new DefaultTestExecutionResult(testDirectory)
        testResults.assertTestClassesExecuted('org.gradle.TestUsingSlf4j')
        with(testResults.testClass('org.gradle.TestUsingSlf4j')) {
            assertTestPassed('mySystemClassLoaderIsUsed')
            assertStderr(Matchers.containsText("ERROR via slf4j"))
            assertStderr(Matchers.containsText("WARN via slf4j"))
            assertStderr(Matchers.containsText("INFO via slf4j"))
        }
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "can run tests referencing slf4j with modular java"() {
        Assume.assumeTrue(frameworkSupportsModularJava)

        given:
        file('src/test/java/org/gradle/example/TestUsingSlf4j.java') << """
            package org.gradle.example;

            ${testFrameworkImports}

            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class TestUsingSlf4j {
                @Test
                public void testModular() {
                    Logger logger = LoggerFactory.getLogger(org.gradle.example.TestUsingSlf4j.class);
                    logger.info("INFO via slf4j");
                    logger.warn("WARN via slf4j");
                    logger.error("ERROR via slf4j");
                }
            }
        """.stripIndent()
        file("src/test/java/module-info.java") << """
            module org.gradle.example {
                exports org.gradle.example;
                requires ${moduleName};
                requires org.slf4j;
            }
        """.stripIndent()
        buildFile << """
            dependencies {
                implementation 'org.slf4j:slf4j-api:1.7.30'
                testRuntimeOnly 'org.slf4j:slf4j-simple:1.7.30'
            }
        """.stripIndent()

        when:
        run 'test'

        then:
        def testResults = new DefaultTestExecutionResult(testDirectory)
        testResults.assertTestClassesExecuted('org.gradle.example.TestUsingSlf4j')
        with(testResults.testClass('org.gradle.example.TestUsingSlf4j')) {
            assertTestPassed('testModular')
            assertStderr(Matchers.containsText("ERROR via slf4j"))
            assertStderr(Matchers.containsText("WARN via slf4j"))
            assertStderr(Matchers.containsText("INFO via slf4j"))
        }
    }

    @Requires(
        value = UnitTestPreconditions.Jdk8OrEarlier,
        reason = "Hangs on Java 9"
    )
    def "can run tests with custom system classloader and java agent"() {
        given:
        file('src/main/java/org/gradle/MySystemClassLoader.java') << customSystemClassLoaderClass
        file('src/main/java/org/gradle/MyAgent.java') << """
            package org.gradle;

            import java.lang.instrument.Instrumentation;

            public class MyAgent {
                public static void premain(String args, Instrumentation instrumentation) {
                    System.setProperty("using.custom.agent", "true");

                    // This agent should be loaded via the custom system ClassLoader
                    assert ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader : "systemClassLoader is not an instanceof MySystemClassLoader";
                    assert MyAgent.class.getClassLoader() == ClassLoader.getSystemClassLoader().getParent() : "MyAgent is not loaded via the system classloader: " + MyAgent.class.getClassLoader().getClass().getName();
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/JUnitTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class JUnitTest {
                @Test
                public void mySystemClassLoaderIsUsed() throws ClassNotFoundException {
                    assertEquals("true", System.getProperty("using.custom.agent"));

                    // This test class should be loaded via the custom system ClassLoader
                    assertTrue(ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader);
                    assertTrue(getClass().getClassLoader() == ClassLoader.getSystemClassLoader().getParent());
                }
            }
        """.stripIndent()
        buildFile << """
            jar {
                manifest {
                    attributes 'Premain-Class': 'org.gradle.MyAgent'
                }
            }

            test {
                dependsOn jar
                systemProperties('java.system.class.loader':'org.gradle.MySystemClassLoader')
                jvmArgs("-javaagent:\${jar.archiveFile.asFile.get()}")
            }
        """.stripIndent()

        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySystemClassLoaderIsUsed')
    }

    def "can run tests with custom security manager"() {
        executer
                .withArgument("-Porg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}")
                .withToolchainDetectionEnabled()

        given:
        file('src/test/java/org/gradle/JUnitTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class JUnitTest {
                @Test
                public void mySecurityManagerIsUsed() throws ClassNotFoundException {
                    assertTrue(System.getSecurityManager() instanceof MySecurityManager);
                    assertEquals(ClassLoader.getSystemClassLoader(), MySecurityManager.class.getClassLoader());
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/MySecurityManager.java') << """
            package org.gradle;

            import java.security.Permission;

            public class MySecurityManager extends SecurityManager {
                public MySecurityManager() {
                    assert getClass().getName().equals(System.getProperty("java.security.manager"));
                }

                @Override
                public void checkPermission(Permission permission) {
                }
            }
        """.stripIndent()
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
            test {
                systemProperties 'java.security.manager': 'org.gradle.MySecurityManager'
            }
        """.stripIndent()

        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySecurityManagerIsUsed')
    }

    String getCustomSystemClassLoaderClass() {
        return """
            package org.gradle;

            import java.net.URL;
            import java.net.URLClassLoader;

            public class MySystemClassLoader extends URLClassLoader {
                public MySystemClassLoader(ClassLoader parent) {
                    super(new URL[0], parent);
                    // Should be constructed with the default system ClassLoader as root
                    if (!getClass().getClassLoader().equals(parent)) {
                        throw new AssertionError();
                    }
                }
            }
        """
    }
}
