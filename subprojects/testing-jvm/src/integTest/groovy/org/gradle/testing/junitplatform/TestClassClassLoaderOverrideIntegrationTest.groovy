/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult

class TestClassClassLoaderOverrideIntegrationTest extends JUnitPlatformIntegrationSpec {

    def "can set test classes classloader in custom test engine"() {
        given:
        file("src/test/resources/META-INF/services/org.junit.platform.engine.TestEngine") << """
            org.gradle.CustomTestEngine
        """
        file("src/test/java/org/gradle/CustomTestEngine.java") << """
            package org.gradle;

            import java.net.MalformedURLException;

            import org.junit.platform.engine.EngineDiscoveryRequest;
            import org.junit.platform.engine.ExecutionRequest;
            import org.junit.platform.engine.TestDescriptor;
            import org.junit.platform.engine.TestEngine;
            import org.junit.platform.engine.UniqueId;

            import org.junit.jupiter.engine.JupiterTestEngine;

            public class CustomTestEngine implements TestEngine {
                private final JupiterTestEngine delegateEngine = new JupiterTestEngine();

                @Override
                public String getId() {
                    return "custom-test-engine";
                }

                @Override
                public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
                    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
                    try {
                        ClassLoader customClassLoader = new CustomClassLoader(originalClassLoader);
                        Thread.currentThread().setContextClassLoader(customClassLoader);
                        return delegateEngine.discover(discoveryRequest, uniqueId);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    } finally {
                        Thread.currentThread().setContextClassLoader(originalClassLoader);
                    }
                }

                @Override
                public void execute(ExecutionRequest request) {
                    delegateEngine.execute(request);
                }
            }
        """
        String testPath = testDirectory.absolutePath
        String platformIndependentTestPath = testPath.replace('\\', '/')
        file("src/test/java/org/gradle/CustomClassLoader.java") << """
            package org.gradle;
            import java.io.File;
            import java.net.MalformedURLException;
            import java.net.URL;
            import java.net.URLClassLoader;
            public class CustomClassLoader extends URLClassLoader {
                public CustomClassLoader(ClassLoader parent) throws MalformedURLException {
                    super(new URL[]{new File("$platformIndependentTestPath/build/classes/java/test/").toURI().toURL()}, parent);
                }
                @Override
                public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (!name.equals("org.gradle.SampleTest")) {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass != null) {
                        return loadedClass;
                    }
                    try {
                        return findClass(name);
                    } catch (ClassNotFoundException e) {
                        return super.loadClass(name, resolve);
                    }
                }
            }
        """
        file("src/test/java/org/gradle/SampleTest.java") << """
            package org.gradle;
            import static org.junit.jupiter.api.Assertions.*;
            import org.junit.jupiter.api.*;
            class SampleTest {
                @Test
                void testClassLoader() {
                    assertTrue(getClass().getClassLoader() instanceof CustomClassLoader);
                }
            }
        """
        buildFile << """
            test {
                useJUnitPlatform {
                    excludeEngines("junit-jupiter")
                }
            }
        """

        when:
        succeeds("test")

        then:
        def testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted("org.gradle.SampleTest")
        testResult.testClass("org.gradle.SampleTest").assertTestCount(1, 0, 0).assertTestPassed("testClassLoader()")
    }
}

