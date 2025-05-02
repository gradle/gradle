/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.attributes.immutable

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

/**
 * Tests {@link ImmutableAttributesSchema}.
 * <p>
 * Also tests {@link ImmutableAttributesSchemaFactory} quite a bit, as it
 * constructs and deduplicates immutable schema instances and this test
 * verifies the equality of the instances.
 */
class ImmutableAttributesSchemaTest extends Specification {

    ImmutableAttributesSchemaFactory factory = AttributeTestUtil.services().getSchemaFactory()

    def "empty schema is same as ImmutableAttributesSchema.EMPTY"() {
        when:
        def schema = create()

        then:
        same(schema, ImmutableAttributesSchema.EMPTY)
    }

    def "empty schemas are same"() {
        expect:
        same()
    }

    def "schemas registering a same attribute are the same"() {
        def a = Attribute.of("a", String)

        expect:
        same {
            attribute(a)
        }
    }

    def "schemas registering equivalent attributes are the same"() {
        def first = create {
            attribute(Attribute.of("a", String))
        }
        def second = create {
            attribute(Attribute.of("a", String))
        }

        expect:
        same(first, second)
    }

    def "schemas registering attributes with different names are different"() {
        def first = create {
            attribute(Attribute.of("a", String))
        }
        def second = create {
            attribute(Attribute.of("b", String))
        }

        expect:
        different(first, second)
    }

    def "schemas registering attributes with different types are different"() {
        def first = create {
            attribute(Attribute.of("a", String))
        }
        def second = create {
            attribute(Attribute.of("a", Boolean))
        }

        expect:
        different(first, second)
    }

    def "schemas with one disambiguation rule are same"() {
        def a = Attribute.of("a", String)

        expect:
        same {
            attribute(a).disambiguationRules.add(FirstDisambiguationRule)
        }
    }

    def "schemas with one compatibility rule are same"() {
        def a = Attribute.of("a", String)

        expect:
        same {
            attribute(a).compatibilityRules.add(FirstCompatibilityRule)
        }
    }

    def "schemas with two disambiguation rules are same"() {
        def a = Attribute.of("a", String)

        expect:
        same {
            attribute(a).disambiguationRules.add(FirstDisambiguationRule)
            attribute(a).disambiguationRules.add(SecondDisambiguationRule)
        }
    }

    def "schemas with two compatibility rules are same"() {
        def a = Attribute.of("a", String)

        expect:
        same {
            attribute(a).compatibilityRules.add(FirstCompatibilityRule)
            attribute(a).compatibilityRules.add(SecondCompatibilityRule)
        }
    }

    def "schemas with built-in rules are same"() {
        def a = Attribute.of("a", String)

        expect:
        same {
            attribute(a).disambiguationRules.pickFirst(Comparator.naturalOrder())
            attribute(a).disambiguationRules.pickLast(Comparator.naturalOrder())
            attribute(a).compatibilityRules.ordered(Comparator.naturalOrder())
            attribute(a).compatibilityRules.reverseOrdered(Comparator.naturalOrder())
        }
    }

    def "schemas with built-in rules but different comparators are different"() {
        def a = Attribute.of("a", String)

        def first = create {
            action.delegate = delegate.attribute(a)
            action(Comparator.naturalOrder())
        }
        def second = create {
            action.delegate = delegate.attribute(a)
            action(Comparator.reverseOrder())
        }

        expect:
        different(first, second)

        where:
        action << [
            { delegate.disambiguationRules.pickFirst(it) },
            { delegate.disambiguationRules.pickLast(it) },
            { delegate.compatibilityRules.ordered(it) },
            { delegate.compatibilityRules.reverseOrdered(it) }
        ]
    }

    def "schemas with different disambiguation rule order are different"() {
        def a = Attribute.of("a", String)

        def first = create {
            attribute(a).disambiguationRules.add(FirstDisambiguationRule)
            attribute(a).disambiguationRules.add(SecondDisambiguationRule)
        }
        def second = create {
            attribute(a).disambiguationRules.add(SecondDisambiguationRule)
            attribute(a).disambiguationRules.add(FirstDisambiguationRule)
        }

        expect:
        different(first, second)
    }

    def "schemas with different compatibility rule order are different"() {
        def a = Attribute.of("a", String)

        def first = create {
            attribute(a).compatibilityRules.add(FirstCompatibilityRule)
            attribute(a).compatibilityRules.add(SecondCompatibilityRule)
        }
        def second = create {
            attribute(a).compatibilityRules.add(SecondCompatibilityRule)
            attribute(a).compatibilityRules.add(FirstCompatibilityRule)
        }

        expect:
        different(first, second)
    }

