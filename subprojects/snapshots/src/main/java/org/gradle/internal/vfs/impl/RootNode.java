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

import java.io.File;
import java.util.function.Function;
import java.util.function.Predicate;

public class RootNode extends AbstractNodeWithMutableChildren {

    @Override
    public String getAbsolutePath() {
        return "";
    }

    @Override
    public Type getType() {
        return Type.DIRECTORY;
    }

    @Override
    public Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier) {
        if (name.isEmpty()) {
            return new Node() {
                @Override
                public Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier) {
                    return RootNode.super.getOrCreateChild(name, nodeSupplier, this);
                }

                @Override
                public Node replaceChild(String name, Function<Node, Node> nodeSupplier, Predicate<Node> shouldReplaceExisting) {
                    return RootNode.super.replaceChild(name, nodeSupplier, null, this);
                }

                @Override
                public void removeChild(String name) {
                    RootNode.super.removeChild(name);
                }

                @Override
                public String getAbsolutePath() {
                    return "/";
                }

                @Override
                public String getChildAbsolutePath(String name) {
                    return File.separatorChar + name;
                }

                @Override
                public Type getType() {
                    return RootNode.this.getType();
                }

                @Override
                public void accept(FileSystemSnapshotVisitor visitor) {
                    RootNode.this.accept(visitor);
                }

                @Override
                public void underLock(Runnable action) {
                    RootNode.super.underLock(action);
                }
            };
        }
        return super.getOrCreateChild(name, nodeSupplier);
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        throw new UnsupportedOperationException("Cannot visit root node");
    }

    @Override
    public String getChildAbsolutePath(String name) {
        return name;
    }

    public void clear() {
        getChildren().clear();
    }
}
