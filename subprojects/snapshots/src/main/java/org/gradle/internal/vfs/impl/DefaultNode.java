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

import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class DefaultNode implements Node {
    private final String name;
    private final String absolutePath;
    private final Map<String, Node> children = new HashMap<>();

    public DefaultNode(String name, Node parent) {
        this.name = name;
        this.absolutePath = parent.getChildAbsolutePath(name);
    }

    @Override
    public Node getChild(String name, Function<Node, Node> nodeSupplier) {
        return children.computeIfAbsent(name, key -> nodeSupplier.apply(this));
    }

    @Override
    public Node getChild(String name, Function<Node, Node> nodeSupplier, Function<Node, Node> replacement) {
        return children.compute(name, (key, current) -> current == null
            ? nodeSupplier.apply(this)
            : replacement.apply(current));
    }

    @Override
    public String getAbsolutePath() {
        return absolutePath;
    }

    @Override
    public Type getType() {
        return Type.UNKNOWN;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        throw new UnsupportedOperationException("Cannot query contents of Node: too much unknown. Path " + getAbsolutePath());
    }
}
