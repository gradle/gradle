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

import static org.gradle.api.reflect.TypeOf.parameterizedTypeOf
import static org.gradle.api.reflect.TypeOf.typeOf

class TypeOfSpec extends Specification {

    static class InnerClass {}

    def "to string"() {
        expect:
        new TypeOf<InnerClass>() {}.toString() == 'org.gradle.api.reflect.TypeOfSpec.InnerClass'
        new TypeOf<List<InnerClass>>() {}.toString() == 'java.util.List<org.gradle.api.reflect.TypeOfSpec.InnerClass>'
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

    def "hashCode semantics"() {
        given:
        def a = new TypeOf() {}.hashCode()
        def b = new TypeOf<List>() {}.hashCode()
        def c = new TypeOf<List<String>>() {}.hashCode()

        expect:
        a != b
        b != c
        c != a

        and:
        a == new TypeOf() {}.hashCode()
        b == new TypeOf<List>() {}.hashCode()
        c == new TypeOf<List<String>>() {}.hashCode()
    }

    def "factory methods"() {
        expect:
        typeOf(String) == new TypeOf<String>() {}
        typeOf((Type) String) == new TypeOf<String>() {}
        parameterizedTypeOf(new TypeOf<List<?>>() {}, typeOf(String)) == new TypeOf<List<String>>() {}
        parameterizedTypeOf(new TypeOf<Map<?, ?>>() {}, typeOf(String), typeOf(Integer)) == new TypeOf<Map<String, Integer>>() {}
    }

    def "componentType is null on non-array types"() {
        expect:
        typeOf(String).componentType == null
        new TypeOf<List<String>>() {}.componentType == null
        new TypeOf<List<?>>() {}.actualTypeArguments[0].with { wildcard && componentType == null }
    }

    def "getFullyQualifiedName returns FQN"() {
        given:
        def stringType = typeOf(String)

        expect:
        stringType.simpleName == 'String'
        stringType.fullyQualifiedName == 'java.lang.String'
    }

    def "getFullyQualifiedName returns FQN striping generic arguments"() {
        given:
        def listType = typeOf(List<Integer>)

        expect:
        listType.simpleName == 'List'
        listType.fullyQualifiedName == 'java.util.List'
    }
}
