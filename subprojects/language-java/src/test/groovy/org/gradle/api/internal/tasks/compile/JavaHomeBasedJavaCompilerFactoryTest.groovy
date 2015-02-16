/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.tools.JavaCompiler
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class JavaHomeBasedJavaCompilerFactoryTest extends Specification {
    JavaHomeBasedJavaCompilerFactory factory = new JavaHomeBasedJavaCompilerFactory()
    JavaHomeBasedJavaCompilerFactory.JavaHomeProvider currentJvmJavaHomeProvider = Mock()
    JavaHomeBasedJavaCompilerFactory.JavaHomeProvider systemPropertiesJavaHomeProvider = Mock()
    JavaHomeBasedJavaCompilerFactory.JavaCompilerProvider javaCompilerProvider = Mock()
    JavaCompiler javaCompiler = Mock()
    @Rule TestNameTestDirectoryProvider temporaryFolder

    def setup() {
        factory.currentJvmJavaHomeProvider = currentJvmJavaHomeProvider
        factory.systemPropertiesJavaHomeProvider = systemPropertiesJavaHomeProvider
        factory.javaCompilerProvider = javaCompilerProvider
    }

    def "creates Java compiler for matching Java home directory"() {
        TestFile javaHome = temporaryFolder.file('my/test/java/home')

        when:
        JavaCompiler expectedJavaCompiler = factory.create()

        then:
        1 * currentJvmJavaHomeProvider.dir >> javaHome
        1 * systemPropertiesJavaHomeProvider.dir >> javaHome
        1 * javaCompilerProvider.compiler >> javaCompiler
        javaCompiler == expectedJavaCompiler
    }

    def "cannot find Java compiler for matching Java home directory"() {
        TestFile javaHome = temporaryFolder.file('my/test/java/home')

        when:
        factory.create()

        then:
        1 * currentJvmJavaHomeProvider.dir >> javaHome
        1 * systemPropertiesJavaHomeProvider.dir >> javaHome
        1 * javaCompilerProvider.compiler >> null
        Throwable t = thrown(RuntimeException)
        t.message == 'Cannot find System Java Compiler. Ensure that you have installed a JDK (not just a JRE) and configured your JAVA_HOME system variable to point to the according directory.'
    }

    def "creates Java compiler for mismatching Java home directory"() {
        TestFile realJavaHome = temporaryFolder.file('my/test/java/home/real')
        TestFile javaHomeFromToolProvidersPointOfView = temporaryFolder.file('my/test/java/home/toolprovider')

        when:
        JavaCompiler expectedJavaCompiler = factory.create()

        then:
        1 * currentJvmJavaHomeProvider.dir >> realJavaHome
        1 * systemPropertiesJavaHomeProvider.dir >> javaHomeFromToolProvidersPointOfView
        1 * javaCompilerProvider.compiler >> javaCompiler
        javaCompiler == expectedJavaCompiler
        SystemProperties.javaHomeDir.canonicalPath == javaHomeFromToolProvidersPointOfView.canonicalPath
    }

    @IgnoreIf({ GradleContextualExecuter.isParallel() })
    def "creates Java compiler for mismatching Java home directory for multiple threads concurrently"() {
        int threadCount = 100
        TestFile realJavaHome = temporaryFolder.file('my/test/java/home/real')
        TestFile javaHomeFromToolProvidersPointOfView = temporaryFolder.file('my/test/java/home/toolprovider')

        when:
        concurrent(threadCount) {
            JavaCompiler expectedJavaCompiler = factory.create()
            assert javaCompiler == expectedJavaCompiler
        }

        then:
        threadCount * currentJvmJavaHomeProvider.dir >> realJavaHome
        threadCount * systemPropertiesJavaHomeProvider.dir >> javaHomeFromToolProvidersPointOfView
        threadCount * javaCompilerProvider.compiler >> javaCompiler
        assert SystemProperties.javaHomeDir.canonicalPath == javaHomeFromToolProvidersPointOfView.canonicalPath
    }

    def concurrent(int count, Closure closure) {
        def values = []
        def futures = []

        ExecutorService executor = Executors.newFixedThreadPool(count)
        CyclicBarrier barrier = new CyclicBarrier(count)

        (1..count).each {
            futures.add(executor.submit(new Callable() {
                Object call() throws Exception {
                    barrier.await()
                    closure.call()
                }
            }))
        }

        futures.each { future ->
            values << future.get()
        }

        values
    }
}
