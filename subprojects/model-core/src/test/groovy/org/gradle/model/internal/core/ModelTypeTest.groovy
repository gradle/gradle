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
import spock.lang.Specification

class ModelTypeTest extends Specification {
    class Nested {}

    def "represents classes"() {
        expect:
        def type = ModelType.of(String)
        type.toString() == String.name
        type.simpleName == String.simpleName

        def nested = ModelType.of(Nested)
        nested.toString() == Nested.name
        nested.simpleName == "ModelTypeTest.Nested"
    }

    def "represents type variables"() {
        when:
        def type = new ModelType<Map<String, Map<Integer, Float>>>() {}

        then:
        type.typeVariables[0] == ModelType.of(String)
        type.typeVariables[1] == new ModelType<Map<Integer, Float>>() {}
        type.typeVariables[1].typeVariables[0] == ModelType.of(Integer)
        type.typeVariables[1].typeVariables[1] == ModelType.of(Float)

        and:
        type.toString() == "java.util.Map<java.lang.String, java.util.Map<java.lang.Integer, java.lang.Float>>"
        type.simpleName == "Map<String, Map<Integer, Float>>"
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

        extendsString.simpleName == "? extends String"
        superString.simpleName == "? super String"
        objects.simpleName == "?"
        anything.simpleName == "?"
    }

    def "isSubclass"() {
        def extendsString = ModelType.paramType(getClass().getDeclaredMethod("m1", List.class), 0).typeVariables[0]
        def superString = ModelType.paramType(getClass().getDeclaredMethod("m2", List.class), 0).typeVariables[0]
        def anything = ModelType.paramType(getClass().getDeclaredMethod("m3", List.class), 0).typeVariables[0]

        expect:
        !ModelType.of(String).asSubclass(ModelType.of(String))
        ModelType.of(CharSequence).asSubclass(ModelType.of(String))
        !ModelType.of(String).asSubclass(ModelType.of(CharSequence))
        !anything.asSubclass(superString)
        !superString.asSubclass(anything)
        !superString.asSubclass(extendsString)
        !extendsString.asSubclass(superString)
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
