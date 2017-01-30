/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.reflect

import spock.lang.Specification

import java.lang.reflect.Type

class TypeOfTest extends Specification {

    def "to string"() {
        expect:
        new TypeOf<String>() {}.toString() == 'java.lang.String'
        new TypeOf<List<String>>() {}.toString() == 'java.util.List<java.lang.String>'
        new TypeOf() {}.toString() == 'java.lang.Object'
    }

    def "simple generic name"() {
        expect:
        new TypeOf<String>() {}.simpleName == 'String'
        new TypeOf<List<String>>() {}.simpleName == 'List<String>'
        new TypeOf() {}.simpleName == 'Object'
    }

    def "equality"() {
        given:
        def a = new TypeOf() {}
        def b = new TypeOf<List>() {}
        def c = new TypeOf<List<String>>() {}

        expect:
        a != b
        b != c
        c != a

        and:
        a == new TypeOf() {}
        b == new TypeOf<List>() {}
        c == new TypeOf<List<String>>() {}
    }

    def "factory methods"() {
        expect:
        TypeOf.of(String) == new TypeOf<String>() {}
        TypeOf.of((Type) String) == new TypeOf<String>() {}
    }

    def "can extract type arguments"() {
        given:
        def mapType = new TypeOf<Map<String, List<Integer>>>() {}

        expect:
        mapType.typeArguments == [String, new TypeOf<List<Integer>>() {}.type]
        mapType.typeOfArguments[1].typeArguments[0] == Integer
    }

    def "can extract type of arguments"() {
        given:
        def mapType = new TypeOf<Map<String, List<Integer>>>() {}

        expect:
        mapType.typeOfArguments == [TypeOf.of(String), new TypeOf<List<Integer>>() {}]
        mapType.typeOfArguments[1].typeArguments[0] == Integer
    }

    def "can extract raw type arguments"() {
        given:
        def mapType = new TypeOf<Map<String, List<Integer>>>() {}

        expect:
        mapType.rawType == Map
        mapType.rawTypeArguments == [String, List]
        mapType.typeOfArguments[1].rawTypeArguments[0] == Integer
    }
}
