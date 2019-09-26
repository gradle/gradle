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
import org.gradle.internal.snapshot.MissingFileSnapshot;

import java.util.function.Function;
import java.util.function.Predicate;

public class MissingFileNode implements Node {

    private final Node parent;
    private final String absolutePath;
    private final String name;

    public MissingFileNode(Node parent, String absolutePath, String name) {
        this.parent = parent;
        this.absolutePath = absolutePath;
        this.name = name;
    }

    @Override
    public Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier) {
        return new MissingFileNode(this, getChildAbsolutePath(name), name);
    }

    @Override
    public Node replaceChild(String name, Function<Node, Node> nodeSupplier, Predicate<Node> shouldReplaceExisting) {
        return new MissingFileNode(this, getChildAbsolutePath(name), name);
    }

    @Override
    public void removeChild(String name) {
        parent.removeChild(this.name);
    }

    @Override
    public String getAbsolutePath() {
        return absolutePath;
    }

    @Override
    public Type getType() {
        return Type.MISSING;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        visitor.visitFile(new MissingFileSnapshot(absolutePath, name));
    }

    @Override
    public void underLock(Runnable action) {
        parent.underLock(action);
    }
}
