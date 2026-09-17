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

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger

/**
 * PROOF test for {@link DesugaringAttributeContainerSerializer} with user-defined {@code Number}
 * subtypes, forcing the {@code NUMBER_ATTRIBUTE} read branch (resolveNumberType -> parseNumber
 * -> Class.forName). Characterizes:
 * <ul>
 *   <li>reconstruction via a static {@code valueOf(String)} factory,</li>
 *   <li>reconstruction via a {@code (String)} constructor,</li>
 *   <li>a type with neither (expected to fail in parseNumber), and</li>
 *   <li>a type loaded by a classloader the serializer's classloader cannot see — the case
 *       that decides whether finding #2's Class.forName concern is real.</li>
 * </ul>
 */
class CustomNumberDesugaringSerializerTest extends SerializerSpec {
    private AttributesFactory attributesFactory = AttributeTestUtil.attributesFactory()
    private DesugaringAttributeContainerSerializer serializer = new DesugaringAttributeContainerSerializer(attributesFactory, TestUtil.objectInstantiator())

    def "round-trips a custom Number reconstructed via a static valueOf(String) factory"() {
        given:
        def attribute = Attribute.of("test", WithValueOf)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new WithValueOf(7))

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        def resultAttribute = result.keySet().first()
        resultAttribute.type == WithValueOf
        def value = result.getAttribute(resultAttribute)
        value.class == WithValueOf
        value == new WithValueOf(7)
    }

    def "round-trips a custom Number reconstructed via a (String) constructor"() {
        given:
        def attribute = Attribute.of("test", WithStringCtor)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new WithStringCtor("7"))

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        def resultAttribute = result.keySet().first()
        resultAttribute.type == WithStringCtor
        def value = result.getAttribute(resultAttribute)
        value.class == WithStringCtor
        value == new WithStringCtor("7")
    }

    def "fails to reconstruct a custom Number that has neither valueOf(String) nor a (String) constructor"() {
        given:
        def attribute = Attribute.of("test", WithNeither)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new WithNeither(7))

        when:
        serialize(container, serializer)

        then:
        // parseNumber finds no valueOf(String) factory and no (String) constructor.
        def e = thrown(IllegalStateException)
        e.message.contains("Cannot reconstruct attribute value '7' as")
        e.message.contains(WithNeither.name)
        e.cause instanceof NoSuchMethodException
    }

    def "custom Number type not visible to the serializer's classloader fails to reconstruct on read"() {
        given: "a Number subtype defined in a child classloader the serializer's classloader cannot see"
        def childLoader = new GroovyClassLoader(DesugaringAttributeContainerSerializer.classLoader)
        Class<?> hidden = childLoader.parseClass('''
            class HiddenNumber extends Number {
                final int v
                HiddenNumber(int v) { this.v = v }
                static HiddenNumber valueOf(String s) { new HiddenNumber(Integer.parseInt(s)) }
                int intValue() { v }
                long longValue() { (long) v }
                float floatValue() { (float) v }
                double doubleValue() { (double) v }
                String toString() { String.valueOf(v) }
                boolean equals(Object o) { o.getClass() == getClass() && o.v == v }
                int hashCode() { v }
            }
        ''')
        def value = hidden.getConstructor(int.class).newInstance(7)
        def attribute = Attribute.of("test", hidden)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, value)

        when:
        serialize(container, serializer)

        then:
        // Write succeeds (only needs the type name + toString). Read fails: resolveNumberType does
        // Class.forName on the serializer's own (parent) classloader, which cannot see a type defined
        // in the child loader. This is finding #2: presence on producer/consumer build classpaths is
        // NOT sufficient, because those live in child classloaders of Gradle's core.
        def e = thrown(IllegalStateException)
        e.message == "Cannot deserialize attribute value: number type 'HiddenNumber' was not found"
        e.cause instanceof ClassNotFoundException
    }

    def "round-trips a negative custom Number value"() {
        given:
        def attribute = Attribute.of("test", WithValueOf)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new WithValueOf(-42))

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then:
        result.getAttribute(result.keySet().first()) == new WithValueOf(-42)
    }

    def "ignores a valueOf(String) whose return type is not the number type and falls back to the (String) constructor"() {
        given:
        def attribute = Attribute.of("test", ValueOfWrongReturnType)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new ValueOfWrongReturnType("7"))

        when:
        ImmutableAttributes result = serialize(container, serializer) as ImmutableAttributes

        then: "the wrong-typed valueOf is rejected by the isAssignableFrom guard, so the (String) constructor is used"
        def value = result.getAttribute(result.keySet().first())
        value.class == ValueOfWrongReturnType
        value == new ValueOfWrongReturnType("7")
    }

    def "wraps an exception thrown by valueOf(String) during reconstruction"() {
        given:
        def attribute = Attribute.of("test", ThrowingValueOf)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new ThrowingValueOf(7))

        when:
        serialize(container, serializer)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("error invoking 'valueOf(String)'")
        e.cause instanceof InvocationTargetException
    }

    def "fails to reconstruct a JDK Number subtype (AtomicInteger) that lacks valueOf(String) and a (String) constructor"() {
        given: "AtomicInteger is a Number subtype visible to the core classloader, but has no valueOf(String) and no (String) constructor"
        def attribute = Attribute.of("test", AtomicInteger)
        def container = attributesFactory.concat(ImmutableAttributes.EMPTY, attribute, new AtomicInteger(7))

        when:
        serialize(container, serializer)

        then: "resolveNumberType succeeds (visible), but parseNumber cannot rebuild it — 'any Number subtype' is narrower than the docs imply"
        def e = thrown(IllegalStateException)
        e.message.contains("Cannot reconstruct attribute value '7' as")
        e.cause instanceof NoSuchMethodException
    }

    // --- Custom Number subtypes visible to this test's (and the serializer's) classloader ---

    static class WithValueOf extends Number implements Serializable {
        final int v
        WithValueOf(int v) { this.v = v }
        static WithValueOf valueOf(String s) { new WithValueOf(Integer.parseInt(s)) }
        int intValue() { v }
        long longValue() { (long) v }
        float floatValue() { (float) v }
        double doubleValue() { (double) v }
        String toString() { String.valueOf(v) }
        boolean equals(Object o) { o instanceof WithValueOf && ((WithValueOf) o).v == v }
        int hashCode() { v }
    }

    static class WithStringCtor extends Number implements Serializable {
        final int v
        WithStringCtor(String s) { this.v = Integer.parseInt(s) }
        int intValue() { v }
        long longValue() { (long) v }
        float floatValue() { (float) v }
        double doubleValue() { (double) v }
        String toString() { String.valueOf(v) }
        boolean equals(Object o) { o instanceof WithStringCtor && ((WithStringCtor) o).v == v }
        int hashCode() { v }
    }

    static class WithNeither extends Number implements Serializable {
        final int v
        WithNeither(int v) { this.v = v }
        int intValue() { v }
        long longValue() { (long) v }
        float floatValue() { (float) v }
        double doubleValue() { (double) v }
        String toString() { String.valueOf(v) }
        boolean equals(Object o) { o instanceof WithNeither && ((WithNeither) o).v == v }
        int hashCode() { v }
    }

    static class ValueOfWrongReturnType extends Number implements Serializable {
        final int v
        ValueOfWrongReturnType(String s) { this.v = Integer.parseInt(s) }
        // Wrong return type (not assignable to ValueOfWrongReturnType): must be rejected by parseNumber.
        static String valueOf(String s) { "ignored" }
        int intValue() { v }
        long longValue() { (long) v }
        float floatValue() { (float) v }
        double doubleValue() { (double) v }
        String toString() { String.valueOf(v) }
        boolean equals(Object o) { o instanceof ValueOfWrongReturnType && ((ValueOfWrongReturnType) o).v == v }
        int hashCode() { v }
    }

    static class ThrowingValueOf extends Number implements Serializable {
        final int v
        ThrowingValueOf(int v) { this.v = v }
        static ThrowingValueOf valueOf(String s) { throw new IllegalArgumentException("boom: " + s) }
        int intValue() { v }
        long longValue() { (long) v }
        float floatValue() { (float) v }
        double doubleValue() { (double) v }
        String toString() { String.valueOf(v) }
        boolean equals(Object o) { o instanceof ThrowingValueOf && ((ThrowingValueOf) o).v == v }
        int hashCode() { v }
    }
}
