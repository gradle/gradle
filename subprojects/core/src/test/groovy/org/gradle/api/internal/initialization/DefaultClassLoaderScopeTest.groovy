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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.api.internal.initialization.loadercache.DefaultClassLoaderCache
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultClassLoaderScopeTest extends Specification {

    ClassLoader rootClassLoader

    ClassLoaderScope root
    ClassLoaderScope scope

    Cache<DefaultClassLoaderCache.Key, ClassLoader> backingCache = CacheBuilder.newBuilder().build()
    ClassLoaderCache classLoaderCache = new DefaultClassLoaderCache(backingCache)

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        file("root/root") << "root"
        rootClassLoader = new URLClassLoader(classPath("root").asURLArray)
        root = new RootClassLoaderScope(rootClassLoader, classLoaderCache)
        scope = root.createChild()
    }

    TestFile file(String path) {
        testDirectoryProvider.testDirectory.file(path)
    }

    ClassPath classPath(String... paths) {
        new DefaultClassPath(paths.collect { file(it).createDir() } as Iterable<File>)
    }

    def "locked scope with no modifications exports parent"() {
        when:
        scope.lock()

        then:
        scope.localClassLoader.is rootClassLoader
        scope.exportClassLoader.is rootClassLoader
    }

    def "ignores empty classpaths"() {
        when:
        scope.export(classPath())
        scope.local(classPath())
        scope.lock()

        then:
        scope.localClassLoader.is rootClassLoader
        scope.exportClassLoader.is rootClassLoader
    }

    def "locked scope with only exports uses same export and local loader"() {
        when:
        file("export/foo") << "foo"
        scope.export(classPath("export")).lock()

        then:
        scope.exportClassLoader.getResource("root").text == "root"
        scope.exportClassLoader.getResource("foo").text == "foo"
        scope.exportClassLoader instanceof URLClassLoader
        scope.exportClassLoader.parent.is rootClassLoader
        scope.localClassLoader.is scope.exportClassLoader
    }

    def "can export more than one classpath"() {
        when:
        file("export/1/foo") << "foo"
        file("export/2/bar") << "bar"
        scope.
                export(classPath("export/1")).
                export(classPath("export/2")).
                lock()

        then:
        scope.exportClassLoader.getResource("root").text == "root"
        scope.exportClassLoader.getResource("foo").text == "foo"
        scope.exportClassLoader.getResource("bar").text == "bar"
        scope.localClassLoader.is scope.exportClassLoader
    }

    def "locked scope with only local exports parent loader to children"() {
        when:
        file("local/1/foo") << "foo"
        file("local/2/bar") << "bar"
        scope.
                local(classPath("local/1")).
                local(classPath("local/2")).
                lock()

        then:
        scope.localClassLoader.getResource("root").text == "root"
        scope.localClassLoader.getResource("foo").text == "foo"
        scope.localClassLoader.getResource("bar").text == "bar"
        scope.localClassLoader != scope.exportClassLoader
        scope.exportClassLoader.is rootClassLoader
    }

    def "locked scope with only one local exports parent loader to children and uses loader as local loader"() {
        when:
        file("local/foo") << "foo"
        scope.local(classPath("local")).lock()

        then:
        scope.localClassLoader.getResource("root").text == "root"
        scope.localClassLoader.getResource("foo").text == "foo"
        scope.localClassLoader instanceof URLClassLoader
        scope.localClassLoader.parent.is rootClassLoader
        scope.exportClassLoader.is rootClassLoader
    }

    def "locked scope with local and exports exports custom classloader to children"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.
                local(classPath("local")).
                export(classPath("export")).
                lock()

        then:
        scope.exportClassLoader.getResource("export").text == "bar"
        scope.exportClassLoader.getResource("local") == null
        scope.exportClassLoader instanceof URLClassLoader
        scope.exportClassLoader.parent == rootClassLoader

        scope.localClassLoader instanceof CachingClassLoader
        scope.localClassLoader.getResource("export").text == "bar"
        scope.localClassLoader.getResource("local").text == "bar"
    }

    def "requesting loaders before locking creates pessimistic setup"() {
        given:
        scope.localClassLoader // trigger

        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.
                local(classPath("local")).
                export(classPath("export"))

        then:
        scope.exportClassLoader instanceof CachingClassLoader
        scope.exportClassLoader.getResource("root").text == "root"
        scope.exportClassLoader.getResource("export").text == "bar"
        scope.exportClassLoader.getResource("local") == null

        scope.localClassLoader instanceof CachingClassLoader
        scope.localClassLoader.getResource("root").text == "root"
        scope.localClassLoader.getResource("export").text == "bar"
        scope.localClassLoader.getResource("local").text == "bar"
    }

    def "cannot modify after locking"() {
        given:
        scope.lock()

        when:
        scope.local(classPath("local"))

        then:
        thrown IllegalStateException

        when:
        scope.export(classPath("local"))

        then:
        thrown IllegalStateException
    }

    def "child scopes can access exported but not local"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.local(classPath("local"))
        scope.export(classPath("export"))
        def child = scope.lock().createChild().lock()

        then:
        child.localClassLoader.getResource("root").text == "root"
        child.localClassLoader.getResource("export").text == "bar"
        child.localClassLoader.getResource("local") == null
        child.exportClassLoader.getResource("root").text == "root"
        child.exportClassLoader.getResource("export").text == "bar"
        child.exportClassLoader.getResource("local") == null
    }

    def "class loaders are reused"() {
        expect:
        backingCache.size() == 0

        when:
        def c1 = classPath("c1")
        def c2 = classPath("c2")
        scope.export(c1).local(c2).lock()
        scope.exportClassLoader

        then:
        backingCache.size() == 2

        when:
        def sibling = root.createChild().export(c1).local(c2).lock()
        sibling.exportClassLoader

        then:
        sibling.exportClassLoader.is scope.exportClassLoader
        backingCache.size() == 2

        when:
        def child = scope.createChild().export(c1).lock()
        child.exportClassLoader

        then:
        child.exportClassLoader != scope.exportClassLoader // classpath is the same, but root is different
        backingCache.size() == 3
    }

    def "pessimistic structure has parent visibility"() {
        expect:
        scope.localClassLoader.getResource("root").text == "root"
    }

    def "optimised structure has parent visibility"() {
        expect:
        scope.lock().localClassLoader.getResource("root").text == "root"
    }

}
