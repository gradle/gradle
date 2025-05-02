/*
 * Copyright 2018 the original author or authors.
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

import java.util.concurrent.TimeUnit

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class ModelTypeTest extends Specification {
    class Nested {}
    interface NestedInterface {}

    def "represents classes"() {
        expect:
        def type = ModelType.of(String)

        type.isClass()
        type.rawClass == String
        type.concreteClass == String

        !type.wildcard

        type.toString() == String.name
        type.displayName == String.simpleName

        def nested = ModelType.of(Nested)
        nested.toString() == org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf(Nested)
        nested.displayName == "ModelTypeTest.Nested"
    }

    def "class is equal to itself"() {
        expect:
        def type = ModelType.of(String)
        def same = ModelType.of(String)
        def different = ModelType.of(Number)
        def rawList = ModelType.of(List)
        def list = new ModelType<List<String>>() {}

        Matchers.strictlyEquals(type, same)
        Matchers.strictlyEquals(rawList, rawList)
        type != different
        rawList != list
    }

    def "class can be assigned from itself or subtypes"() {
        expect:
        def type = ModelType.of(Number)
        def object = ModelType.of(Object)
        def intType = ModelType.of(Integer)
        def string = ModelType.of(String)
        def rawCollection = ModelType.of(Collection)
        def rawList = ModelType.of(List)
        def list = new ModelType<List<String>>() {}
        def extendsNumber = new ModelType<List<? extends Number>>() {}.typeVariables[0]
        def superNumber = new ModelType<List<? super Number>>() {}.typeVariables[0]
        def anything = new ModelType<List<?>>() {}.typeVariables[0]

        type.isAssignableFrom(type)
        type.isAssignableFrom(intType)
        type.isAssignableFrom(extendsNumber)
        !type.isAssignableFrom(superNumber)
        !type.isAssignableFrom(anything)

        !type.isAssignableFrom(object)
        !type.isAssignableFrom(string)

        rawCollection.isAssignableFrom(rawCollection)
        rawCollection.isAssignableFrom(rawList)
        rawCollection.isAssignableFrom(list)
        rawList.isAssignableFrom(list)
    }

    def "represents nested interfaces"() {
        def nestedInterface = ModelType.of(NestedInterface)

        expect:
        nestedInterface.isClass()
        nestedInterface.rawClass == NestedInterface
        nestedInterface.concreteClass == NestedInterface

        nestedInterface.toString() == org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf(NestedInterface)
        nestedInterface.displayName == "ModelTypeTest.NestedInterface"
    }

    def "represents parameterized types"() {
        when:
        def type = new ModelType<Map<String, Map<Integer, Float>>>() {}

        then:
        !type.isClass()
        type.rawClass == Map
        type.concreteClass == Map

        !type.wildcard

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
        def superChars = new ModelType<List<? super CharSequence>>() {}
        def anything = new ModelType<List<?>>() {}
        def collection = new ModelType<Collection<CharSequence>>() {}
        def extendsCollection = new ModelType<List<? extends Collection<? extends CharSequence>>>() {}.typeVariables[0]
        def stringMap = new ModelType<Map<String, String>>() {}
        def numberMap = new ModelType<Map<String, Number>>() {}
        def extendsCharsMap = new ModelType<Map<? extends CharSequence, ? extends CharSequence>>() {}
        def raw = ModelType.of(List)

        expect:
        strings.isAssignableFrom(strings)
        !strings.isAssignableFrom(extendsChars)
        !strings.isAssignableFrom(superStrings)
        !strings.isAssignableFrom(anything)
        !strings.isAssignableFrom(raw)
        !strings.isAssignableFrom(collection)

        chars.isAssignableFrom(chars)
        !chars.isAssignableFrom(strings)
        !chars.isAssignableFrom(extendsChars)
        !chars.isAssignableFrom(superStrings)
        !chars.isAssignableFrom(anything)
        !chars.isAssignableFrom(raw)
        !chars.isAssignableFrom(collection)

        extendsChars.isAssignableFrom(chars)
        extendsChars.isAssignableFrom(strings)
        extendsChars.isAssignableFrom(extendsChars)
        !extendsChars.isAssignableFrom(superStrings)
        !extendsChars.isAssignableFrom(anything)
        !extendsChars.isAssignableFrom(raw)
        !extendsChars.isAssignableFrom(collection)

        superStrings.isAssignableFrom(chars)
        superStrings.isAssignableFrom(strings)
        superStrings.isAssignableFrom(superStrings)
        superStrings.isAssignableFrom(superChars)
        !superStrings.isAssignableFrom(extendsChars)
        !superStrings.isAssignableFrom(anything)
        !superStrings.isAssignableFrom(raw)
        !superStrings.isAssignableFrom(collection)

        superChars.isAssignableFrom(chars)
        superChars.isAssignableFrom(superChars)
        !superChars.isAssignableFrom(superStrings)
        !superChars.isAssignableFrom(strings)
        !superChars.isAssignableFrom(raw)
        !superChars.isAssignableFrom(anything)

        anything.isAssignableFrom(chars)
        anything.isAssignableFrom(strings)
        anything.isAssignableFrom(extendsChars)
        anything.isAssignableFrom(anything)
        anything.isAssignableFrom(superChars)
        anything.isAssignableFrom(superStrings)
        anything.isAssignableFrom(raw)
        !anything.isAssignableFrom(collection)

        raw.isAssignableFrom(chars)
        raw.isAssignableFrom(strings)
        raw.isAssignableFrom(anything)
        raw.isAssignableFrom(raw)
        !raw.isAssignableFrom(collection)

        collection.isAssignableFrom(chars)
        !collection.isAssignableFrom(strings)
        !collection.isAssignableFrom(raw)

        extendsCollection.isAssignableFrom(chars)
        extendsCollection.isAssignableFrom(strings)
        extendsCollection.isAssignableFrom(collection)
        !extendsCollection.isAssignableFrom(anything)
        !extendsCollection.isAssignableFrom(raw)

        !stringMap.isAssignableFrom(numberMap)
        !stringMap.isAssignableFrom(extendsCharsMap)
        !stringMap.isAssignableFrom(strings)

        extendsCharsMap.isAssignableFrom(stringMap)
        !extendsCharsMap.isAssignableFrom(numberMap)
    }

    def "represents wildcards"() {
        def extendsString = new ModelType<List<? extends String>>() {}.typeVariables[0]
        def superString = new ModelType<List<? super String>>() {}.typeVariables[0]
        def anything = new ModelType<List<?>>() {}.typeVariables[0]
        def objects = new ModelType<List<? extends Object>>() {}.typeVariables[0]

        expect:
        extendsString.wildcard
        superString.wildcard
        objects.wildcard
        anything.wildcard

        !extendsString.isClass()
        !superString.isClass()
        !objects.isClass()
        !anything.isClass()

        extendsString.rawClass == String
        superString.rawClass == Object
        objects.rawClass == Object
        anything.rawClass == Object

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
        def type = new ModelType<List<? extends Integer>>() {}.typeVariables[0]
        def same = new ModelType<List<? extends Integer>>() {}.typeVariables[0]
        def parameterized = new ModelType<List<? extends List<? extends Number>>>() {}.typeVariables[0]
        def sameParameterized = new ModelType<List<? extends List<? extends Number>>>() {}.typeVariables[0]
        def wildcard = new ModelType<List<?>>() {}.typeVariables[0]
        def extendsObject = new ModelType<List<? extends Object>>() {}.typeVariables[0]
        def notWildcard = new ModelType<List<List>>() {}.typeVariables[0]
        def superType = new ModelType<List<? extends Number>>() {}.typeVariables[0]

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
        def type = new ModelType<List<? super Integer>>() {}.typeVariables[0]
        def same = new ModelType<List<? super Integer>>() {}.typeVariables[0]
        def parameterized = new ModelType<List<? super List<? extends Number>>>() {}.typeVariables[0]
        def sameParameterized = new ModelType<List<? super List<? extends Number>>>() {}.typeVariables[0]
        def notWildcard = new ModelType<List<List>>() {}.typeVariables[0]
        def superType = new ModelType<List<? super Number>>() {}.typeVariables[0]

        Matchers.strictlyEquals(type, same)
        Matchers.strictlyEquals(parameterized, sameParameterized)
        type != parameterized
        type != notWildcard
        type != superType
    }

    def "wildcard assignment"() {
        expect:
        def extendsNumber = new ModelType<List<? extends Number>>() {}.typeVariables[0]
        def extendsInt = new ModelType<List<? extends Integer>>() {}.typeVariables[0]
        def superNumber = new ModelType<List<? super Number>>() {}.typeVariables[0]
        def anything = new ModelType<List<?>>() {}.typeVariables[0]
        def object = ModelType.of(Object)
        def number = ModelType.of(Number)
        def intType = ModelType.of(Integer)

        object.isAssignableFrom(extendsNumber)
        number.isAssignableFrom(extendsNumber)
        !intType.isAssignableFrom(extendsNumber)

        object.isAssignableFrom(superNumber)
        !number.isAssignableFrom(superNumber)
        !intType.isAssignableFrom(superNumber)

        object.isAssignableFrom(anything)
        !number.isAssignableFrom(anything)
        !intType.isAssignableFrom(anything)

        extendsNumber.isAssignableFrom(number)
        extendsNumber.isAssignableFrom(intType)
        extendsNumber.isAssignableFrom(extendsInt)
        !extendsNumber.isAssignableFrom(object)
        !extendsNumber.isAssignableFrom(superNumber)
        !extendsNumber.isAssignableFrom(anything)

        superNumber.isAssignableFrom(number)
        superNumber.isAssignableFrom(object)
        !superNumber.isAssignableFrom(intType)
        !superNumber.isAssignableFrom(extendsNumber)
        !superNumber.isAssignableFrom(extendsInt)
        !superNumber.isAssignableFrom(anything)

        !extendsInt.isAssignableFrom(extendsNumber)

        anything.isAssignableFrom(number)
        anything.isAssignableFrom(object)
        anything.isAssignableFrom(extendsNumber)
        anything.isAssignableFrom(superNumber)
    }

    def "represents arrays"() {
        def cl = List[].class

        expect:
        def listArray = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m2", cl), 0)

        !listArray.isClass()
        listArray.rawClass == cl

        !listArray.wildcard

        listArray.toString() == 'java.util.List<java.lang.String>[]'
        listArray.getDisplayName() == 'List<String>[]'
    }

    def "arrays are equal when component types are equal"() {
        expect:
        def listArray = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m2", List[].class), 0)
        def rawArray = ModelType.of(List[].class)
        def numberListArray = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m4", List[].class), 0)

        Matchers.strictlyEquals(listArray, listArray)
        listArray != rawArray
        listArray != numberListArray
    }

    def "array compatibility"() {
        expect:
        def listArray = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m2", List[].class), 0)
        def rawArray = ModelType.of(List[].class)
        def numberListArray = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m4", List[].class), 0)
        def collectionArray = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m5", Collection[].class), 0)

        listArray.isAssignableFrom(listArray)
        !listArray.isAssignableFrom(rawArray)
        !listArray.isAssignableFrom(numberListArray)
        !listArray.isAssignableFrom(collectionArray)

        rawArray.isAssignableFrom(listArray)
        rawArray.isAssignableFrom(rawArray)
        rawArray.isAssignableFrom(numberListArray)
        !rawArray.isAssignableFrom(collectionArray)

        numberListArray.isAssignableFrom(numberListArray)
        !numberListArray.isAssignableFrom(rawArray)
        !numberListArray.isAssignableFrom(listArray)

        collectionArray.isAssignableFrom(listArray)
        !collectionArray.isAssignableFrom(rawArray)
        !collectionArray.isAssignableFrom(numberListArray)
    }

    def "represents type variables"() {
        expect:
        def extendsNumber = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m1", Number.class, Number.class, Object.class), 0)
        def extendsTypeVar = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m1", Number.class, Number.class, Object.class), 1)
        def extendsTypeVarWithMultipleBounds = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m1", Number.class), 0)
        def anything = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m1", Number.class, Number.class, Object.class), 2)

        !extendsNumber.isClass()

        extendsNumber.rawClass == Number
        extendsTypeVar.rawClass == Number
        extendsTypeVarWithMultipleBounds.rawClass == Number
        anything.rawClass == Object

        extendsNumber.toString() == 'T'
        extendsNumber.getDisplayName() == 'T'
        extendsTypeVar.toString() == 'S'
        extendsTypeVar.getDisplayName() == 'S'
        anything.toString() == 'R'
        anything.getDisplayName() == 'R'

        extendsNumber.wildcard
        extendsNumber.upperBound == ModelType.of(Number)
        extendsNumber.lowerBound == null

        extendsTypeVar.wildcard
        extendsTypeVar.upperBound == extendsNumber // TODO - should be Number
        extendsTypeVar.lowerBound == null

        extendsTypeVarWithMultipleBounds.wildcard
        extendsTypeVarWithMultipleBounds.upperBound != null // TODO - should be Number
        extendsTypeVarWithMultipleBounds.lowerBound == null

        anything.wildcard
        anything.upperBound == null
        anything.lowerBound == null
    }

    def "type variable compatibility"() {
        expect:
        def extendsNumber = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m1", Number.class, Number.class, Object.class), 0)
        def extendsTypeVar = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m1", Number.class, Number.class, Object.class), 1)
        def anything = ModelType.paramType(ModelTypeJavaTest.getDeclaredMethod("m1", Number.class, Number.class, Object.class), 2)
        def object = ModelType.of(Object)
        def number = ModelType.of(Number)
        def intType = ModelType.of(Integer)

        object.isAssignableFrom(extendsNumber)
        number.isAssignableFrom(extendsNumber)
        !intType.isAssignableFrom(extendsNumber)

        object.isAssignableFrom(extendsTypeVar)
        number.isAssignableFrom(extendsTypeVar)
        !intType.isAssignableFrom(extendsTypeVar)

        object.isAssignableFrom(anything)
        !number.isAssignableFrom(anything)
        !intType.isAssignableFrom(anything)

        extendsNumber.isAssignableFrom(extendsNumber)
        !extendsNumber.isAssignableFrom(number)
        !extendsNumber.isAssignableFrom(intType)
        !extendsNumber.isAssignableFrom(extendsTypeVar)

        !extendsTypeVar.isAssignableFrom(number)
        extendsTypeVar.isAssignableFrom(extendsTypeVar)
//        extendsTypeVar.isAssignableFrom(extendsNumber) TODO - needs fixing

        !anything.isAssignableFrom(number)
        !anything.isAssignableFrom(extendsNumber)
        !anything.isAssignableFrom(extendsTypeVar)
    }

    def "asSubtype"() {
        expect:
        ModelType.of(String).asSubtype(ModelType.of(String)) == ModelType.of(String)
        ModelType.of(String).asSubtype(ModelType.of(CharSequence)) == ModelType.of(String)
    }

    def "asSubtype failures"() {
        def extendsString = new ModelType<List<? extends String>>() {}.typeVariables[0]
        def superString = new ModelType<List<? super String>>() {}.typeVariables[0]
        def anything = new ModelType<List<?>>() {}.typeVariables[0]

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
        ModelType.of(List).rawClassOfParameterizedType

        !ModelType.of(String).rawClassOfParameterizedType
        !new ModelType<List<?>>() {}.rawClassOfParameterizedType
        !new ModelType<List<String>>() {}.rawClassOfParameterizedType
        !new ModelType<List<? super String>>() {}.typeVariables[0].rawClassOfParameterizedType
    }

    enum MyEnum {
        ONE, TWO
    }

    enum MyEnumWithClassBodies {
        ONE {
            int execute() { return 1 }
        },
        TWO {
            int execute() { return 2 }
        }

        abstract int execute()
    }

    def "represents enums"() {
        given:
        def enumType = MyEnum.class
        def enumInstanceType = MyEnum.ONE.class

        expect:
        def modelOfEnum = ModelType.of(enumType)
        def modelOfInstance = ModelType.of(enumInstanceType)
        modelOfEnum == modelOfInstance
    }

    def "represents top level enums having constants with class body"() {
        given:
        def enumType = TimeUnit.class
        def enumInstanceType = TimeUnit.SECONDS.class

        expect:
        def modelOfEnum = ModelType.of(enumType)
        def modelOfInstance = ModelType.of(enumInstanceType)
        modelOfInstance.displayName == 'TimeUnit'
        modelOfEnum.toString() == 'java.util.concurrent.TimeUnit'
        modelOfEnum == modelOfInstance
    }

    def "represents nested enums having constants with class body"() {
        given:
        def enumType = MyEnumWithClassBodies.class
        def enumInstanceType = MyEnumWithClassBodies.TWO.class

        expect:
        def modelOfEnum = ModelType.of(enumType)
        def modelOfInstance = ModelType.of(enumInstanceType)
        modelOfInstance.displayName == 'ModelTypeTest.MyEnumWithClassBodies'
        modelOfEnum.toString() == 'org.gradle.model.internal.core.ModelTypeTest.MyEnumWithClassBodies'
        modelOfEnum == modelOfInstance
    }
}
