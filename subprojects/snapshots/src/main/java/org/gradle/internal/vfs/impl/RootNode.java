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

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import javax.annotation.Nullable;
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

    @Nullable
    @Override
    public Node getChild(String name) {
        if (name.isEmpty()) {
            return new EmptyPathRootNode(this);
        }
        return super.getChild(name);
    }

    @Override
    public Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier) {
        if (name.isEmpty()) {
            return new EmptyPathRootNode(this);
        }
        return super.getOrCreateChild(name, nodeSupplier);
    }

    @Override
    public FileSystemLocationSnapshot getSnapshot() {
        throw new UnsupportedOperationException("Cannot visit root node");
    }

    @Override
    public String getChildAbsolutePath(String name) {
        return name;
    }

    public void clear() {
        getChildren().clear();
    }

    private static class EmptyPathRootNode implements Node {
        private final RootNode delegate;

        public EmptyPathRootNode(RootNode delegate) {
            this.delegate = delegate;
        }

        @Nullable
        @Override
        public Node getChild(String name) {
            return delegate.getChild(name);
        }

        @Override
        public Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier) {
            return delegate.getOrCreateChild(name, nodeSupplier, this);
        }

        @Override
        public Node replaceChild(String name, Function<Node, Node> nodeSupplier, Predicate<Node> shouldReplaceExisting) {
            return delegate.replaceChild(name, nodeSupplier, null, this);
        }

        @Override
        public void removeChild(String name) {
            delegate.removeChild(name);
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
            return delegate.getType();
        }

        @Override
        public FileSystemLocationSnapshot getSnapshot() {
            return delegate.getSnapshot();
        }

    }
}
