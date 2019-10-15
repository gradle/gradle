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

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractMutableNode implements Node {
    private final ConcurrentHashMap<String, Node> children = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Node getDescendant(ImmutableList<String> path) {
        if (path.isEmpty()) {
            return this;
        }
        String childName = path.get(0);
        Node child = children.get(childName);
        return child != null
            ? child.getDescendant(path.subList(1, path.size()))
            : null;
    }

    @Override
    public Node replaceDescendant(ImmutableList<String> path, ChildNodeSupplier nodeSupplier) {
        return replace(path, nodeSupplier, this);
    }

    protected Node replace(ImmutableList<String> path, ChildNodeSupplier nodeSupplier, Node parent) {
        if (path.isEmpty()) {
            throw new UnsupportedOperationException("Can't replace current node");
        }
        boolean directChild = path.size() == 1;
        String childName = path.get(0);
        if (directChild) {
            return children.compute(childName, (key, current) -> nodeSupplier.create(parent));
        }
        return children.computeIfAbsent(childName, key -> new DefaultNode(childName, parent))
            .replaceDescendant(path.subList(1, path.size()), nodeSupplier);
    }

    @Override
    public void removeDescendant(ImmutableList<String> path) {
        if (path.isEmpty()) {
            throw new UnsupportedOperationException("Can't remove current node");
        }
        boolean directChild = path.size() == 1;
        String childName = path.get(0);
        if (directChild) {
            children.remove(childName);
        } else {
            Node child = children.get(childName);
            if (child != null) {
                child.removeDescendant(path.subList(1, path.size()));
            }
        }
    }

    public Map<String, Node> getChildren() {
        return children;
    }
}
