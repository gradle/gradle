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

package org.gradle.model.internal.core

import org.gradle.model.internal.type.ModelType
import org.gradle.util.Matchers
import spock.lang.Specification

class ModelTypeTest extends Specification {
    class Nested {}
    interface NestedInterface {}

    def "represents classes"() {
        expect:
        def type = ModelType.of(String)

        type.isClass()
        type.rawClass == String
        type.concreteClass == String

        type.toString() == String.name
        type.displayName == String.simpleName

        def nested = ModelType.of(Nested)
        nested.toString() == Nested.name
        nested.displayName == "ModelTypeTest.Nested"
    }

    def "class is equal to itself"() {
        expect:
        def type = ModelType.of(String)
        def same = ModelType.of(String)
        def different = ModelType.of(Number)

        Matchers.strictlyEqual(same)
        type != different
    }

    def "represents nested interfaces"() {
        def nestedInterface = ModelType.of(NestedInterface)

        expect:
        nestedInterface.isClass()
        nestedInterface.rawClass == NestedInterface
        nestedInterface.concreteClass == NestedInterface

        nestedInterface.toString() == NestedInterface.name
        nestedInterface.displayName == "ModelTypeTest.NestedInterface"
    }

    def "represents type variables"() {
        when:
        def type = new ModelType<Map<String, Map<Integer, Float>>>() {}

        then:
        !type.isClass()

        type.typeVariables[0] == ModelType.of(String)
        type.typeVariables[1] == new ModelType<Map<Integer, Float>>() {}
        type.typeVariables[1].typeVariables[0] == ModelType.of(Integer)
        type.typeVariables[1].typeVariables[1] == ModelType.of(Float)

        and:
        type.toString() == "java.util.Map<java.lang.String, java.util.Map<java.lang.Integer, java.lang.Float>>"
        type.displayName == "Map<String, Map<Integer, Float>>"
    }

    def "parameterized type is equal when its raw type and all type parameters are equal"() {
        expect:
        def type = new ModelType<List<Integer>>() {}
        def same = new ModelType<List<Integer>>() {}
        def raw = ModelType.of(List)
        def superType = new ModelType<Collection<Integer>>() {}
        def differentTypeParam = new ModelType<List<String>>() {}
        def upperBound = new ModelType<List<? extends Number>>() {}
        def lowerBound = new ModelType<List<? super Integer>>() {}

        Matchers.strictlyEquals(type, same)
        type != raw
        type != superType
        type != differentTypeParam
        type != upperBound
        type != lowerBound
    }

    def "generic type compatibility"() {
        def chars = new ModelType<List<CharSequence>>() {}
        def strings = new ModelType<List<String>>() {}
        def extendsChars = new ModelType<List<? extends CharSequence>>() {}
        def superStrings = new ModelType<List<? super String>>() {}

        expect:
        !chars.isAssignableFrom(strings)

        strings.isAssignableFrom(strings)
        !strings.isAssignableFrom(extendsChars)
        !strings.isAssignableFrom(superStrings)

        chars.isAssignableFrom(chars)
        !chars.isAssignableFrom(extendsChars)
        !chars.isAssignableFrom(superStrings)

        extendsChars.isAssignableFrom(chars)
        extendsChars.isAssignableFrom(strings)
        extendsChars.isAssignableFrom(extendsChars)
        !extendsChars.isAssignableFrom(superStrings)

        superStrings.isAssignableFrom(chars)
        superStrings.isAssignableFrom(strings)
        superStrings.isAssignableFrom(superStrings)
        !superStrings.isAssignableFrom(extendsChars)
    }

    def m1(List<? extends String> strings) {}

    def m2(List<? super String> strings) {}

    def m3(List<?> anything) {}

    def m4(List<? extends Object> objects) {}

    def "wildcards"() {
        def extendsString = ModelType.paramType(getClass().getDeclaredMethod("m1", List.class), 0).typeVariables[0]
        def superString = ModelType.paramType(getClass().getDeclaredMethod("m2", List.class), 0).typeVariables[0]
        def anything = ModelType.paramType(getClass().getDeclaredMethod("m3", List.class), 0).typeVariables[0]
        def objects = ModelType.paramType(getClass().getDeclaredMethod("m4", List.class), 0).typeVariables[0]

        expect:
        extendsString.wildcard
        superString.wildcard
        objects.wildcard
        anything.wildcard

        !extendsString.isClass()
        !superString.isClass()
        !objects.isClass()
        !anything.isClass()

        extendsString.upperBound == ModelType.of(String)
        extendsString.lowerBound == null

        superString.upperBound == null
        superString.lowerBound == ModelType.of(String)

        objects.upperBound == null
        objects.lowerBound == null

        anything.upperBound == null
        anything.lowerBound == null

        extendsString.toString() == "? extends java.lang.String"
        superString.toString() == "? super java.lang.String"
        objects.toString() == "?"
        anything.toString() == "?"

        extendsString.displayName == "? extends String"
        superString.displayName == "? super String"
        objects.displayName == "?"
        anything.displayName == "?"
    }

