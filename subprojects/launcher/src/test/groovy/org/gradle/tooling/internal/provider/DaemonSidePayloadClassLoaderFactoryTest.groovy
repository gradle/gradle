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

package org.gradle.tooling.internal.provider

import org.gradle.internal.Factory
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DaemonSidePayloadClassLoaderFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def factory = Mock(PayloadClassLoaderFactory)
    def jarCache = Mock(JarCache)
    def cache = Stub(PersistentCache)
    def cacheBuilder = Stub(CacheBuilder) {
        open() >> cache
        withDisplayName(_) >> { cacheBuilder }
        withCrossVersionCache() >> { cacheBuilder }
        withLockOptions(_) >> { cacheBuilder }
    }
    def cacheRepository = Stub(CacheRepository) {
        cache(_) >> cacheBuilder
    }

    def registry = new DaemonSidePayloadClassLoaderFactory(factory, jarCache, cacheRepository)

    def "creates ClassLoader for classpath"() {
        def url1 = new URL("http://localhost/file1.jar")
        def url2 = new URL("http://localhost/file2.jar")

        when:
        def cl = registry.getClassLoaderFor(new MutableURLClassLoader.Spec([url1, url2]), [null])

        then:
        cl instanceof MutableURLClassLoader
        cl.URLs == [url1, url2] as URL[]
    }

    def "creates ClassLoader for jar classpath"() {
        def jarFile = tmpDir.createFile("file1.jar")
        def cachedJar = tmpDir.createFile("cached/file1.jar")
        def url1 = jarFile.toURI().toURL()
        def cached = cachedJar.toURI().toURL()
        def url2 = tmpDir.createDir("classes-dir").toURI().toURL()

        given:
        cache.useCache(_, _) >> { String display, Factory f -> f.create() }
        jarCache.getCachedJar(jarFile, _) >> cachedJar

        when:
        def cl = registry.getClassLoaderFor(new MutableURLClassLoader.Spec([url1, url2]), [null])

        then:
        cl instanceof MutableURLClassLoader
        cl.URLs == [cached, url2] as URL[]
    }
}
