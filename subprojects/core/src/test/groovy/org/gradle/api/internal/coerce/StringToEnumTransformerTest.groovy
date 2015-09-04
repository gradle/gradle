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

import org.gradle.internal.typeconversion.TypeConversionException
import spock.lang.Specification
import spock.lang.Unroll

@SuppressWarnings("GroovyUnusedDeclaration")
class StringToEnumTransformerTest extends Specification {

    def transformer = new StringToEnumTransformer()

    static enum TestEnum {
        ABC, DEF
    }

    static class EnumTester {
        TestEnum enumProperty

        void oneArgEnumMethod(TestEnum testEnum) {}

        void twoArgEnumMethod(TestEnum testEnum, String other) {}

        void oneArgNonEnumMethod(String other) {}
    }

    @Unroll
    def "can enum transform correctly - #desc"() {
        def i = new EnumTester()

        expect:
        transformer.transform(i, name, args.toArray()).toList() == transformed

        where:
        name                  | args    | transformed    | desc
        "setEnumProperty"     | ["abc"] | [TestEnum.ABC] | "for property"
        "oneArgEnumMethod"    | ["dEf"] | [TestEnum.DEF] | "one arg method"
        "twoArgEnumMethod"    | ["abc"] | ["abc"]        | "two arg method"
        "oneArgNonEnumMethod" | ["abc"] | ["abc"]        | "one arg method non enum method"
    }

    def "exception thrown when coercing invalid string to enum"() {
        def i = new EnumTester()

        when:
        transformer.transform(i, "oneArgEnumMethod", "invalid")

        then:
        def e = thrown TypeConversionException
        e.message.contains TestEnum.values().toString() // error message shows valid values
    }

}
