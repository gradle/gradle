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


import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory

class AttributeTestUtil {
    static ImmutableAttributesFactory attributesFactory() {
        return new DefaultImmutableAttributesFactory(SnapshotTestUtil.isolatableFactory(), TestUtil.objectInstantiator())
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
}
