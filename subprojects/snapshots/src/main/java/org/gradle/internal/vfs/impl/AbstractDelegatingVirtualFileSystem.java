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

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.vfs.SnapshotHierarchy;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

abstract class AbstractDelegatingVirtualFileSystem extends AbstractVirtualFileSystem {
    private final AbstractVirtualFileSystem delegate;

    public AbstractDelegatingVirtualFileSystem(AbstractVirtualFileSystem delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor) {
        return delegate.readRegularFileContentHash(location, visitor);
    }

    @Override
    public <T> T read(String location, Function<CompleteFileSystemLocationSnapshot, T> visitor) {
        return delegate.read(location, visitor);
    }

    @Override
    public void read(String location, SnapshottingFilter filter, Consumer<CompleteFileSystemLocationSnapshot> visitor) {
        delegate.read(location, filter, visitor);
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        delegate.update(locations, action);
    }

    @Override
    public void invalidateAll() {
        delegate.invalidateAll();
    }

    @Override
    public void updateWithKnownSnapshot(CompleteFileSystemLocationSnapshot snapshot) {
        delegate.updateWithKnownSnapshot(snapshot);
    }

    @Override
    SnapshotHierarchy getRoot() {
        return delegate.getRoot();
    }
}
