/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableMap
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.internal.component.model.AttributeSelectionSchema
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.util.TestUtil.objectFactory

/**
 * Tests {@link DefaultAttributesSchema.DefaultAttributeSelectionSchema}. The class-under-test should
 * eventually be moved into its own file so that we don't need to instantiate it indirectly through a
 * {@link DefaultAttributesSchema} instance. Most tests in {@link DefaultAttributesSchemaTest} probably
 * belong here instead.
 */
class DefaultAttributeSelectionSchemaTest extends Specification {
    private static final ImmutableAttributesFactory ATTRIBUTES_FACTORY = AttributeTestUtil.attributesFactory()

    private AttributesSchemaInternal attributesSchema = Spy(new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory()))
    private AttributeSelectionSchema schema = attributesSchema.matcher().selectionSchema

    def "collects extra attributes, single candidate"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("bar", String)

        given:
        ImmutableAttributes[] candidates = [candidate(ImmutableMap.of(attr1, "v1", attr2, "v2"))]
        AttributeContainer requested = attribute(attr1, "v3")

        when:
        def extraAttributes = schema.collectExtraAttributes(candidates, requested).toList()

        then:
        extraAttributes.contains(attr2)
        !extraAttributes.contains(attr1)
    }

    def "collects extra attributes, two candidates"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("bar", String)
        def attr3 = Attribute.of("baz", String)

        given:
        ImmutableAttributes[] candidates = [
            candidate(ImmutableMap.of(attr1, "v1", attr2, "v2")),
            candidate(ImmutableMap.of(attr2, "v1", attr3, "v2"))
        ]
        AttributeContainer requested = attribute(attr1, "v3")

        when:
        def extraAttributes = schema.collectExtraAttributes(candidates, requested).toList()

        then:
        extraAttributes.contains(attr2)
        extraAttributes.contains(attr3)
        !extraAttributes.contains(attr1)
    }

    def "prefers extra attributes from the selection schema"() {
        def foo1 = Attribute.of("foo", String)
        def foo2 = Attribute.of("foo", String)
        def attr3 = Attribute.of("baz", String)

        given:

        ImmutableAttributes[] candidates = [candidate(ImmutableMap.of(attr3, "v2", foo1, "v2"))]
        AttributeContainer requested = attribute(attr3, "v3")
        attributesSchema.getAttributeByName("foo") >> foo2

        when:
        def extraAttributes = schema.collectExtraAttributes(candidates, requested).toList()

        then:
        extraAttributes.contains(foo2)
        extraAttributes.every {
            !it.is(foo1)
        }
        !extraAttributes.contains(attr3)
    }

    def "ignores attribute type when computing extra attributes"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("foo", Usage)

        given:

        ImmutableAttributes[] candidates = [candidate(ImmutableMap.of(attr2, objectFactory().named(Usage, "foo")))]
        AttributeContainer requested = attribute(attr1, "v3")

        expect:
        schema.collectExtraAttributes(candidates, requested).length == 0
    }

    def attribute(Attribute<String> attr, String value) {
        def mutable = ATTRIBUTES_FACTORY.mutable()
        mutable.attribute(attr, value)
        mutable.asImmutable()
    }

    private <T> ImmutableAttributes candidate(Map<Attribute<T>, T> map) {
        def mutable = ATTRIBUTES_FACTORY.mutable()
        map.each { mutable.attribute(it.key, it.value)}
        mutable.asImmutable()
    }
}
