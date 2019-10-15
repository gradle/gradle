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
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import javax.annotation.Nullable;
import java.io.File;

public class RootNode extends AbstractMutableNode {

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
    public Node getDescendant(ImmutableList<String> path) {
        String childName = path.get(0);
        if (childName.isEmpty()) {
            return new EmptyPathRootNode(this).getDescendant(path.subList(1, path.size()));
        }
        return super.getDescendant(path);
    }

    @Override
    public Node replaceDescendant(ImmutableList<String> path, ChildNodeSupplier nodeSupplier) {
        String childName = path.get(0);
        if (childName.isEmpty()) {
            return new EmptyPathRootNode(this).replaceDescendant(path.subList(1, path.size()), nodeSupplier);
        }
        return super.replaceDescendant(path, nodeSupplier);
    }

    @Override
    public void removeDescendant(ImmutableList<String> path) {
        String childName = path.get(0);
        if (childName.isEmpty()) {
            new EmptyPathRootNode(this).removeDescendant(path.subList(1, path.size()));
        } else {
            super.removeDescendant(path);
        }
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
        public Node getDescendant(ImmutableList<String> path) {
            return delegate.getDescendant(path);
        }

        @Override
        public Node replaceDescendant(ImmutableList<String> path, ChildNodeSupplier nodeSupplier) {
            return delegate.replace(path, nodeSupplier, this);
        }

        @Override
        public void removeDescendant(ImmutableList<String> path) {
            delegate.removeDescendant(path);
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
