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
 * An incomplete snapshot of an existing directory.
 *
 * May include some of its children.
 */
public class PartialDirectoryNode extends AbstractIncompleteFileSystemNode {

    public static PartialDirectoryNode withoutKnownChildren() {
        return new PartialDirectoryNode(EmptyChildMap.getInstance());
    }

    public PartialDirectoryNode(ChildMap<? extends FileSystemNode> children) {
        super(children);
    }

    @Override
    protected FileSystemNode withIncompleteChildren(ChildMap<? extends FileSystemNode> newChildren) {
        return new PartialDirectoryNode(newChildren);
    }

    @Override
    protected Optional<FileSystemNode> withAllChildrenRemoved() {
        return Optional.of(children.isEmpty() ? this : withoutKnownChildren());
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot() {
        return Optional.of(MetadataSnapshot.DIRECTORY);
    }

    @Override
    protected FileSystemNode withIncompleteChildren() {
        return this;
    }
}
