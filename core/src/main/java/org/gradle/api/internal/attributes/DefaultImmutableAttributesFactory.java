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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ServiceScope(Scopes.BuildSession.class)
public class DefaultImmutableAttributesFactory implements ImmutableAttributesFactory {
    private final ImmutableAttributes root;
    private final Map<ImmutableAttributes, List<DefaultImmutableAttributes>> children;
    private final IsolatableFactory isolatableFactory;
    private final UsageCompatibilityHandler usageCompatibilityHandler;
    private final NamedObjectInstantiator instantiator;

    public DefaultImmutableAttributesFactory(IsolatableFactory isolatableFactory, NamedObjectInstantiator instantiator) {
        this.isolatableFactory = isolatableFactory;
        this.instantiator = instantiator;
        this.root = ImmutableAttributes.EMPTY;
        this.children = Maps.newHashMap();
        this.children.put(root, new ArrayList<DefaultImmutableAttributes>());
        this.usageCompatibilityHandler = new UsageCompatibilityHandler(isolatableFactory, instantiator);
    }

    public int size() {
        return children.size();
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
        return concat(root, key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, @Nullable T value) {
        return concat(node, key, isolate(value));
    }

    private <T> Isolatable<T> isolate(@Nullable T value) {
        if (value instanceof String) {
            return Cast.uncheckedNonnullCast(new CoercingStringValueSnapshot((String) value, instantiator));
        } else {
            return isolatableFactory.isolate(value);
        }
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        if (key.equals(Usage.USAGE_ATTRIBUTE) || key.getName().equals(Usage.USAGE_ATTRIBUTE.getName())) {
            return usageCompatibilityHandler.doConcat(this, node, key, value);
        } else {
            return doConcatIsolatable(node, key, value);
        }
    }

    ImmutableAttributes doConcatIsolatable(ImmutableAttributes node, Attribute<?> key, @Nullable Isolatable<?> value) {
        synchronized (this) {
            List<DefaultImmutableAttributes> nodeChildren = children.computeIfAbsent(node, k -> Lists.newArrayList());
            for (DefaultImmutableAttributes child : nodeChildren) {
                if (child.attribute.equals(key) && child.value.equals(value)) {
                    return child;
                }
            }
            DefaultImmutableAttributes child = new DefaultImmutableAttributes((DefaultImmutableAttributes) node, key, value);
            nodeChildren.add(child);
            return child;
        }
    }

    public ImmutableAttributes getRoot() {
        return root;
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) {
        if (attributes1 == ImmutableAttributes.EMPTY) {
            return attributes2;
        }
        if (attributes2 == ImmutableAttributes.EMPTY) {
            return attributes1;
        }
        ImmutableAttributes current = attributes2;
        for (Attribute<?> attribute : attributes1.keySet()) {
            if (!current.findEntry(attribute.getName()).isPresent()) {
                if (attributes1 instanceof DefaultImmutableAttributes) {
                    current = doConcatIsolatable(current, attribute, ((DefaultImmutableAttributes) attributes1).getIsolatableAttribute(attribute));
                } else {
                    current = concat(current, Cast.uncheckedNonnullCast(attribute), attributes1.getAttribute(attribute));
                }
            }
        }
        return current;
    }

    @Override
    public ImmutableAttributes safeConcat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) throws AttributeMergingException {
        if (attributes1 == ImmutableAttributes.EMPTY) {
            return attributes2;
        }
        if (attributes2 == ImmutableAttributes.EMPTY) {
            return attributes1;
        }
        ImmutableAttributes current = attributes2;
        for (Attribute<?> attribute : attributes1.keySet()) {
            AttributeValue<?> entry = current.findEntry(attribute.getName());
            if (entry.isPresent()) {
                Object currentAttribute = entry.get();
                Object existingAttribute = attributes1.getAttribute(attribute);
                if (!currentAttribute.equals(existingAttribute)) {
                    throw new AttributeMergingException(attribute, existingAttribute, currentAttribute);
                }
            }
            if (attributes1 instanceof DefaultImmutableAttributes) {
                current = doConcatIsolatable(current, attribute, ((DefaultImmutableAttributes) attributes1).getIsolatableAttribute(attribute));
            } else {
                current = concat(current, Cast.uncheckedNonnullCast(attribute), attributes1.getAttribute(attribute));
            }
        }
        return current;
    }
}
