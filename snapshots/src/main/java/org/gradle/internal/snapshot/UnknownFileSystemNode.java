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

package org.gradle.internal.snapshot;

import java.util.Optional;

/**
 * An incomplete snapshot where we don’t know if it’s a file, a directory, or nothing.
 *
 * The snapshot must have children.
 * It is created when we store missing files underneath it, so that we don’t have to query them again and again.
 */
public class UnknownFileSystemNode extends AbstractIncompleteFileSystemNode {

    public UnknownFileSystemNode(ChildMap<? extends FileSystemNode> children) {
        super(children);
        assert !children.isEmpty();
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot() {
        return Optional.empty();
    }

    @Override
    protected FileSystemNode withIncompleteChildren(ChildMap<? extends FileSystemNode> merged) {
        return new UnknownFileSystemNode(merged);
    }

    @Override
    protected Optional<FileSystemNode> withAllChildrenRemoved() {
        return Optional.empty();
    }

    @Override
    protected FileSystemNode withIncompleteChildren() {
        return this;
    }
}
