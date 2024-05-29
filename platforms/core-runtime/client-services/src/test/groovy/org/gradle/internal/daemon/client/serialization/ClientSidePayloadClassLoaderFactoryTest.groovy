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

package org.gradle.internal.daemon.client.serialization

import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory
import spock.lang.Specification

class ClientSidePayloadClassLoaderFactoryTest extends Specification {
    def registry = new ClientSidePayloadClassLoaderFactory(Mock(PayloadClassLoaderFactory))

    def "creates ClassLoader for classpath"() {
        def url1 = new URL("http://localhost/file1.jar")
        def url2 = new URL("http://localhost/file2.jar")

        when:
        def cl = registry.getClassLoaderFor(new VisitableURLClassLoader.Spec("test", [url1, url2]), [null])

        then:
        cl instanceof VisitableURLClassLoader
        cl.name == "test-client-payload-loader"
        cl.URLs == [url1, url2] as URL[]
    }
}
