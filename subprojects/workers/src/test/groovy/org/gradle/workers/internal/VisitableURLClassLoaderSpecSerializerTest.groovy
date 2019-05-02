/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import spock.lang.Specification

class VisitableURLClassLoaderSpecSerializerTest extends Specification {
    def serializer = new VisitableURLClassLoaderSpecSerializer()
    def outputStream = new ByteArrayOutputStream()
    def encoder = new KryoBackedEncoder(outputStream)

    def "can serialize and deserialize a spec"() {
        def urls = [ new URL("file://some/path"), new URL("file://some/other/path") ]
        def spec = new VisitableURLClassLoader.Spec("test", urls)

        when:
        serializer.write(encoder, spec)
        encoder.flush()

        and:
        def decoder = new KryoBackedDecoder(new ByteArrayInputStream(outputStream.toByteArray()))
        def decodedSpec = serializer.read(decoder)

        then:
        decodedSpec == spec
    }
}
