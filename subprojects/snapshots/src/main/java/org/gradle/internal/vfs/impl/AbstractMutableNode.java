/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractMutableNode implements Node {
    private final ConcurrentHashMap<String, Node> children = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Node getChild(String name) {
        return children.get(name);
    }

    @Override
    public Node getOrCreateChild(String name, ChildNodeSupplier nodeSupplier) {
        return getOrCreateChild(name, nodeSupplier, this);
    }

    protected Node getOrCreateChild(String name, ChildNodeSupplier nodeSupplier, Node parent) {
        return children.computeIfAbsent(name, key -> nodeSupplier.create(parent));
    }

    @Override
    public Node replaceChild(String name, ChildNodeSupplier nodeSupplier, ExistingChildPredicate shouldReplaceExisting) {
        return replaceChild(name, nodeSupplier, shouldReplaceExisting, this);
    }

    @Override
    public void removeChild(String name) {
        children.remove(name);
    }

    public Node replaceChild(String name, ChildNodeSupplier nodeSupplier, ExistingChildPredicate shouldReplaceExisting, Node parent) {
        return children.compute(
            name,
            (key, current) -> (current == null || shouldReplaceExisting.test(current))
                ? nodeSupplier.create(parent)
                : current
        );
    }

    public Map<String, Node> getChildren() {
        return children;
    }
}
