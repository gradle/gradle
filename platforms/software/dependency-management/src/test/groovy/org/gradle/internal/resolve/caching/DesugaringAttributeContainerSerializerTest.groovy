/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.internal.resolve.caching

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.test.fixtures.ExpectDeprecation
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

/**
 * Unit tests for {@link DesugaringAttributeContainerSerializer}.
 */
class DesugaringAttributeContainerSerializerTest extends SerializerSpec {
    private AttributesFactory attributesFactory = AttributeTestUtil.attributesFactory()
    private DesugaringAttributeContainerSerializer serializer = new DesugaringAttributeContainerSerializer(attributesFactory, TestUtil.objectInstantiator())

    def "round-trips a #type.simpleName attribute value (#value)"() {
        given:
        def attribute = Attribute.of("test", type)
        //noinspection GroovyAssignabilityCheck
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, value)

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        result.keySet().size() == 1
        def resultAttribute = result.keySet().first()
        resultAttribute.name == "test"
        resultAttribute.type == type
        def resultValue = result.getAttribute(resultAttribute)
        resultValue.class == type

        and:
        // Use .equals() rather than Groovy '==' so NaN round-trips compare equal and BigDecimal
        // comparison stays scale-sensitive, verifying an exact reconstruction.
        //noinspection ChangeToOperator
        resultValue.equals(value)

        where:
        type       | value
        Byte       | (Byte) 42
        Byte       | Byte.MIN_VALUE
        Byte       | Byte.MAX_VALUE
        Short      | (Short) 4242
        Short      | Short.MIN_VALUE
        Short      | Short.MAX_VALUE
        Integer    | 7
        Integer    | Integer.MIN_VALUE
        Integer    | Integer.MAX_VALUE
        Long       | 9000000000L
        Long       | Long.MIN_VALUE
        Long       | Long.MAX_VALUE
        Float      | 1.5f
        Float      | Float.MIN_VALUE
        Float      | Float.MAX_VALUE
        Float      | Float.NaN
        Float      | Float.POSITIVE_INFINITY
        Double     | 3.14159d
        Double     | Double.MIN_VALUE
        Double     | Double.MAX_VALUE
        Double     | Double.NaN
        Double     | Double.NEGATIVE_INFINITY
        BigInteger | BigInteger.valueOf(123456789012345678L).multiply(BigInteger.TEN)
        BigDecimal | new BigDecimal("12345678901234567890.0987654321")
    }

    def "round-trips a String attribute value"() {
        given:
        def attribute = Attribute.of("test", String)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, "hello")

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        def resultAttribute = result.keySet().first()
        resultAttribute.type == String
        result.getAttribute(resultAttribute) == "hello"
    }

    def "round-trips a Boolean attribute value"() {
        given:
        def attribute = Attribute.of("test", Boolean)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, true)

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        def resultAttribute = result.keySet().first()
        resultAttribute.type == Boolean
        result.getAttribute(resultAttribute) == true
    }

    def "desugars a Named attribute value"() {
        given:
        def named = TestUtil.objectInstantiator().named(Flavor, "vanilla")
        def attribute = Attribute.of("flavor", Flavor)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, named)

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        def resultAttribute = result.keySet().first()
        resultAttribute.name == "flavor"
        result.getAttribute(Attribute.of("flavor", String)) == "vanilla"
    }

    @ExpectDeprecation("as a value type for attribute 'flavor' has been deprecated")
    def "round-trips a plain Enum attribute value"() {
        given:
        // Plain (non-Named) enums are deprecated as attribute value types, but until that becomes an
        // error in Gradle 10 one can still reach this serializer — e.g. via a component metadata rule.
        def attribute = Attribute.of("flavor", PlainFlavor)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, PlainFlavor.VANILLA)

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        def resultAttribute = result.keySet().first()
        resultAttribute.name == "flavor"

        and: "the value is written as the constant's name, and coerced back to the constant on read"
        result.getAttribute(Attribute.of("flavor", String)) == "VANILLA"
        result.getAttribute(attribute) == PlainFlavor.VANILLA
    }

    @ExpectDeprecation('Serializing the value of attribute \'flavor\', of the enum type \'org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializerTest$PlainFlavor\', which does not implement org.gradle.api.Named has been deprecated')
    def "writing a plain Enum attribute value emits its own deprecation"() {
        given:
        // Attribute.of already nags whoever declares the type, but a consumer can read a value of
        // that type back out of a cache an earlier build wrote without ever declaring it itself.
        def attribute = Attribute.of("flavor", PlainFlavor)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, PlainFlavor.VANILLA)

        expect:
        serialize(container, serializer)
    }

    @ExpectDeprecation("as a value type for attribute 'flavor' has been deprecated")
    def "fails with a clear error when an attribute value can not be serialized"() {
        given:
        def attribute = Attribute.of("flavor", Unsupported)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new Unsupported())

        when:
        serialize(container, serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Cannot serialize attribute 'flavor'")
    }

    def "round-trips a Named Enum attribute value whose getName() differs from name()"() {
        given:
        def attribute = Attribute.of("flavor", NamedFlavor)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, NamedFlavor.VANILLA)

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then: "getName() wins over name() on write, as for any other Named value"
        result.getAttribute(Attribute.of("flavor", String)) == "vanilla"

        and: "and read coerces the name back to the constant it was written from"
        result.getAttribute(attribute) == NamedFlavor.VANILLA
    }

    def "round-trips a container holding a mix of value types"() {
        given:
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, Attribute.of("s", String), "text")
        container = attributesFactory.concat(container, Attribute.of("i", Integer), 1)
        container = attributesFactory.concat(container, Attribute.of("l", Long), 2L)
        container = attributesFactory.concat(container, Attribute.of("d", Double), 3.0d)
        container = attributesFactory.concat(container, Attribute.of("b", Boolean), false)
        container = attributesFactory.concat(container, Attribute.of("n", Flavor), TestUtil.objectInstantiator().named(Flavor, "vanilla"))
        container = attributesFactory.concat(container, Attribute.of("e", NamedFlavor), NamedFlavor.CHOCOLATE)

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        result.keySet().size() == 7
        result.getAttribute(Attribute.of("n", Flavor)).name == "vanilla"
        result.getAttribute(Attribute.of("e", NamedFlavor)) == NamedFlavor.CHOCOLATE
        result.getAttribute(Attribute.of("s", String)) == "text"
        result.getAttribute(Attribute.of("i", Integer)) == 1
        result.getAttribute(Attribute.of("l", Long)) == 2L
        result.getAttribute(Attribute.of("d", Double)) == 3.0d
        result.getAttribute(Attribute.of("b", Boolean)) == false
    }

    interface Flavor extends Named {}

    enum PlainFlavor { VANILLA, CHOCOLATE }

    enum NamedFlavor implements Named {
        VANILLA, CHOCOLATE

        @Override
        String getName() {
            name().toLowerCase(Locale.ROOT)
        }
    }

    // Serializable so the value can be isolated into the container at all, and thus reach the serializer.
    static class Unsupported implements Serializable {}
}
