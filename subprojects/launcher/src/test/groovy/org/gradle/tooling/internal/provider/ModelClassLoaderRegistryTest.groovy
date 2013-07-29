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

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.internal.classloader.MutableURLClassLoader

import java.util.concurrent.CopyOnWriteArraySet

class ModelClassLoaderRegistryTest extends ConcurrentSpec {
    final ModelClassLoaderRegistry registry = new ModelClassLoaderRegistry()

    def "creates and caches ClassLoader for classpath"() {
        def url1 = new URL("http://localhost/file1.jar")
        def url2 = new URL("http://localhost/file2.jar")

        when:
        def cl = registry.getClassLoaderFor([url1, url2])

        then:
        cl instanceof MutableURLClassLoader
        cl.URLs == [url1, url2] as URL[]

        when:
        def cl2 = registry.getClassLoaderFor([url1, url2])

        then:
        cl2.is(cl)

        when:
        def cl3 = registry.getClassLoaderFor([url1])

        then:
        cl3 != cl
    }

    def "reuse of ClassLoader is thread-safe"() {
        def url1 = new URL("http://localhost/file1.jar")
        def url2 = new URL("http://localhost/file2.jar")

        def results = new CopyOnWriteArraySet()

        when:
        async {
            10.times {
                start {
                    10.times {
                        results << registry.getClassLoaderFor([url1, url2])
                    }
                }
            }
        }

        then:
        results.size() == 1
    }
}
