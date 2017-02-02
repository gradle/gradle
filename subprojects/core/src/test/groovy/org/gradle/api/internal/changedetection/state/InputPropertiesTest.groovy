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

package org.gradle.api.internal.changedetection.state

import com.google.common.base.Objects
import spock.lang.Specification
import spock.lang.Unroll;

public class InputPropertiesTest extends Specification {

    def "creates a binary only wrapper for String input property"() {
        when:
        def property = InputProperties.create("foo")

        then:
        property instanceof BinaryInputProperty
    }

    def "creates a binary only wrapper for Boolean input property"() {
        when:
        def property = InputProperties.create(Boolean.FALSE)

        then:
        property instanceof BinaryInputProperty
    }

    def "creates a binary only wrapper for Number input property"() {
        when:
        def integerProperty = InputProperties.create(new Integer(42))
        def floatProperty = InputProperties.create(new Float(42.0f))
        def doubleProperty = InputProperties.create(new Double(42.0))
        def shortProperty = InputProperties.create(new Short("42"))
        def byteProperty = InputProperties.create(new Byte("42"))
        def bigDecimalProperty = InputProperties.create(BigDecimal.ONE)
        def bigIntegerProperty = InputProperties.create(BigInteger.TEN)

        then:
        integerProperty instanceof BinaryInputProperty
        floatProperty instanceof BinaryInputProperty
        doubleProperty instanceof BinaryInputProperty
        shortProperty instanceof BinaryInputProperty
        byteProperty instanceof BinaryInputProperty
        bigDecimalProperty instanceof BinaryInputProperty
        bigIntegerProperty instanceof BinaryInputProperty
    }

    def "creates a binary only wrapper for Collection input property of binary compatible type"() {
        when:
        def property = InputProperties.create([1, 2, 3, 4] as SortedSet<Integer>)

        then:
        property instanceof BinaryInputProperty
    }

    def "creates a binary only wrapper for Map input property of binary compatible type"() {
        when:
        def property = InputProperties.create(new TreeMap<Integer, String>([1:"1", 2:"2", 3:"3"]))

        then:
        property instanceof BinaryInputProperty
    }

    static class SerializableType implements Serializable, Comparable<SerializableType> {
        @Override
        int compareTo(SerializableType o) {
            return 0
        }
    }

    static class SerializableTypeWithCustomEquals implements Serializable, Comparable<SerializableTypeWithCustomEquals> {
        @Override
        int compareTo(SerializableTypeWithCustomEquals o) {
            return 0
        }

        @Override
        boolean equals(Object obj) {
            if (obj == null) {
                return false
            }
            return obj == this || obj.getClass() == getClass()
        }

        @Override
        int hashCode() {
            return Objects.hashCode()
        }
    }

    @Unroll
    def "create a #wrapperType.simpleName wrapper for custom #inputType.simpleName input property"() {
        when:
        def property = InputProperties.create(inputType.newInstance())

        then:
        wrapperType.isAssignableFrom(property.class)

        where:
        inputType                               | wrapperType
        SerializableType.class                  | DefaultInputProperty.class
        SerializableTypeWithCustomEquals.class  | DefaultInputProperty.class
    }

    @Unroll
    def "create a #wrapperType.simpleName wrapper for Collection input property of custom #inputType.simpleName"() {
        when:
        def property = InputProperties.create([inputType.newInstance()] as SortedSet<Object>)

        then:
        wrapperType.isAssignableFrom(property.class)

        where:
        inputType                               | wrapperType
        SerializableType.class                  | DefaultInputProperty.class
        SerializableTypeWithCustomEquals.class  | DefaultInputProperty.class
    }

    @Unroll
    def "create a #wrapperType.simpleName wrapper for Map input property of custom #inputType.simpleName"() {
        when:
        def property = InputProperties.create(new TreeMap<Integer, Object>([1: inputType.newInstance()]))

        then:
        wrapperType.isAssignableFrom(property.class)

        where:
        inputType                               | wrapperType
        SerializableType.class                  | DefaultInputProperty.class
        SerializableTypeWithCustomEquals.class  | DefaultInputProperty.class
    }

    def "create a DefaultInputProperty wrapper for Map input property with null value for first entry"() {
        when:
        def property = InputProperties.create(new TreeMap<Integer, Object>([1: null]))

        then:
        DefaultInputProperty.class.isAssignableFrom(property.class)
    }
}
