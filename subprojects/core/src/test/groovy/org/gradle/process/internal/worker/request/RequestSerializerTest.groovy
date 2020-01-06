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

package org.gradle.process.internal.worker.request

import org.gradle.internal.operations.DefaultBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerRegistry
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import spock.lang.Specification

class RequestSerializerTest extends Specification {
    List<SerializerRegistry> registries = []
    def serializer = new RequestSerializer(this.getClass().getClassLoader(), registries)
    def outputStream = new ByteArrayOutputStream()
    def encoder = new KryoBackedEncoder(outputStream)
    def encoded = false
    def decoded = false

    def "can serialize and deserialize request without registry"() {
        def request = new Request("foo", [Foo.class, Bar.class] as Class<?>[], [new Foo("foo"), new Bar("bar")] as Object[], buildOperation())

        when:
        serializer.write(encoder, request)
        encoder.flush()

        and:
        def decodedRequest = serializer.read(decoder())

        then:
        identical(decodedRequest, request)

        and:
        !encoded
        !decoded
    }

    def "can serialize and deserialize request with a registry"() {
        def request = new Request("foo", [Foo.class, Bar.class] as Class<?>[], [new Foo("foo"), new Bar("bar")] as Object[], buildOperation())

        when:
        registries.add(registry())
        serializer.write(encoder, request)
        encoder.flush()

        and:
        def decodedRequest = serializer.read(decoder())

        then:
        identical(decodedRequest, request)

        and:
        encoded
        decoded
    }

    def decoder() {
        return new KryoBackedDecoder(new ByteArrayInputStream(outputStream.toByteArray()))
    }

    boolean identical(Request decodedRequest, Request request) {
        assert decodedRequest.methodName == request.methodName
        assert decodedRequest.paramTypes == request.paramTypes
        assert decodedRequest.args.size() == request.args.size()
        assert decodedRequest.args.collect { it.text } == request.args.collect { it.text }
        assert decodedRequest.buildOperation.id == request.buildOperation.id
        return true
    }

    def buildOperation() {
        OperationIdentifier id = new OperationIdentifier(1234)
        return new DefaultBuildOperationRef(id, id)
    }

    def registry() {
        def fooSerializer = new Serializer<Foo>() {
            @Override
            void write(Encoder encoder, Foo value) throws Exception {
                encoded = true
                encoder.writeString(value.text)
            }

            @Override
            Foo read(Decoder decoder) throws EOFException, Exception {
                decoded = true
                return new Foo(decoder.readString())
            }
        }
        SerializerRegistry registry = new DefaultSerializerRegistry()
        registry.register(Foo.class, fooSerializer)
        return registry
    }

    static class Foo implements Serializable {
        String text

        Foo(String text) {
            this.text = text
        }
    }

    static class Bar implements Serializable {
        String text

        Bar(String text) {
            this.text = text
        }
    }
}
