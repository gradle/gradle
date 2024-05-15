/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.daemon.serialization

import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory
import org.junit.Rule
import spock.lang.Specification

class DaemonSidePayloadClassLoaderFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def factory = Mock(PayloadClassLoaderFactory)
    def classpathTransformer = Mock(CachedClasspathTransformer)

    def registry = new DaemonSidePayloadClassLoaderFactory(factory, classpathTransformer)

    def "creates ClassLoader for classpath"() {
        def url1 = new URL("http://localhost/file1.jar")
        def url2 = new URL("http://localhost/file2.jar")

        given:
        classpathTransformer.transform(_, _) >> [url1, url2]

        when:
        def cl = registry.getClassLoaderFor(new VisitableURLClassLoader.Spec("test", [url1, url2]), [null])

        then:
        cl instanceof VisitableURLClassLoader
        cl.URLs == [url1, url2] as URL[]
    }

    def "creates ClassLoader for jar classpath"() {
        def jarFile = tmpDir.createFile("file1.jar")
        def cachedJar = tmpDir.createFile("cached/file1.jar")
        def url1 = jarFile.toURI().toURL()
        def cached = cachedJar.toURI().toURL()
        def url2 = tmpDir.createDir("classes-dir").toURI().toURL()

        given:
        classpathTransformer.transform(_, _) >> [cached, url2]

        when:
        def cl = registry.getClassLoaderFor(new VisitableURLClassLoader.Spec("test", [url1, url2]), [null])

        then:
        cl instanceof VisitableURLClassLoader
        cl.URLs == [cached, url2] as URL[]
    }
}
