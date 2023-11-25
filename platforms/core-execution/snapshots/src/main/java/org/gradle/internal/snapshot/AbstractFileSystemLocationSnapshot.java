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

import com.google.common.collect.Interner;
import org.gradle.internal.file.FileMetadata.AccessType;

import java.util.Optional;
import java.util.stream.Stream;

public abstract class AbstractFileSystemLocationSnapshot implements FileSystemLocationSnapshot {
    private final String absolutePath;
    private final String name;
    private final AccessType accessType;

    public AbstractFileSystemLocationSnapshot(String absolutePath, String name, AccessType accessType) {
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
    public Optional<? extends FileSystemLocationSnapshot> relocate(String targetPath, Interner<String> interner) {
        if (accessType == AccessType.VIA_SYMLINK) {
            return Optional.empty();
        }
        String internedTargetPath = interner.intern(targetPath);
        String targetName = PathUtil.getFileName(internedTargetPath);
        String internedTargetName = targetName.equals(name)
            ? name
            : interner.intern(targetName);
        return relocateDirectAccess(internedTargetPath, internedTargetName, interner);
    }

    protected abstract Optional<? extends FileSystemLocationSnapshot> relocateDirectAccess(String targetPath, String internedTargetName, Interner<String> interner);

    @Override
    public AccessType getAccessType() {
        return accessType;
    }

    public String getPathToParent() {
        return getName();
    }

    @Override
    public FileSystemLocationSnapshot store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.NodeDiffListener diffListener) {
        return this;
    }

    @Override
    public Stream<FileSystemLocationSnapshot> rootSnapshots() {
        return Stream.of(this);
    }

    @Override
    public boolean hasDescendants() {
        return true;
    }

    @Override
    public FileSystemNode asFileSystemNode() {
        return this;
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
    public Optional<FileSystemNode> getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return Optional.of(getChildNode(relativePath, caseSensitivity));
    }

    protected FileSystemNode getChildNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return missingSnapshotForAbsolutePath(relativePath.getAbsolutePath());
    }

    @Override
    public Stream<FileSystemLocationSnapshot> roots() {
        return Stream.of(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractFileSystemLocationSnapshot that = (AbstractFileSystemLocationSnapshot) o;

        if (accessType != that.accessType) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        if (!absolutePath.equals(that.absolutePath)) {
            return false;
        }
        return getHash().equals(that.getHash());
    }

    @Override
    public int hashCode() {
        int result = absolutePath.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + accessType.hashCode();
        result = 31 * result + getHash().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
