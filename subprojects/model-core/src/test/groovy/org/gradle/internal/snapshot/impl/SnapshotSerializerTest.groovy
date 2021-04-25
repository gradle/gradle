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

package org.gradle.internal.snapshot.impl

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.internal.snapshot.ValueSnapshot
import spock.lang.Specification
import spock.lang.Subject

class SnapshotSerializerTest extends Specification {

    def output = new ByteArrayOutputStream()
    def encoder = new OutputStreamBackedEncoder(output)

    @Subject
            serializer = new SnapshotSerializer()

    def "serializes serialized properties"() {
        def original = snapshot("x".bytes)
        write(original)

        expect:
        original == written
    }

    def "serializes string properties"() {
        def original = string("x")
        write(original)

        expect:
        original == written
    }

    def "serializes number properties"() {
        write(value)

        expect:
        value == written

        where:
        value                                   | _
        integer(123)                            | _
        integer(-123)                           | _
        integer(Integer.MAX_VALUE)              | _
        integer(Integer.MIN_VALUE)              | _
        new LongValueSnapshot(123L)             | _
        new LongValueSnapshot(0L)               | _
        new LongValueSnapshot(Long.MAX_VALUE)   | _
        new LongValueSnapshot(Long.MIN_VALUE)   | _
        new ShortValueSnapshot(123 as short)    | _
        new ShortValueSnapshot(0 as short)      | _
        new ShortValueSnapshot(Short.MAX_VALUE) | _
        new ShortValueSnapshot(Short.MIN_VALUE) | _
    }

    def "serializes hash properties"() {
        def value = new HashValueSnapshot(HashCode.fromInt(123))

        when:
        write(value)

        then:
        value == written
    }

    enum Thing {
        THING_1, THING_2
    }

    def "serializes enum properties"() {
        def original = new EnumValueSnapshot(Thing.THING_1)
        write(original)

        expect:
        original == written
    }

    def "serializes file properties"() {
        def original = new FileValueSnapshot(new File("abc"))
        write(original)

        expect:
        original == written
    }

    def "serializes null properties"() {
        def original = NullValueSnapshot.INSTANCE
        write(original)

        expect:
        original == written
    }

    def "serializes boolean properties"() {
        write(value)

        expect:
        value == written

        where:
        value                      | _
        BooleanValueSnapshot.TRUE  | _
        BooleanValueSnapshot.FALSE | _
    }

    def "serializes array properties"() {
        def original = array(string("123"), array(string("456")))
        write(original)

        expect:
        original == written
    }

    def "serializes empty array properties"() {
        def original = array()
        write(original)

        expect:
        original == written
    }

    def "serializes list properties"() {
        def original = list(string("123"), list(string("456")))
        write(original)

        expect:
        original == written
    }

    def "serializes empty list properties"() {
        def original = list()
        write(original)

        expect:
        original == written
    }

    def "serializes set properties"() {
        def original = set(string("123"), set(string("456")))
        write(original)

        expect:
        original == written
    }

    def "serializes map properties"() {
        def builder = ImmutableList.<MapEntrySnapshot<ValueSnapshot>>builder()
        builder.add(new MapEntrySnapshot<ValueSnapshot>(string("123"), integer(123)))
        builder.add(new MapEntrySnapshot<ValueSnapshot>(string("456"), list(integer(-12), integer(12))))
        def original = new MapValueSnapshot(builder.build())
        write(original)

        expect:
        original == written
    }

    def "serializes managed type properties"() {
        def original = new ManagedValueSnapshot("named", integer(123))
        write(original)

        expect:
        original == written
    }

    def "serializes immutable managed type properties"() {
        def original = new ImmutableManagedValueSnapshot("type", "name")
        write(original)

        expect:
        original == written
    }

    def "serializes implementation properties"() {
        def original = ImplementationSnapshot.of("someClassName", HashCode.fromString("0123456789"))
        write(original)

        expect:
        original == written
    }

    def "serializes implementation properties with unknown classloader"() {
        def original = ImplementationSnapshot.of("someClassName", null)
        write(original)

        expect:
        ImplementationSnapshot copy = written
        copy.typeName == original.typeName
        copy.classLoaderHash == null
        copy.unknown
        copy.unknownReason.contains("unknown classloader")
    }

    def "serializes implementation properties with lambda"() {
        def original = ImplementationSnapshot.of('someClassName$$Lambda$12/312454364', HashCode.fromInt(1234))
        write(original)

        expect:
        ImplementationSnapshot copy = written
        copy.typeName == original.typeName
        copy.classLoaderHash == null
        copy.isUnknown()
        copy.unknownReason.contains("lambda")
    }

    private ArrayValueSnapshot array(ValueSnapshot... elements) {
        return new ArrayValueSnapshot(ImmutableList.copyOf(elements))
    }

    private ListValueSnapshot list(ValueSnapshot... elements) {
        return new ListValueSnapshot(ImmutableList.copyOf(elements))
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

    private ValueSnapshot getWritten() {
        serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(output.toByteArray())))
    }

    private void write(ValueSnapshot snapshot) {
        serializer.write(encoder, snapshot)
    }
}
