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

package org.gradle.internal.classpath.types

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer
import org.gradle.internal.classpath.InstrumentingClasspathFileTransformer
import org.gradle.internal.hash.HashCode
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.junit.Rule

class InstrumentationTypeRegistryTest extends ConcurrentSpec {

    private static final THIS_CLASS_NAME = InstrumentationTypeRegistryTest.class.name.replace(".", "/")

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory
    def cache = new TestInMemoryCacheFactory().open(testDir.file("cache"), "test")
    def instrumentingTransformer = Stub(InstrumentingClasspathFileTransformer) {
        getConfigHash() >> HashCode.fromBytes(new byte[]{0, 0, 0, 0})
    }
    def classpathWalker = new ClasspathWalker(TestFiles.fileSystem())
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def parallelExecutorFactory = new DefaultCachedClasspathTransformer.ParallelTransformExecutor(cache, executorFactory.create("test"))
    def gradleCoreRegistry = new TestGradleCoreInstrumentationTypeRegistry([
        ("$THIS_CLASS_NAME\$DefaultTask".toString()): ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set,
        ("$THIS_CLASS_NAME\$VerificationTask".toString()): ["org/gradle/api/VerificationTask"] as Set
    ])

    def "should collect instrumented types"() {
        given:
        def dir = testDir.file("thing.dir")
        createClasses(dir)
        def factory = new DefaultInstrumentationTypeRegistryFactory(
            gradleCoreRegistry,
            cache,
            parallelExecutorFactory,
            classpathWalker,
            fileSystemAccess
        )

        when:
        def className = D.name.replace('.', '/')
        def typeRegistry = factory.createFor([dir], instrumentingTransformer)
        def superTypes = typeRegistry.getSuperTypes(className)

        then:
        superTypes ==~ [
            'org/gradle/api/Task',
            'org/gradle/api/internal/TaskInternal',
            "$THIS_CLASS_NAME\$DefaultTask",
            "org/gradle/api/VerificationTask",
            "$THIS_CLASS_NAME\$VerificationTask",
            "$THIS_CLASS_NAME\$B",
            "$THIS_CLASS_NAME\$C",
            "$THIS_CLASS_NAME\$D"
        ].collect { it.toString() }
    }

    def createClasses(TestFile dir) {
        dir.deleteDir()
        dir.createDir()
        [B, C, D, E, F].each { clazz ->
            dir.file("${clazz.simpleName}.class").bytes = getClassBytes(clazz)
        }
        return dir.listFiles().toList()
    }

    static interface VerificationTask {
    }

    static abstract class DefaultTask {
    }

    static abstract class B extends DefaultTask {
    }
    static abstract class C extends B implements VerificationTask {
    }
    static abstract class D extends C {
    }

    static abstract class E {
    }
    static abstract class F extends E {
    }

    byte[] getClassBytes(Class<?> clazz) {
        return clazz.classLoader.getResource(clazz.name.replace('.', '/') + ".class").bytes
    }

    private static class TestGradleCoreInstrumentationTypeRegistry implements InstrumentationTypeRegistry {

        private final Map<String, Set<String>> instrumentedSuperTypes

        TestGradleCoreInstrumentationTypeRegistry(Map<String, Set<String>> instrumentedSuperTypes) {
            this.instrumentedSuperTypes = instrumentedSuperTypes
        }

        @Override
        Set<String> getSuperTypes(String type) {
            return instrumentedSuperTypes.getOrDefault(type, Collections.emptySet())
        }

        @Override
        boolean isEmpty() {
            return instrumentedSuperTypes.isEmpty()
        }
    }
}