    def "schemas with same disambiguation rule but for different attributes are different"() {
        def a = Attribute.of("a", String)
        def b = Attribute.of("b", String)

        def first = create {
            attribute(a).disambiguationRules.add(FirstDisambiguationRule)
        }
        def second = create {
            attribute(b).disambiguationRules.add(FirstDisambiguationRule)
        }

        expect:
        different(first, second)
    }

    def "schemas with same compatibility rule but for different attributes are different"() {
        def a = Attribute.of("a", String)
        def b = Attribute.of("b", String)

        def first = create {
            attribute(a).compatibilityRules.add(FirstCompatibilityRule)
        }
        def second = create {
            attribute(b).compatibilityRules.add(FirstCompatibilityRule)
        }

        expect:
        different(first, second)
    }

    def "schemas with same disambiguation rule with same parameters same"() {
        def a = Attribute.of("a", String)
        def named = TestUtil.objectInstantiator().named(Usage, "foo")

        expect:
        same {
            attribute(a).disambiguationRules.add(FirstDisambiguationRule) {
                params(1, "String", false, null, ["foo"], named)
            }
        }
    }

    def "schemas with same disambiguation rules but different parameters are different"() {
        def a = Attribute.of("a", String)

        def first = create {
            attribute(a).disambiguationRules.add(FirstDisambiguationRule) {
                params(1)
            }
        }
        def second = create {
            attribute(a).disambiguationRules.add(FirstDisambiguationRule) {
                params(2)
            }
        }

        expect:
        different(first, second)
    }

    def "schemas with same compatibility rule with same parameters same"() {
        def a = Attribute.of("a", String)
        def named = TestUtil.objectInstantiator().named(Usage, "foo")

        expect:
        same {
            attribute(a).compatibilityRules.add(FirstCompatibilityRule) {
                params(1, "String", false, null, ["foo"], named)
            }
        }
    }

    def "schemas with same compatibility rules but different parameters are different"() {
        def a = Attribute.of("a", String)

        def first = create {
            attribute(a).compatibilityRules.add(FirstCompatibilityRule) {
                params(1)
            }
        }
        def second = create {
            attribute(a).compatibilityRules.add(FirstCompatibilityRule) {
                params(2)
            }
        }

        expect:
        different(first, second)
    }

    def "schemas with same precedence order are the same"() {
        def a = Attribute.of("a", String)
        def b = Attribute.of("b", String)

        expect:
        same {
            attributeDisambiguationPrecedence(a, b)
        }
    }

     def "schemas with different ordered attribute precedence order are different"() {
        def a = Attribute.of("a", String)
        def b = Attribute.of("b", String)

        def first = create {
            attributeDisambiguationPrecedence(a, b)
        }
        def second = create {
            attributeDisambiguationPrecedence(b, a)
        }

        expect:
        different(first, second)
    }

    def "schema with attribute precedence with same name but different types are different"() {
        def a = Attribute.of("a", String)
        def b = Attribute.of("a", Boolean)

        def first = create {
            attributeDisambiguationPrecedence(a)
        }
        def second = create {
            attributeDisambiguationPrecedence(b)
        }

        expect:
        different(first, second)
    }

    static class FirstDisambiguationRule implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> objectMultipleCandidatesDetails) {}
    }
    static class SecondDisambiguationRule implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> objectMultipleCandidatesDetails) {}
    }
    static class FirstCompatibilityRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> stringCompatibilityCheckDetails) {}
    }
    static class SecondCompatibilityRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> stringCompatibilityCheckDetails) {}
    }

    boolean same(@DelegatesTo(AttributesSchema) Closure<?> action = {}) {
        def first = create(action)
        def second = create(action)
        same(first, second)
    }

    boolean same(ImmutableAttributesSchema first, ImmutableAttributesSchema second) {
        return first.is(second) && first == second && first.hashCode() == second.hashCode()
    }

    boolean different(ImmutableAttributesSchema first, ImmutableAttributesSchema second) {
        return !first.is(second) && first != second && first.hashCode() != second.hashCode()
    }

    ImmutableAttributesSchema create(@DelegatesTo(AttributesSchema) Closure<?> action = {}) {
        return factory.create(AttributeTestUtil.mutableSchema(action))
    }
}
