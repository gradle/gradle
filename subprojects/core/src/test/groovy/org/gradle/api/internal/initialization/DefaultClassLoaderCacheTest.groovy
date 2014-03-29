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

package org.gradle.api.internal.initialization

import com.google.common.cache.CacheBuilder
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultClassLoaderCacheTest extends Specification {

    def backingCache = CacheBuilder.newBuilder().build()
    def cache = new DefaultClassLoaderCache(backingCache)

    @Rule TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    TestFile file(String path) {
        testDirectoryProvider.testDirectory.file(path)
    }

    ClassPath classPath(String... paths) {
        new DefaultClassPath(paths.collect { file(it) } as Iterable<File>)
    }

    ClassLoader classLoader(ClassPath classPath) {
        new URLClassLoader(classPath.asURLArray)
    }

    def "class loaders are reused"() {
        expect:
        def root = classLoader(classPath("root"))
        cache.get(root, classPath("c1"), null).is cache.get(root, classPath("c1"), null)
    }

    def "parents are respected"() {
        expect:
        def root1 = classLoader(classPath("root1"))
        def root2 = classLoader(classPath("root2"))
        cache.get(root1, classPath("c1"), null) != cache.get(root2, classPath("c1"), null)
    }

    def "filters are respected"() {
        expect:
        def root = classLoader(classPath("root"))
        def f1 = new FilteringClassLoader.Spec(["1"], [], [], [], [], [])
        def f2 = new FilteringClassLoader.Spec(["2"], [], [], [], [], [])
        cache.get(root, classPath("c1"), f1).is(cache.get(root, classPath("c1"), f1))
        !cache.get(root, classPath("c1"), f1).is(cache.get(root, classPath("c1"), f2))
        backingCache.size() == 3
    }

    def "non filtered classloaders are reused"() {
        expect:
        def root = classLoader(classPath("root"))
        def f1 = new FilteringClassLoader.Spec(["1"], [], [], [], [], [])
        cache.get(root, classPath("c1"), f1)
        backingCache.size() == 2
        cache.get(root, classPath("c1"), null)
        backingCache.size() == 2
    }

}