    def "upper bound wildcards are equal when upper bounds are equal"() {
        expect:
        def type = new ModelType<List<? extends Integer>>() {}
        def same = new ModelType<List<? extends Integer>>() {}
        def parameterized = new ModelType<List<? extends List<? extends Number>>>() {}
        def sameParameterized = new ModelType<List<? extends List<? extends Number>>>() {}
        def wildcard = new ModelType<List<?>>() {}
        def extendsObject = new ModelType<List<? extends Object>>() {}
        def notWildcard = new ModelType<List<List>>() {}
        def superType = new ModelType<List<? extends Number>>() {}

        Matchers.strictlyEquals(type, same)
        Matchers.strictlyEquals(wildcard, extendsObject)
        Matchers.strictlyEquals(parameterized, sameParameterized)
        type != wildcard
        type != parameterized
        type != extendsObject
        type != notWildcard
        type != superType
    }

    def "lower bound wildcards are equal when lower bounds are equal"() {
        expect:
        def type = new ModelType<List<? super Integer>>() {}
        def same = new ModelType<List<? super Integer>>() {}
        def parameterized = new ModelType<List<? super List<? extends Number>>>() {}
        def sameParameterized = new ModelType<List<? super List<? extends Number>>>() {}
        def notWildcard = new ModelType<List<List>>() {}
        def superType = new ModelType<List<? super Number>>() {}

        Matchers.strictlyEquals(type, same)
        Matchers.strictlyEquals(parameterized, sameParameterized)
        type != parameterized
        type != notWildcard
        type != superType
    }

    def "asSubtype"() {
        expect:
        ModelType.of(String).asSubtype(ModelType.of(String)) == ModelType.of(String)
        ModelType.of(String).asSubtype(ModelType.of(CharSequence)) == ModelType.of(String)
    }

    def "asSubtype failures"() {
        def extendsString = ModelType.paramType(getClass().getDeclaredMethod("m1", List.class), 0).typeVariables[0]
        def superString = ModelType.paramType(getClass().getDeclaredMethod("m2", List.class), 0).typeVariables[0]
        def anything = ModelType.paramType(getClass().getDeclaredMethod("m3", List.class), 0).typeVariables[0]

        when: ModelType.of(CharSequence).asSubtype(ModelType.of(String))
        then: thrown ClassCastException

        when: anything.asSubtype(superString)
        then: thrown IllegalStateException

        when: superString.asSubtype(anything)
        then: thrown IllegalStateException

        when: superString.asSubtype(extendsString)
        then: thrown IllegalStateException

        when: extendsString.asSubtype(superString)
        then: thrown IllegalStateException

        when: ModelType.of(String).asSubtype(anything)
        then: thrown IllegalArgumentException

        when: ModelType.of(String).asSubtype(extendsString)
        then: thrown IllegalArgumentException

        when: ModelType.of(String).asSubtype(superString)
        then: thrown IllegalArgumentException
    }

    def "has wildcards"() {
        expect:
        !ModelType.of(String).hasWildcardTypeVariables
        new ModelType<List<?>>() {}.hasWildcardTypeVariables
        new ModelType<List<? extends CharSequence>>() {}.hasWildcardTypeVariables
        new ModelType<List<? super CharSequence>>() {}.hasWildcardTypeVariables
        !new ModelType<List<List<String>>>() {}.hasWildcardTypeVariables
        new ModelType<List<List<?>>>() {}.hasWildcardTypeVariables
        new ModelType<List<List<List<?>>>>() {}.hasWildcardTypeVariables
        new ModelType<List<List<? super List<String>>>>() {}.hasWildcardTypeVariables
    }

    def "is raw of param type"() {
        expect:
        !new ModelType<List<?>>() {}.rawClassOfParameterizedType
        !new ModelType<List<String>>() {}.rawClassOfParameterizedType
        new ModelType<List>() {}.rawClassOfParameterizedType
    }
}
