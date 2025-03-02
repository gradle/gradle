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

import javax.annotation.Nullable;

/**
 * Default implementation of {@link AttributesFactory}.
 */
public class DefaultAttributesFactory implements AttributesFactory {

    private final AttributeValueIsolator attributeValueIsolator;
    private final BaseAttributesFactory delegate;

    public DefaultAttributesFactory(
        AttributeValueIsolator attributeValueIsolator,
        BaseAttributesFactory delegate
    ) {
        this.attributeValueIsolator = attributeValueIsolator;
        this.delegate = delegate;
    }

    @Override
    public AttributeContainerInternal mutable() {
        return new DefaultMutableAttributeContainer(delegate, attributeValueIsolator);
    }

    @Override
    public AttributeContainerInternal mutable(AttributeContainerInternal fallback) {
        return join(fallback, new DefaultMutableAttributeContainer(delegate, attributeValueIsolator));
    }

    @Override
    public AttributeContainerInternal join(AttributeContainerInternal fallback, AttributeContainerInternal primary) {
        return new HierarchicalMutableAttributeContainer(delegate, fallback, primary);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, T value) {
        return delegate.of(key, attributeValueIsolator.isolate(value));
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, @Nullable T value) {
        return delegate.concat(node, key, attributeValueIsolator.isolate(value));
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes fallback, ImmutableAttributes primary) {
        return delegate.concat(fallback, primary);
    }

    @Override
    public ImmutableAttributes safeConcat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) throws AttributeMergingException {
        return delegate.safeConcat(attributes1, attributes2);
    }
}
