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
import org.gradle.internal.hash.HashCode
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

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
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

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
        cache.get(id1, classPath("c1"), root, null, HashCode.fromInt(100)) == cache.get(id1, classPath("c1"), root, null, HashCode.fromInt(100))
        cache.get(id1, classPath("c1"), root, null, HashCode.fromInt(100)) != cache.get(id1, classPath("c1", "c2"), root, null, HashCode.fromInt(200))
        cache.get(id1, classPath("c1"), root, null, HashCode.fromInt(100)) != cache.get(id1, classPath("c1"), root, null, null)
        cache.get(id1, classPath("c1"), root, null, classpathHasher.hash(classPath("c1"))) == cache.get(id1, classPath("c1"), root, null, null)
    }

    def "class loaders with different ids and same spec are reused"() {
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
        cache.size() == 3
    }

    def "non filtered classloaders are reused"() {
        expect:
        def root = classLoader(classPath("root"))
        def f1 = new FilteringClassLoader.Spec(["1"], [], [], [], [], [], [])
        cache.get(id1, classPath("c1"), root, f1)
        cache.size() == 2
        cache.get(id1, classPath("c1"), root, null)
        cache.get(id1, classPath("c1"), root, null).is(cache.get(id1, classPath("c1"), root, f1).parent)
        cache.size() == 2
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

    def "retains soft reference to unused classloaders at the end of the build"() {
        expect:
        def root = classLoader(classPath("root"))
        def c1 = cache.get(id1, classPath("c1"), root, null)
        def c2 = cache.get(id2, classPath("c2"), root, null)

        cache.beforeComplete()
        cache.size() == 2
        cache.retained() == 0

        cache.get(id1, classPath("c1"), root, null).is(c1)

        cache.beforeComplete()
        cache.size() == 1
        cache.retained() == 1

        cache.get(id1, classPath("c1"), root, null).is(c1)
        // Reuse from soft reference
        cache.get(id2, classPath("c2"), root, null).is(c2)

        cache.size() == 2
        cache.retained() == 0
    }

    def "recreates unused classloaders if soft reference is cleared"() {
        expect:
        def root = classLoader(classPath("root"))
        def c1 = cache.get(id1, classPath("c1"), root, null)

        cache.beforeComplete()
        cache.size() == 1
        cache.retained() == 0

        cache.beforeComplete()
        cache.size() == 0
        cache.retained() == 1
        cache.releaseReferences()

        !cache.get(id1, classPath("c1"), root, null).is(c1)

        cache.size() == 1
        cache.retained() == 0
    }

    def "can put loaders"() {
        def loader = Stub(ClassLoader)

        when:
        cache.put(id1, loader)

        then:
        cache.size() == 1
    }

    def "can replace specialized loader"() {
        def parent = classLoader(classPath("root"))
        def loader1 = Stub(ClassLoader)
        def loader2 = Stub(ClassLoader)

        when:
        cache.put(id1, loader1)

        then:
        cache.size() == 1

        when:
        cache.put(id1, loader2)

        then:
        cache.size() == 1

        when:
        def cl = cache.get(id1, classPath("c1"), parent, null)

        then:
        cl != loader1
        cl != loader2
        cache.size() == 2

        when:
        cache.put(id1, loader1)

        then:
        cache.size() == 2
    }
}
