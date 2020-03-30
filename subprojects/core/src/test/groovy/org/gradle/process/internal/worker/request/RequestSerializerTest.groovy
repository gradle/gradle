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
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import spock.lang.Specification

class RequestSerializerTest extends Specification {
    def outputStream = new ByteArrayOutputStream()
    def encoder = new KryoBackedEncoder(outputStream)
    def encoded = false
    def decoded = false

    def "can serialize and deserialize request"() {
        def request = new Request(new Foo("foo"), buildOperation())
        def serializer = new RequestSerializer(serializer(), false)

        when:
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

    def "can serialize request and skip arg on read"() {
        def request = new Request(new Foo("foo"), buildOperation())
        def serializer = new RequestSerializer(serializer(), true)

        when:
        serializer.write(encoder, request)
        encoder.flush()

        and:
        def decodedRequest = serializer.read(decoder())

        then:
        decodedRequest.arg == null

        and:
        encoded
        !decoded
    }

    def decoder() {
        return new KryoBackedDecoder(new ByteArrayInputStream(outputStream.toByteArray()))
    }

    boolean identical(Request decodedRequest, Request request) {
        assert decodedRequest.arg.class == request.arg.class
        assert decodedRequest.arg.text == request.arg.text
        assert decodedRequest.buildOperation.id == request.buildOperation.id
        return true
    }

    def buildOperation() {
        OperationIdentifier id = new OperationIdentifier(1234)
        return new DefaultBuildOperationRef(id, id)
    }

    def serializer() {
        return new Serializer<Foo>() {
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
    }

    static class Foo implements Serializable {
        String text

        Foo(String text) {
            this.text = text
        }
    }
}
