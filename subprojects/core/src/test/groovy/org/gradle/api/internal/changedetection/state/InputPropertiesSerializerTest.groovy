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

package org.gradle.api.internal.changedetection.state

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.hash.HashCode
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import spock.lang.Specification
import spock.lang.Subject

class InputPropertiesSerializerTest extends Specification {

    def output = new ByteArrayOutputStream()
    def encoder = new OutputStreamBackedEncoder(output)

    @Subject serializer = new InputPropertiesSerializer()

    def "serializes empty properties"() {
        write [:]
        expect:
        [:] == written
    }

    def "serializes properties"() {
        def original = [a: snapshot("x".bytes), b: snapshot("y".bytes)]
        write(original)

        expect:
        original == written
    }

    def "serializes string properties"() {
        def original = [a: string("x"), b: string("y")]
        write(original)

        expect:
        original == written
    }

    def "serializes number properties"() {
        def original = [a: a, b: b]
        write(original)

        expect:
        original == written

        where:
        a                                       | b
        integer(123)                            | integer(-123)
        integer(Integer.MAX_VALUE)              | integer(Integer.MIN_VALUE)
        new LongValueSnapshot(123L)             | new LongValueSnapshot(0L)
        new LongValueSnapshot(Long.MAX_VALUE)   | new LongValueSnapshot(Long.MIN_VALUE)
        new ShortValueSnapshot(123 as short)    | new ShortValueSnapshot(0 as short)
        new ShortValueSnapshot(Short.MAX_VALUE) | new ShortValueSnapshot(Short.MIN_VALUE)
    }

    enum Thing {
        THING_1, THING_2
    }

    def "serializes enum properties"() {
        def original = [a: new EnumValueSnapshot(Thing.THING_1), b: new EnumValueSnapshot(Thing.THING_2)]
        write(original)

        expect:
        original == written
    }

    def "serializes file properties"() {
        def original = [a: new FileValueSnapshot(new File("abc")), b: new FileValueSnapshot(new File("123").getAbsoluteFile())]
        write(original)

        expect:
        original == written
    }

    def "serializes null properties"() {
        def original = [a: NullValueSnapshot.INSTANCE, b: NullValueSnapshot.INSTANCE]
        write(original)

        expect:
        original == written
    }

    def "serializes boolean properties"() {
        def original = [a: BooleanValueSnapshot.TRUE, b: BooleanValueSnapshot.FALSE]
        write(original)

        expect:
        original == written
    }

    def "serializes array properties"() {
        def original = [a: array(string("123"), string("456")), b: array(array(string("123")))]
        write(original)

        expect:
        original == written
    }

    def "serializes empty array properties"() {
        def original = [a: array(), b: array()]
        write(original)

        expect:
        original == written
    }

    def "serializes list properties"() {
        def original = [a: list(string("123"), string("456")), b: list(list(string("123")))]
        write(original)

        expect:
        original == written
    }

    def "serializes empty list properties"() {
        def original = [a: list(), b: list()]
        write(original)

        expect:
        original == written
    }

    def "serializes set properties"() {
        def original = [a: set(string("123"), string("456")), b: set(set(string("123"))), c: set()]
        write(original)

        expect:
        original == written
    }

    def "serializes map properties"() {
        def builder = ImmutableMap.builder()
        def empty = builder.build()
        builder.put(string("123"), integer(123))
        def original = [a: new MapValueSnapshot(builder.build()), b: new MapValueSnapshot(empty)]
        write(original)

        expect:
        original == written
    }

    private ArrayValueSnapshot array(ValueSnapshot... elements) {
        return new ArrayValueSnapshot(elements)
    }

    private ListValueSnapshot list(ValueSnapshot... elements) {
        return new ListValueSnapshot(elements)
    }

    private SetValueSnapshot set(ValueSnapshot... elements) {
        return new SetValueSnapshot(ImmutableSet.copyOf(elements))
    }

    private IntegerValueSnapshot integer(int value) {
        return new IntegerValueSnapshot(value)
    }

    private StringValueSnapshot string(String value) {
        return new StringValueSnapshot(value)
    }

    private SerializedValueSnapshot snapshot(byte[] value) {
        return new SerializedValueSnapshot(HashCode.fromInt(123), value)
    }

    private Map<String, ValueSnapshot> getWritten() {
        serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(output.toByteArray())))
    }

    private void write(Map<String, ValueSnapshot> map) {
        serializer.write(encoder, ImmutableMap.copyOf(map))
    }
}
