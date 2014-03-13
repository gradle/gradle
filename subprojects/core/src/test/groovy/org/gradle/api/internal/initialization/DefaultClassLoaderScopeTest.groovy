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
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultClassLoaderScopeTest extends Specification {

    ClassLoader parentClassLoader
    ClassLoader baseClassLoader

    ClassLoaderScope parent
    ClassLoaderScope base
    ClassLoaderScope scope

    Cache<DefaultClassLoaderCache.Key, ClassLoader> backingCache = CacheBuilder.newBuilder().build();
    ClassLoaderCache classLoaderCache = new DefaultClassLoaderCache(backingCache);

    @Rule TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        parentClassLoader = new URLClassLoader(classPath("root").asURLArray)
        baseClassLoader = new URLClassLoader(classPath("base").asURLArray)
        parent = new RootClassLoaderScope(parentClassLoader, classLoaderCache)
        base = new RootClassLoaderScope(baseClassLoader, classLoaderCache)
        scope = new DefaultClassLoaderScope(parent, base, classLoaderCache)
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
        scope.childClassLoader.is parentClassLoader
        scope.scopeClassLoader.is parentClassLoader
    }

    def "locked scope with only exports only exports exports"() {
        when:
        file("export/foo") << "bar"
        def exportClassLoader = scope.export(classPath("export"))
        scope.lock()

        then:
        exportClassLoader.getResource("foo").text == "bar"
        scope.childClassLoader.is scope.scopeClassLoader
        scope.scopeClassLoader.getResource("foo").text == "bar"
    }

    def "locked scope with only local exports parent loader to children"() {
        when:
        file("local/foo") << "bar"
        def localClassLoader = scope.addLocal(classPath("local"))
        scope.lock()

        then:
        localClassLoader.getResource("foo").text == "bar"
        scope.childClassLoader.is parentClassLoader
        scope.scopeClassLoader.getResource("foo").text == "bar"
    }

    def "locked scope with local and exports exports custom classloader to children"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        def localClassLoader = scope.addLocal(classPath("local"))
        def exportClassLoader = scope.export(classPath("export"))
        scope.lock()

        then:
        localClassLoader.getResource("local").text == "bar"
        exportClassLoader.getResource("export").text == "bar"
        scope.childClassLoader.getResource("export").text == "bar"
        scope.childClassLoader.getResource("local") == null
        scope.scopeClassLoader instanceof CachingClassLoader
        scope.scopeClassLoader.getResource("export").text == "bar"
        scope.scopeClassLoader.getResource("local").text == "bar"
    }

    def "requesting loaders before locking creates pessimistic setup"() {
        given:
        scope.scopeClassLoader // trigger

        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        def localClassLoader = scope.addLocal(classPath("local"))
        def exportClassLoader = scope.export(classPath("export"))

        then:
        localClassLoader.getResource("local").text == "bar"
        exportClassLoader.getResource("export").text == "bar"
        scope.childClassLoader.getResource("export").text == "bar"
        scope.childClassLoader.getResource("local") == null
        scope.scopeClassLoader instanceof CachingClassLoader
        scope.scopeClassLoader.getResource("export").text == "bar"
        scope.scopeClassLoader.getResource("local").text == "bar"
    }

    def "cannot modify after locking"() {
        given:
        scope.lock()

        when:
        scope.addLocal(classPath("local"))

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
        scope.addLocal(classPath("local"))
        scope.export(classPath("export"))
        scope.lock()
        def child = scope.createChild()
        child.lock()

        then:
        child.scopeClassLoader.getResource("export").text == "bar"
        child.scopeClassLoader.getResource("local") == null
        child.childClassLoader.getResource("export").text == "bar"
        child.childClassLoader.getResource("local") == null
    }

    def "sibling scopes can not access exported or local"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.addLocal(classPath("local"))
        scope.export(classPath("export"))
        scope.lock()
        def sibling = scope.createSibling()
        sibling.lock()

        then:
        sibling.scopeClassLoader.getResource("export") == null
        sibling.scopeClassLoader.getResource("local") == null
        sibling.childClassLoader.getResource("export") == null
        sibling.childClassLoader.getResource("local") == null
    }

    def "rebased children local additions can access parent exported"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.addLocal(classPath("local"))
        scope.export(classPath("export"))
        scope.lock()
        def child = scope.createRebasedChild()
        file("childLocal/childLocal") << "bar"
        def childLocalClassLoader = child.addLocal(classPath("childLocal"))
        child.lock()

        then:
        childLocalClassLoader.getResource("local") == null
        childLocalClassLoader.getResource("export").text == "bar"
        childLocalClassLoader.getResource("childLocal").text == "bar"
    }

    def "class loaders are reused"() {
        expect:
        backingCache.size() == 0

        when:
        file("c1/c1") << "bar"
        file("c2/c2") << "bar"
        def c1ExportLoader = scope.export(classPath("c1"))
        def c2Local = scope.addLocal(classPath("c2"))
        scope.lock()

        def sibling = scope.createSibling()
        def child = scope.createChild()

        then:
        backingCache.size() == 2
        sibling.export(classPath("c1")).is c1ExportLoader
        backingCache.size() == 2

        !child.export(classPath("c1")).is(c1ExportLoader) // classpath is the same, but parent is different
        backingCache.size() == 3

        sibling.addLocal(classPath("c2")).is c2Local
        child.addLocal(classPath("c2")).is c2Local
        backingCache.size() == 3
    }

    def "pessimistic structure has parent visibility"() {
        when:
        file("root/root") << "foo"

        then:
        scope.scopeClassLoader.getResource("root").text == "foo"
    }

    def "optimised structure has parent visibility"() {
        when:
        file("root/root") << "foo"

        then:
        scope.lock().scopeClassLoader.getResource("root").text == "foo"
    }
}
