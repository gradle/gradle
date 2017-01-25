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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultImmutableAttributesFactory implements ImmutableAttributesFactory {
    private final ImmutableAttributes root;
    private final Map<ImmutableAttributes, List<ImmutableAttributes>> children;

    public DefaultImmutableAttributesFactory() {
        this.root = new ImmutableAttributes(this);
        this.children = Maps.newHashMap();
        children.put(root, new ArrayList<ImmutableAttributes>());
    }

    public int size() {
        return children.size();
    }

    @Override
    public Builder builder() {
        return root.builder;
    }

    @Override
    public Builder builder(ImmutableAttributes from) {
        return from.builder != null ? from.builder : new Builder(from);
    }

    @Override
    public ImmutableAttributes of(Attribute<?> key, Object value) {
        return concat(root, key, value);
    }

    @Override
    public synchronized ImmutableAttributes concat(ImmutableAttributes node, Attribute<?> key, Object value) {
        List<ImmutableAttributes> nodeChildren = children.get(node);
        if (nodeChildren == null) {
            nodeChildren = Lists.newArrayList();
            children.put(node, nodeChildren);
        }
        for (ImmutableAttributes child : nodeChildren) {
            if (child.attribute.equals(key) && child.value.equals(value)) {
                return child;
            }
        }
        ImmutableAttributes child = new ImmutableAttributes(node, key, value, this);
        nodeChildren.add(child);
        return child;
    }

    public ImmutableAttributes getRoot() {
        return root;
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes attributes, ImmutableAttributes state) {
        Builder builder = new Builder(attributes);
        for (Attribute<?> attribute : state.keySet()) {
            builder = builder.addAttribute(attribute, state.getAttribute(attribute));
        }
        return builder.get();
    }

    public class Builder {
        private final ImmutableAttributes node;

        public Builder(ImmutableAttributes from) {
            node = from;
        }

        public Builder addAttribute(Attribute<?> attribute, Object value) {
            ImmutableAttributes cur = node;
            if (!cur.contains(attribute)) {
                cur = concat(cur, attribute, value);
            }
            return cur.builder;
        }

        public Builder addAny(Object key, Object value) {
            return addAttribute(asAttribute(key), value);
        }

        public ImmutableAttributes get() {
            return node;
        }

        private Attribute<?> asAttribute(Object rawKey) {
            if (rawKey instanceof Attribute) {
                return (Attribute<?>) rawKey;
            }
            return Attribute.of(rawKey.toString(), String.class);
        }
    }
}
