/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.attributes;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;

/**
 * The Android plugin currently adds some attributes to {@link org.gradle.api.artifacts.Configuration}s
 * without first declaring them in the schema. This should be an error, but for now we need to support
 * it by implicitly adding these attributes to the schema.
 */
public class SchemaUpdatingAttributeFactory implements ImmutableAttributesFactory {
    private final ImmutableAttributesFactory delegate;
    private final AttributesSchema schema;

    public SchemaUpdatingAttributeFactory(ImmutableAttributesFactory delegate, AttributesSchema schema) {
        this.delegate = delegate;
        this.schema = schema;
    }

    @Override
    public AttributeContainerInternal mutable() {
        return new DefaultMutableAttributeContainer(this);
    }

    @Override
    public AttributeContainerInternal mutable(AttributeContainerInternal parent) {
        return new DefaultMutableAttributeContainer(this, parent);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, T value) {
        schema.attribute(key);
        return delegate.of(key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value) {
        schema.attribute(key);
        return delegate.concat(node, key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        schema.attribute(key);
        return delegate.concat(node, key, value);
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) {
        return delegate.concat(attributes1, attributes2);
    }
}
