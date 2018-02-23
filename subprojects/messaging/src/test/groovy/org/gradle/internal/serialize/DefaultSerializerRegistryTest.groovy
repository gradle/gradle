/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.serialize

class DefaultSerializerRegistryTest extends SerializerSpec {
    def longSerializer = Stub(Serializer) {
        read(_) >> { Decoder decoder ->
            return decoder.readSmallLong()
        }
        write(_, _) >> { Encoder encoder, Long value ->
            encoder.writeSmallLong(value)
        }
    }
    def intSerializer = Stub(Serializer) {
        read(_) >> { Decoder decoder ->
            return decoder.readSmallInt()
        }
        write(_, _) >> { Encoder encoder, Integer value ->
            encoder.writeSmallInt(value)
        }
    }

    def "can query whether type can be serialized"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.register(Long, longSerializer)
        registry.register(Integer, intSerializer)
        registry.useJavaSerialization(StringBuilder)

        expect:
        registry.canSerialize(Long)
        registry.canSerialize(Number)
        registry.canSerialize(Object)
        !registry.canSerialize(BigDecimal)

        registry.canSerialize(StringBuilder)
        registry.canSerialize(CharSequence)
        !registry.canSerialize(String)
    }


    def "serializes type information with a value"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.register(Long, longSerializer)
        registry.register(Integer, intSerializer)
        def serializer = registry.build(Number)

        expect:
        serialize(123L, serializer) == 123L
        serialize(123, serializer) == 123
        toBytes(123L, serializer).length == toBytes(123L, longSerializer).length + 1
    }

    def "does not write type tag when there is only one matching registered type"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.register(Long, longSerializer)
        registry.register(Integer, intSerializer)
        def serializer1 = registry.build(Long)
        def serializer2 = registry.build(Number)

        expect:
        toBytes(123L, serializer1).length + 1 == toBytes(123L, serializer2).length
    }

    def "type information is independent of the order that types are registered"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.register(Long, longSerializer)
        registry.register(Integer, intSerializer)
        def serializer1 = registry.build(Number)

        and:
        registry = new DefaultSerializerRegistry()
        registry.register(Integer, intSerializer)
        registry.register(Long, longSerializer)
        def serializer2 = registry.build(Number)

        expect:
        fromBytes(toBytes(123L, serializer1), serializer2) == 123L
        fromBytes(toBytes(123, serializer1), serializer2) == 123
    }

    def "cannot write value with type that has not been registered"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.register(Long, longSerializer)
        registry.register(Integer, intSerializer)
        registry.useJavaSerialization(String)
        def serializer = registry.build(Object)

        when:
        toBytes(123.4, serializer)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Don't know how to serialize an object of type java.math.BigDecimal."
    }

    def "cannot get serializer when no matching types have been registered"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.register(Long, longSerializer)

        when:
        registry.build(String)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Don't know how to serialize objects of type java.lang.String."
    }

    def "uses serializer registered for Throwable for subtypes of Throwable"() {
        def failure = new IOException("broken")
        def throwableSerializer = Mock(Serializer)

        given:
        _ * throwableSerializer.write(_, failure)
        _ * throwableSerializer.read(_) >> failure
        def registry = new DefaultSerializerRegistry()
        registry.register(Throwable, throwableSerializer)
        def serializer = registry.build(Object)

        expect:
        serialize(failure, serializer) == failure
    }

    def "can use Java serialization for registered type"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.useJavaSerialization(Long)
        registry.useJavaSerialization(Integer)
        def serializer = registry.build(Number)

        expect:
        serialize(123L, serializer) == 123L
        serialize(123, serializer) == 123
    }

    def "can use Java serialization for subtypes of registered type"() {
        given:
        def registry = new DefaultSerializerRegistry()
        registry.useJavaSerialization(Number)
        def serializer = registry.build(Number)

        expect:
        serialize(123L, serializer) == 123L
        serialize(123, serializer) == 123
        serialize(123.4, serializer) == 123.4
    }

    def "custom serialization takes precedence over Java serialization"() {
        given:
        def customSerializer = Stub(Serializer) {
            read(_) >> { Decoder decoder ->
                return decoder.readSmallLong() + 1
            }
            write(_, _) >> { Encoder encoder, Long value ->
                encoder.writeSmallLong(value + 1)
            }
        }
        def registry = new DefaultSerializerRegistry()
        registry.useJavaSerialization(Number)
        registry.register(Long, customSerializer)
        def serializer = registry.build(Number)

        expect:
        serialize(123L, serializer) == 125L
        serialize(123, serializer) == 123
        serialize(123.4, serializer) == 123.4
    }
}
