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

package org.gradle.api.internal.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

/**
 * Tests {@link DefaultAttributesSchema}.
 */
class DefaultAttributesSchemaTest extends Specification {
    def schema = AttributeTestUtil.mutableSchema()

    def "can create an attribute of scalar type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            String,
            Number,
            MyEnum,
            Flavor
        ]
    }

    def "can create an attribute of array type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            String[].class,
            Number[].class,
            MyEnum[].class,
            Flavor[].class
        ]
    }

    enum MyEnum {
        FOO,
        BAR
    }

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of('flavor', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for flavor'
    }

    def "strategy is per attribute"() {
        given:
        schema.attribute(Attribute.of('a', Flavor))

        when:
        schema.getMatchingStrategy(Attribute.of('someOther', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for someOther'

        when:
        schema.getMatchingStrategy(Attribute.of('picard', Flavor))

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for picard'
    }

    def "precedence order can be set"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "a", "b", "c" ]
        when:
        schema.attributeDisambiguationPrecedence = [Attribute.of("c", ConcreteNamed)]
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c" ]
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c", "a" ]
    }

    def "precedence order cannot be changed for the same attribute"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Attribute 'a' precedence has already been set."
    }

    static interface Flavor extends Named {}

    static abstract class ConcreteNamed implements Named {
    }

}
