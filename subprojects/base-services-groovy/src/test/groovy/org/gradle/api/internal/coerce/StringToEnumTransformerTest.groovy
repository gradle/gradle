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

package org.gradle.api.internal.coerce

import org.codehaus.groovy.reflection.CachedClass
import spock.lang.Specification

@SuppressWarnings("GroovyUnusedDeclaration")
class StringToEnumTransformerTest extends Specification {

    def transformer = new StringToEnumTransformer()

    static enum TestEnum {
        ABC, DEF
    }

    def "can transform enums correctly - #desc"() {
        def typesArray = types.collect { new CachedClass(it, null) } as CachedClass[]
        def argsArray = args as Object[]

        expect:
        transformer.transform(typesArray, argsArray).toList() == transformed

        where:
        types              | args           | transformed           | desc
        [TestEnum]         | ["abc"]        | [TestEnum.ABC]        | "one arg"
        [TestEnum, String] | ["abc", "abc"] | [TestEnum.ABC, "abc"] | "two args"
    }

    def "returns original args when no transformation is required - #desc"() {
        def typesArray = types.collect { new CachedClass(it, null) } as CachedClass[]
        def argsArray = args as Object[]

        expect:
        transformer.transform(typesArray, argsArray).is(argsArray)

        where:
        types              | args                  | desc
        []                 | []                    | "zero args"
        [TestEnum]         | [TestEnum.ABC]        | "one arg"
        [TestEnum, String] | [TestEnum.ABC, "abc"] | "two args"
        [String]           | ["abc"]               | "non enum args"
    }

    def "returns original args when no transformation is available for all args - #desc"() {
        def typesArray = types.collect { new CachedClass(it, null) } as CachedClass[]
        def argsArray = args as Object[]

        expect:
        transformer.transform(typesArray, argsArray).is(argsArray)

        where:
        types            | args                  | desc
        [String]         | [TestEnum.ABC]        | "one arg"
        [Long, TestEnum] | [TestEnum.ABC, "abc"] | "two args"
    }

    def "exception thrown when coercing invalid string to enum"() {
        when:
        transformer.transformValue(TestEnum, "invalid")

        then:
        def e = thrown IllegalArgumentException
        TestEnum.values().every { e.message.contains(it.name()) }
    }

}
