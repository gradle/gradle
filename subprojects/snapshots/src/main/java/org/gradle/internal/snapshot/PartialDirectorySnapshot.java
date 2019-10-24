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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.file.FileType;

import java.util.List;
import java.util.Optional;

public class PartialDirectorySnapshot extends AbstractFileSystemNodeWithChildren implements MetadataSnapshot {

    public PartialDirectorySnapshot(String prefix, List<? extends FileSystemNode> children) {
        super(prefix, children);
    }

    @Override
    protected Optional<MetadataSnapshot> getThisSnapshot() {
        return Optional.of(this);
    }

    @Override
    protected FileSystemNode createCopy(String prefix, List<? extends FileSystemNode> newChildren) {
        return new PartialDirectorySnapshot(prefix, newChildren);
    }

    @Override
    protected Optional<FileSystemNode> withNoChildren() {
        return Optional.of(new PartialDirectorySnapshot(getPrefix(), ImmutableList.of()));
    }

    @Override
    protected FileSystemNode withUnkownChildInvalidated() {
        return this;
    }

    @Override
    public FileType getType() {
        return FileType.Directory;
    }
}
