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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractNodeWithMutableChildren implements Node {
    private final Map<String, Node> children = new HashMap<>();

    @Override
    public Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier) {
        return getOrCreateChild(name, nodeSupplier, this);
    }

    protected Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier, Node parent) {
        return children.computeIfAbsent(name, key -> nodeSupplier.apply(parent));
    }

    @Override
    public Node replaceChild(String name, Function<Node, Node> nodeSupplier, Function<Node, Node> replacement) {
        return replaceChild(name, nodeSupplier, replacement, this);
    }

    public Node replaceChild(String name, Function<Node, Node> nodeSupplier, Function<Node, Node> replacement, Node parent) {
        return children.compute(name, (key, current) -> current == null
            ? nodeSupplier.apply(parent)
            : replacement.apply(current));
    }

    public Map<String, Node> getChildren() {
        return children;
    }
}
