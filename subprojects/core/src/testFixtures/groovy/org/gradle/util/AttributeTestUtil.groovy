/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.util

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesFactory
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistryFactory

class AttributeTestUtil {
    static DefaultAttributesFactory attributesFactory() {
        return new DefaultAttributesFactory(SnapshotTestUtil.isolatableFactory(), TestUtil.objectInstantiator())
    }

    /**
     * Creates an attribute set that can contain only JDK-typed {@link Attribute} values.
     *
     * The type of the attribute is derived by the class of the value.
     *
     * Use {@link #attributesTyped(Map)} to create an attribute set with more specific attribute values.
     */
    static ImmutableAttributes attributes(Map<String, Object> values) {
        def attrs = ImmutableAttributes.EMPTY
        if (values) {
            values.each { String key, Object value ->
                assert value.class.package.name.startsWith("java.lang")
                def attribute = Attribute.of(key, value.class)
                attrs = attributesFactory().concat(attrs, attribute, value)
            }
        }
        return attrs
    }

    /**
     * Creates an attribute set with the provided attributes and values.
     *
     * The type of the attribute must be assignable from the value of the attribute.
     */
    static ImmutableAttributes attributesTyped(Map<Attribute, ?> values) {
        def attrs = ImmutableAttributes.EMPTY
        if (values) {
            values.each { Attribute key, Object value ->
                assert key.type.isAssignableFrom(value.class)
                attrs = attributesFactory().concat(attrs, key, value)
            }
        }
        return attrs
    }

    static <T> T named(Class<T> clazz, String value) {
        TestUtil.objectInstantiator().named(clazz, value)
    }

    /**
     * Creates a mutable attribute schema, configuring it with the provided action.
     */
    static AttributesSchemaInternal mutableSchema(@DelegatesTo(AttributesSchema) @ClosureParams(value = SimpleType, options = ["org.gradle.api.attributes.AttributesSchema"]) Closure<?> action = {}) {
        def attributesSchema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        action.delegate = attributesSchema
        action(attributesSchema)

        return attributesSchema
    }

    /**
     * Creates an immutable attribute schema, configuring it with the provided action.
     */
    static ImmutableAttributesSchema immutableSchema(@DelegatesTo(AttributesSchema) @ClosureParams(value = SimpleType, options = ["org.gradle.api.attributes.AttributesSchema"]) Closure<?> action = {}) {
        def mutable = mutableSchema(action)
        return services().getSchemaFactory().create(mutable)
    }

    /**
     * Creates a service factory, used for creating attribute matchers and variant transformers.
     */
    static AttributeSchemaServices services() {
        new AttributeSchemaServices(
            new ImmutableAttributesSchemaFactory(TestUtil.inMemoryCacheFactory()),
            new ImmutableArtifactTypeRegistryFactory(TestUtil.inMemoryCacheFactory(), attributesFactory()),
            TestUtil.inMemoryCacheFactory()
        )
    }
}
