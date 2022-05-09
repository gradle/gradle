/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache

import org.gradle.internal.classloader.DefaultHashingClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.TestHashCodes
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Function

class DefaultClassLoaderCacheTest extends Specification {

    def classpathHasher = new FileClasspathHasher()
    def cache = new DefaultClassLoaderCache(new DefaultHashingClassLoaderFactory(classpathHasher), classpathHasher)
    def id1 = new ClassLoaderId() {
        @Override
        String getDisplayName() { "id1" }
    }
    def id2 = new ClassLoaderId() {
        @Override
        String getDisplayName() { "id2" }
    }

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    TestFile file(String path) {
        def f = testDirectoryProvider.testDirectory.file(path)
        f.text = path
        return f
    }

    ClassPath classPath(String... paths) {
        DefaultClassPath.of(paths.collect { file(it) } as Iterable<File>)
    }

    ClassLoader classLoader(ClassPath classPath) {
        new URLClassLoader(classPath.asURLArray)
    }

    def "class loaders are reused when parent, class path and implementation hash are the same"() {
        expect:
        def root = classLoader(classPath("root"))
        cache.get(id1, classPath("c1"), root, null) == cache.get(id1, classPath("c1"), root, null)
        cache.get(id1, classPath("c1"), root, null) != cache.get(id1, classPath("c1", "c2"), root, null)
        cache.get(id1, classPath("c1"), root, null, TestHashCodes.hashCodeFrom(100)) == cache.get(id1, classPath("c1"), root, null, TestHashCodes.hashCodeFrom(100))
        cache.get(id1, classPath("c1"), root, null, TestHashCodes.hashCodeFrom(100)) != cache.get(id1, classPath("c1", "c2"), root, null, TestHashCodes.hashCodeFrom(200))
        cache.get(id1, classPath("c1"), root, null, TestHashCodes.hashCodeFrom(100)) != cache.get(id1, classPath("c1"), root, null, null)
        cache.get(id1, classPath("c1"), root, null, classpathHasher.hash(classPath("c1"))) == cache.get(id1, classPath("c1"), root, null, null)
    }

    def "class loaders with different ids are reused"() {
        expect:
        def root = classLoader(classPath("root"))
        cache.get(id1, classPath("c1"), root, null).is cache.get(id2, classPath("c1"), root, null)
    }

    def "parents are respected"() {
        expect:
        def root1 = classLoader(classPath("root1"))
        def root2 = classLoader(classPath("root2"))
        cache.get(id1, classPath("c1"), root1, null) != cache.get(id2, classPath("c1"), root2, null)
    }

    def "null parents are respected"() {
        expect:
        def root = classLoader(classPath("root"))
        cache.get(id1, classPath("c1"), null, null) == cache.get(id1, classPath("c1"), null, null)
        cache.get(id1, classPath("c1"), null, null) != cache.get(id1, classPath("c1"), root, null)
    }

    def "filters are respected"() {
        def root = classLoader(classPath("root"))
        def f1 = new FilteringClassLoader.Spec(["1"], [], [], [], [], [], [])
        def f2 = new FilteringClassLoader.Spec(["2"], [], [], [], [], [], [])

        expect:
        cache.get(id1, classPath("c1"), root, f1).is(cache.get(id1, classPath("c1"), root, f1))
        cache.size() == 2
        !cache.get(id1, classPath("c1"), root, f1).is(cache.get(id1, classPath("c1"), root, f2))
        cache.size() == 2
    }

    def "non filtered classloaders are reused"() {
        expect:
        def root = classLoader(classPath("root"))
        def f1 = new FilteringClassLoader.Spec(["1"], [], [], [], [], [], [])
        cache.get(id1, classPath("c1"), root, f1)
        cache.size() == 2
        cache.get(id1, classPath("c1"), root, null)
        cache.size() == 1
    }

    def "filtered classloaders are reused if they have multiple ids"() {
        expect:
        def root = classLoader(classPath("root"))
        def f1 = new FilteringClassLoader.Spec(["1"], [], [], [], [], [], [])
        cache.get(id1, classPath("c1"), root, f1)
        cache.get(id2, classPath("c1"), root, f1)
        cache.size() == 2
        cache.get(id1, classPath("c1"), root, null)
        cache.size() == 2
    }

    def "unfiltered base is released when there are no more references to it"() {
        expect:
        def root = classLoader(classPath("root"))
        def f1 = new FilteringClassLoader.Spec(["1"], [], [], [], [], [], [])
        def f2 = new FilteringClassLoader.Spec(["2"], [], [], [], [], [], [])
        def cp1 = classPath("c1")
        def cp2 = classPath("c2")

        cache.get(id1, cp1, root, f1)
        cache.get(id2, cp1, root, f2)
        cache.size() == 3
        cache.get(id1, cp2, root, f1)
        cache.size() == 4
        cache.get(id1, cp1, root, null)
        cache.size() == 2
        cache.get(id1, cp2, root, null)
        cache.get(id2, cp2, root, null)
        cache.size() == 1
    }

    def "removes stale classloader"() {
        def root = classLoader(classPath("root"))
        cache.get(id1, classPath("c1"), root, null)
        def c2 = cache.get(id1, classPath("c2"), root, null)
        expect:
        cache.size() == 1
        c2.is cache.get(id1, classPath("c2"), root, null)
    }

    def "can remove loaders"() {
        expect:
        cache.size() == 0

        when:
        cache.remove(id1)

        then:
        noExceptionThrown()
        cache.size() == 0

        when:
        def root = classLoader(classPath("root"))
        cache.get(id1, classPath("c2"), root, null)
        cache.get(id2, classPath("c2"), root, null)

        then:
        cache.size() == 1 // both are the same

        when:
        cache.remove(id1)

        then:
        cache.size() == 1 // still used by id2

        when:
        cache.remove(id2)

        then:
        cache.size() == 0
    }

    def "can add specialized loaders"() {
        def parent = Stub(ClassLoader)
        def loader = Stub(ClassLoader)
        def classPath = classPath("root")

        when:
        def result = cache.createIfAbsent(id1, classPath, parent, { ClassLoader spec -> loader } as Function, null)

        then:
        result == loader
        cache.size() == 1

        when:
        def result2 = cache.createIfAbsent(id1, classPath, parent, { throw new RuntimeException() } as Function, null)

        then:
        result2 == loader
        cache.size() == 1

        when:
        def result3 = cache.get(id1, classPath, parent, null)

        then:
        result3 == loader
        cache.size() == 1

        when:
        cache.remove(id1)

        then:
        cache.size() == 0
    }
}
