/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.util

import com.google.common.base.Charsets
import com.google.common.hash.Hasher
import org.apache.commons.lang.SerializationUtils
import spock.lang.Specification

class HasherUtilTest extends Specification {
    Hasher hasher = Mock(Hasher)

    def "hash boolean value as the number of byte (1) and data during putBoolean"() {
        given:
        boolean toSerialize = true

        when:
        HasherUtil.putBoolean(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize)
            0 * hasher._
        }
    }

    def "hash byte value as the number of byte (1) and data during putByte"() {
        given:
        byte toSerialize = 42

        when:
        HasherUtil.putByte(hasher, toSerialize)

        then:
        interaction {
            expectHashingOf(toSerialize)
            0 * hasher._
        }
    }

    def "hash array of byte as the number byte (5) and data during putBytes"() {
        given:
        byte[] toSerialize = [1, 2, 3, 4, 5]

        when:
        HasherUtil.putBytes(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize)
            0 * hasher._
        }
    }

    def "hash sub-array of byte as the number of byte (4) and data during putBytes"() {
        given:
        byte[] toSerialize = [1, 2, 3, 4, 5]
        int offset = 2
        int length = 4

        when:
        HasherUtil.putBytes(hasher, toSerialize, offset, length);

        then:
        interaction {
            expectHashingOf(toSerialize, offset, length)
            0 * hasher._
        }
    }

    def "hash double value as the number of byte (8) and data during putDouble"() {
        given:
        double toSerialize = 4.2

        when:
        HasherUtil.putDouble(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize)
            0 * hasher._
        }
    }

    def "hash integer value as the number of byte (4) and data during putInt"() {
        given:
        int toSerialize = 42

        when:
        HasherUtil.putInt(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize)
            0 * hasher._
        }
    }

    def "hash long value as the number of byte (8) and data during putLong"() {
        given:
        long toSerialize = 42

        when:
        HasherUtil.putLong(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf((long)toSerialize)
            0 * hasher._
        }
    }


    def "hash string as it's length and data during putString"() {
        given:
        String toSerialize = "42"

        when:
        HasherUtil.putString(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize)
            0 * hasher._
        }
    }

    def "hash a null object as '\$NULL' string during putObject"() {
        given:
        Object toSerialize = null

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOfNull()
            0 * hasher._
        }
    }

    def "hash an empty array as only the 'Array' header string during putObject"() {
        given:
        Object[] toSerialize = new Object[0]

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOfArrayHeader()
            0 * hasher._
        }
    }

    def "hash an array as the 'Array' header string, element index and element value during putObject"() {
        given:
        Integer item = new Integer(42)
        Integer[] toSerialize = [item]

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOfArrayHeader()
            expectHashingOf(0)
            expectHashingOf(item.intValue())
            0 * hasher._
        }
    }

    def "hash an empty collection as only the 'Iterable' header string during putObject"() {
        given:
        List<Integer> toSerialize = []

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOfIterableHeader()
            0 * hasher._
        }
    }

    def "hash a collection as the 'Iterable' header string, element index and element value during putObject"() {
        given:
        Integer item = new Integer(42)
        List<Integer> toSerialize = [item]

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOfIterableHeader()
            expectHashingOf(0)
            expectHashingOf(item.intValue())
            0 * hasher._
        }
    }

    def "hash an empty map as only the 'Map' header string during putObject"() {
        given:
        Map<Integer, String> toSerialize = [:]

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOfMapHeader()
            0 * hasher._
        }
    }

    def "hash a map as the 'Map' header string, entry index, entry key and entry value during putObject"() {
        given:
        Integer key = new Integer(42)
        String value = "42"
        Map<Integer, String> toSerialize = new HashMap<Integer, String>()
        toSerialize.put(key, value)

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOfMapHeader()
            expectHashingOf(0)
            expectHashingOf(key.intValue())
            expectHashingOf(value)
            0 * hasher._
        }
    }

    def "hash Boolean object as the number of byte (1) and data during putObject"() {
        given:
        Boolean toSerialize = Boolean.FALSE

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize.booleanValue())
            0 * hasher._
        }
    }

    def "hash Integer object as the number of byte (4) and data during putObject"() {
        given:
        Integer toSerialize = new Integer(42)

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize.intValue())
            0 * hasher._
        }
    }

    def "hash Short object as the number of byte (2) and data during putObject"() {
        given:
        Short toSerialize = new Short((short)42)

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize.shortValue())
            0 * hasher._
        }
    }

    def "hash Double object as the number of byte (8) and data during putObject"() {
        given:
        Double toSerialize = new Double(4.2)

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize.doubleValue())
            0 * hasher._
        }
    }

    def "hash Float object as the number of byte (8) and data during putObject"() {
        given:
        Double toSerialize = new Double(4.2f)

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize.floatValue())
            0 * hasher._
        }
    }

    def "hash BigInteger object as an array of bytes during putObject"() {
        given:
        BigInteger toSerialize = BigInteger.TEN

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize.toByteArray())
            0 * hasher._
        }
    }

    def "hash String object as CharSequence during putObject"() {
        given:
        String toSerialize = "42"

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize)
            0 * hasher._
        }
    }

    private static enum FooEnum {
        FOO
    }

    def "hash Enum object as the enum class name and enum name during putObject"() {
        given:
        FooEnum toSerialize = FooEnum.FOO

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(toSerialize.getClass().getName())
            expectHashingOf(toSerialize.name())
            0 * hasher._
        }
    }

    private static class FooSerializable implements Serializable {}

    def "hash Serializable object as an array of bytes during putObject"() {
        given:
        Serializable toSerialize = new FooSerializable()

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        interaction {
            expectHashingOf(SerializationUtils.serialize(toSerialize))
            0 * hasher._
        }
    }

    private static class Foo {}

    def "hash a non-Serializable object throws NotSerializableException during putObject"() {
        given:
        Foo toSerialize = new Foo()

        when:
        HasherUtil.putObject(hasher, toSerialize);

        then:
        thrown(NotSerializableException)
        0 * hasher._
    }

    private void expectHashingOf(byte value) {
        1 * hasher.putInt(1)
        1 * hasher.putByte(value)
    }

    private void expectHashingOf(byte[] value) {
        1 * hasher.putInt(value.length)
        1 * hasher.putBytes(value)
    }

    private void expectHashingOf(byte[] bytes, int off, int len) {
        1 * hasher.putInt(len)
        1 * hasher.putBytes(bytes, off, len)
    }

    private void expectHashingOf(int value) {
        1 * hasher.putInt(4)
        1 * hasher.putInt(value)
    }

    private void expectHashingOf(long value) {
        1 * hasher.putInt(8)
        1 * hasher.putLong(value)
    }

    private void expectHashingOf(double value) {
        1 * hasher.putInt(8)
        1 * hasher.putDouble(value)
    }

    private void expectHashingOf(boolean value) {
        1 * hasher.putInt(1)
        1 * hasher.putBoolean(value)
    }

    private void expectHashingOf(CharSequence value) {
        1 * hasher.putInt(value.length())
        1 * hasher.putString(value, Charsets.UTF_8)
    }

    private void expectHashingOfNull() {
        expectHashingOf("\$NULL")
    }

    private void expectHashingOfArrayHeader() {
        expectHashingOf("Array")
    }

    private void expectHashingOfIterableHeader() {
        expectHashingOf("Iterable")
    }

    private void expectHashingOfMapHeader() {
        expectHashingOf("Map")
    }
}
