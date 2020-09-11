/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.file.FileMetadata.AccessType;

import java.util.Optional;

public abstract class AbstractCompleteFileSystemLocationSnapshot implements CompleteFileSystemLocationSnapshot {
    private final String absolutePath;
    private final String name;
    private final AccessType accessType;

    public AbstractCompleteFileSystemLocationSnapshot(String absolutePath, String name, AccessType accessType) {
        this.absolutePath = absolutePath;
        this.name = name;
        this.accessType = accessType;
    }

    protected static MissingFileSnapshot missingSnapshotForAbsolutePath(String filePath) {
        return new MissingFileSnapshot(filePath, AccessType.DIRECT);
    }

    @Override
    public String getAbsolutePath() {
        return absolutePath;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AccessType getAccessType() {
        return accessType;
    }

    @Override
    public String getPathToParent() {
        return getName();
    }

    @Override
    public CompleteFileSystemLocationSnapshot store(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.NodeDiffListener diffListener) {
        return this;
    }

    @Override
    public void accept(SnapshotHierarchy.SnapshotVisitor snapshotVisitor) {
        snapshotVisitor.visitSnapshotRoot(this);
    }

    @Override
    public boolean hasDescendants() {
        return true;
    }

    @Override
    public FileSystemNode asFileSystemNode(String pathToParent) {
        return getPathToParent().equals(pathToParent)
            ? this
            : new PathCompressingSnapshotWrapper(pathToParent, this);
    }

    @Override
    public FileSystemNode withPathToParent(String newPathToParent) {
        return getPathToParent().equals(newPathToParent)
            ? this
            : new PathCompressingSnapshotWrapper(newPathToParent, this);
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot() {
        return Optional.of(this);
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return getChildSnapshot(relativePath, caseSensitivity);
    }

    protected Optional<MetadataSnapshot> getChildSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return Optional.of(missingSnapshotForAbsolutePath(relativePath.getAbsolutePath()));
    }

    @Override
    public ReadOnlyFileSystemNode getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return getChildNode(relativePath, caseSensitivity);
    }

    protected ReadOnlyFileSystemNode getChildNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return missingSnapshotForAbsolutePath(relativePath.getAbsolutePath());
    }

    /**
     * A wrapper that changes the relative path of the snapshot to something different.
     *
     * It delegates everything to the wrapped complete file system location snapshot.
     */
    private static class PathCompressingSnapshotWrapper extends AbstractFileSystemNode {
        private final AbstractCompleteFileSystemLocationSnapshot delegate;

        public PathCompressingSnapshotWrapper(String pathToParent, AbstractCompleteFileSystemLocationSnapshot delegate) {
            super(pathToParent);
            this.delegate = delegate;
        }

        @Override
        public Optional<FileSystemNode> invalidate(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
            return delegate.invalidate(relativePath, caseSensitivity, diffListener).map(splitSnapshot -> splitSnapshot.withPathToParent(getPathToParent()));
        }

        @Override
        public FileSystemNode store(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, MetadataSnapshot newSnapshot, SnapshotHierarchy.NodeDiffListener diffListener) {
            return this;
        }

        @Override
        public Optional<MetadataSnapshot> getSnapshot() {
            return delegate.getSnapshot();
        }

        @Override
        public Optional<MetadataSnapshot> getSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
            return delegate.getSnapshot(relativePath, caseSensitivity);
        }

        @Override
        public boolean hasDescendants() {
            return delegate.hasDescendants();
        }

        @Override
        public ReadOnlyFileSystemNode getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
            return delegate.getNode(relativePath, caseSensitivity);
        }

        @Override
        public FileSystemNode withPathToParent(String newPathToParent) {
            return getPathToParent().equals(newPathToParent)
                ? this
                : delegate.asFileSystemNode(newPathToParent);
        }

        @Override
        public void accept(SnapshotHierarchy.SnapshotVisitor snapshotVisitor) {
            delegate.accept(snapshotVisitor);
        }
    }
}
