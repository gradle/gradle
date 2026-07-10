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

package org.gradle.internal.classloader

import groovy.transform.CompileStatic
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Issue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

class TransformingClassLoaderConcurrencyTest extends ConcurrentSpec {

    @Issue("https://github.com/gradle/gradle/issues/36824")
    def "handles concurrent class loading in same package without race condition"() {
        given:
        def classPath = DefaultClassPath.of(ClasspathUtil.getClasspathForClass(TransformingClassLoaderTestClasses))
        def numClasses = TransformingClassLoaderTestClasses.NUM_CLASSES
        def classNames = (1..numClasses).collect {
            "${TransformingClassLoaderTestClasses.name}\$Class${String.format('%02d', it)}"
        }
        when:
        async {
            try (def classLoader = new TestTransformingClassLoader(classPath)) {
                def loadingCompleteLatch = new CountDownLatch(numClasses)
                def loadingBarrier = new CyclicBarrier(numClasses)

                classNames.each { className ->
                    start {
                        try {
                            loadingBarrier.await() // Align all threads to load classes simultaneously
                            classLoader.loadClass(className)
                        } finally {
                            loadingCompleteLatch.countDown()
                        }
                    }
                }
                // Arbitrary timeout to prevent test from being stuck there indefinitely.
                // Not waiting here closes the classloader too early and it may break.
                if (!loadingCompleteLatch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Classloading failed to complete in reasonable time")
                }
            }
        }

        then:
        noExceptionThrown()
    }

    @CompileStatic
    private static class TestTransformingClassLoader extends TransformingClassLoader {
        static {
            registerAsParallelCapable()
        }

        TestTransformingClassLoader(ClassPath classPath) {
            super("test", null, classPath)
        }

        @Override
        protected byte[] transform(String className, byte[] bytes) {
            return bytes
        }
    }
}
