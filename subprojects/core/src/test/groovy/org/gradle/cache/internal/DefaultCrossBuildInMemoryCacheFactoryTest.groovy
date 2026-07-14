/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.cache.internal

import groovy.transform.CompileStatic
import org.gradle.internal.session.BuildSessionLifecycleListener
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Timeout

import java.lang.ref.WeakReference
import java.util.function.Function
import javax.tools.ToolProvider

class DefaultCrossBuildInMemoryCacheFactoryTest extends AbstractCrossBuildInMemoryCacheTest {

    @Override
    CrossBuildInMemoryCache<String, Object> newCache() {
        return factory.newCache()
    }

    def "retains strong references to values from the previous session"() {
        def function = Mock(Function)

        when:
        def cache = factory.newCache()
        cache.get("a", function)
        cache.get("b", function)

        then:
        1 * function.apply("a") >> new Object()
        1 * function.apply("b") >> new Object()
        0 * function._

        when:
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()
        System.gc()
        cache.get("a", function)
        cache.get("b", function)

        then:
        0 * function._
    }

    def "creates a cache whose keys are classes"() {
        def a = new Object()
        def b = new Object()
        def c = new Object()
        def function = Mock(Function)

        given:
        function.apply(String) >> a
        function.apply(Long) >> b

        def cache = factory.newClassCache()

        expect:
        cache.get(String, function) == a
        cache.get(Long, function) == b
        cache.getIfPresent(String) == a
        cache.getIfPresent(Long) == b

        cache.put(String, c)
        cache.getIfPresent(String) == c

        cache.clear()
        cache.getIfPresent(String) == null
    }

    def "creates a map whose keys are classes"() {
        def a = new Object()
        def b = new Object()
        def c = new Object()
        def function = Mock(Function)

        given:
        function.apply(String) >> a
        function.apply(Long) >> b

        def cache = factory.newClassMap()

        expect:
        cache.get(String, function) == a
        cache.get(Long, function) == b
        cache.getIfPresent(String) == a
        cache.getIfPresent(Long) == b

        cache.put(String, c)
        cache.getIfPresent(String) == c
    }

    @Issue("https://github.com/gradle/gradle/issues/18313")
    @Timeout(30)
    def "class cache releases a value WITHIN a session once its Class is unreachable"() {
        given:
        def cache = factory.newClassCache()
        // Deliberately NO beforeComplete()/clear(): the build session is still open. That single omission is
        // what makes this a within-session guard (the value must die with its Class), not a cross-session one.
        WeakReference<ClassLoader> loaderProbe = populate(cache)

        expect: "no session-lived strong reference pins the worker ClassLoader, so a GC reclaims it mid-session"
        ConcurrentTestUtil.poll(10) {
            System.gc()
            assert loaderProbe.get() == null
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/18313")
    @Timeout(30)
    def "class map releases a value WITHIN a session once its Class is unreachable"() {
        given:
        def cache = factory.newClassMap()
        WeakReference<ClassLoader> loaderProbe = populate(cache)

        expect:
        ConcurrentTestUtil.poll(10) {
            System.gc()
            assert loaderProbe.get() == null
        }
    }

    @TempDir
    File tempDir

    // Compiles a trivial pure-Java class and loads it in a fresh, isolated URLClassLoader (a pure-Java class avoids
    // Groovy's global ClassInfo/globalClassSet retention), then caches a value against it. Stays dynamic Groovy --
    // the JavaCompiler.run varargs call does not resolve under @CompileStatic -- but never dispatches a method ON
    // the throwaway Class; that reflection is isolated in cacheConstructor() below. Returns ONLY a weak probe on
    // the loader, so no strong local escapes to the caller.
    private WeakReference<ClassLoader> populate(CrossBuildInMemoryCache<Class<?>, Object> cache) {
        String name = "Throwaway"
        File src = new File(tempDir, "src" + System.nanoTime())
        File out = new File(tempDir, "out" + System.nanoTime())
        src.mkdirs()
        out.mkdirs()
        File javaFile = new File(src, name + ".java")
        javaFile.text = "public class " + name + " {}"
        assert ToolProvider.systemJavaCompiler.run(null, null, null, "-d", out.absolutePath, javaFile.absolutePath) == 0

        URLClassLoader loader = new URLClassLoader([out.toURI().toURL()] as URL[], getClass().classLoader)
        cacheConstructor(cache, loader.loadClass(name))
        return new WeakReference<ClassLoader>(loader)
    }

    // @CompileStatic so the reflective call on the throwaway Class compiles to a plain Java invocation. Under
    // dynamic Groovy, invoking a method on a Class instance registers a ClassInfo in the global
    // ClassInfo.globalClassSet, which would pin the class (and its loader) regardless of the cache under test and
    // defeat the collection this test asserts. The cached value (a Constructor) transitively strong-references its
    // declaring Class (the cache key), exactly like the value cached on the real worker path in issue #18313.
    @CompileStatic
    private static void cacheConstructor(CrossBuildInMemoryCache<Class<?>, Object> cache, Class<?> throwaway) {
        Function<Class<?>, Object> factory = { Class<?> k -> (Object) k.getDeclaredConstructors()[0] }
        cache.get(throwaway, factory)
    }
}
