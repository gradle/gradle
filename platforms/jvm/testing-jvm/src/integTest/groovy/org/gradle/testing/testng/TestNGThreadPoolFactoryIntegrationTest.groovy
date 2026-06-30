/*
 * Copyright 2025 the original author or authors.
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
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/28955")
class TestNGThreadPoolFactoryIntegrationTest extends AbstractIntegrationSpec {

    def "can configure threadPoolFactoryClass with TestNG 7.10+ (new IExecutorServiceFactory API)"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:7.10.2'
            }
            test {
                systemProperty 'factoryMarkerFile', new File(layout.buildDirectory.asFile.get(), 'factory-invoked.marker').absolutePath
                useTestNG {
                    threadPoolFactoryClass = 'org.gradle.test.CustomExecutorServiceFactory'
                    parallel = 'methods'
                    threadCount = 2
                }
            }
        """

        file("src/test/java/org/gradle/test/CustomExecutorServiceFactory.java") << """
            package org.gradle.test;

            import java.io.File;
            import java.io.IOException;
            import java.util.concurrent.BlockingQueue;
            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.ThreadFactory;
            import java.util.concurrent.ThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public class CustomExecutorServiceFactory implements org.testng.IExecutorServiceFactory {
                @Override
                public ExecutorService create(
                        int corePoolSize,
                        int maximumPoolSize,
                        long keepAliveTime,
                        TimeUnit unit,
                        BlockingQueue<Runnable> workQueue,
                        ThreadFactory threadFactory) {
                    String markerPath = System.getProperty("factoryMarkerFile");
                    if (markerPath != null) {
                        File marker = new File(markerPath);
                        marker.getParentFile().mkdirs();
                        try {
                            marker.createNewFile();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
                }
            }
        """

        file("src/test/java/org/gradle/test/SimpleTest.java") << """
            package org.gradle.test;

            import org.testng.annotations.Test;

            public class SimpleTest {
                @Test
                public void testPassOne() {
                    assert true;
                }

                @Test
                public void testPassTwo() {
                    assert true;
                }
            }
        """

        when:
        succeeds("test")

        then:
        file("build/factory-invoked.marker").assertExists()
    }

    def "fails with informative error when threadPoolFactoryClass cannot be loaded (TestNG 7.10+)"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:7.10.2'
            }
            test {
                useTestNG {
                    threadPoolFactoryClass = 'org.gradle.test.DoesNotExist'
                }
            }
        """

        file("src/test/java/org/gradle/test/SimpleTest.java") << """
            package org.gradle.test;

            import org.testng.annotations.Test;

            public class SimpleTest {
                @Test
                public void testPass() {
                    assert true;
                }
            }
        """

        when:
        fails("test")

        then:
        failure.assertHasCause("Could not load thread pool factory class 'org.gradle.test.DoesNotExist'.")
    }

    def "fails with informative error when threadPoolFactoryClass does not implement IExecutorServiceFactory (TestNG 7.10+)"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:7.10.2'
            }
            test {
                useTestNG {
                    threadPoolFactoryClass = 'org.gradle.test.NotAFactory'
                }
            }
        """

        file("src/test/java/org/gradle/test/NotAFactory.java") << """
            package org.gradle.test;

            public class NotAFactory {
            }
        """

        file("src/test/java/org/gradle/test/SimpleTest.java") << """
            package org.gradle.test;

            import org.testng.annotations.Test;

            public class SimpleTest {
                @Test
                public void testPass() {
                    assert true;
                }
            }
        """

        when:
        fails("test")

        then:
        failure.assertHasCause("The thread pool factory class 'org.gradle.test.NotAFactory' does not implement org.testng.IExecutorServiceFactory.")
    }

    def "can configure threadPoolFactoryClass with TestNG 7.5 (legacy setExecutorFactoryClass API)"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:7.5'
            }
            test {
                useTestNG {
                    threadPoolFactoryClass = 'org.gradle.test.CustomExecutorFactory'
                }
            }
        """

        // For the old API, we use reflection-based delegation to avoid
        // compiling against complex internal TestNG classes.
        file("src/test/java/org/gradle/test/CustomExecutorFactory.java") << """
            package org.gradle.test;

            import org.testng.thread.IExecutorFactory;
            import org.testng.thread.ITestNGThreadPoolExecutor;
            import org.testng.thread.IThreadWorkerFactory;
            import org.testng.IDynamicGraph;
            import org.testng.ISuite;
            import org.testng.ITestNGMethod;

            import java.util.Comparator;
            import java.util.concurrent.BlockingQueue;
            import java.util.concurrent.TimeUnit;

            public class CustomExecutorFactory implements IExecutorFactory {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public ITestNGThreadPoolExecutor newSuiteExecutor(
                        String name,
                        IDynamicGraph<ISuite> graph,
                        IThreadWorkerFactory<ISuite> factory,
                        int corePoolSize,
                        int maximumPoolSize,
                        long keepAliveTime,
                        TimeUnit unit,
                        BlockingQueue<Runnable> workQueue,
                        Comparator<ISuite> comparator) {
                    try {
                        Class clazz = Class.forName("org.testng.internal.thread.graph.TestNGThreadPoolExecutor");
                        return (ITestNGThreadPoolExecutor) clazz.getConstructors()[0].newInstance(
                            name, graph, factory, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, comparator);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public ITestNGThreadPoolExecutor newTestMethodExecutor(
                        String name,
                        IDynamicGraph<ITestNGMethod> graph,
                        IThreadWorkerFactory<ITestNGMethod> factory,
                        int corePoolSize,
                        int maximumPoolSize,
                        long keepAliveTime,
                        TimeUnit unit,
                        BlockingQueue<Runnable> workQueue,
                        Comparator<ITestNGMethod> comparator) {
                    try {
                        Class clazz = Class.forName("org.testng.internal.thread.graph.TestNGThreadPoolExecutor");
                        return (ITestNGThreadPoolExecutor) clazz.getConstructors()[0].newInstance(
                            name, graph, factory, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, comparator);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """

        file("src/test/java/org/gradle/test/SimpleTest.java") << """
            package org.gradle.test;

            import org.testng.annotations.Test;

            public class SimpleTest {
                @Test
                public void testPass() {
                    assert true;
                }
            }
        """

        when:
        succeeds("test")

        then:
        noExceptionThrown()
    }
}
