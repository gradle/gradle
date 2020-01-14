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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.MetadataSnapshot;
import org.gradle.internal.snapshot.VfsRelativePath;
import org.gradle.internal.vfs.SnapshotHierarchy;

import java.util.Optional;

import static org.gradle.internal.snapshot.SnapshotUtil.getSnapshotFromChild;
import static org.gradle.internal.snapshot.SnapshotUtil.invalidateSingleChild;
import static org.gradle.internal.snapshot.SnapshotUtil.storeSingleChild;

public class DefaultSnapshotHierarchy implements SnapshotHierarchy {

    @VisibleForTesting
    final FileSystemNode rootNode;
    private final CaseSensitivity caseSensitivity;

    public static SnapshotHierarchy from(String absolutePath, MetadataSnapshot snapshot, CaseSensitivity caseSensitivity) {
        VfsRelativePath relativePath = VfsRelativePath.of(absolutePath);
        return new DefaultSnapshotHierarchy(snapshot.asFileSystemNode(relativePath.getAsString()), caseSensitivity);
    }

    private DefaultSnapshotHierarchy(FileSystemNode rootNode, CaseSensitivity caseSensitivity) {
        this.rootNode = rootNode;
        this.caseSensitivity = caseSensitivity;
    }

    public static SnapshotHierarchy empty(CaseSensitivity caseSensitivity) {
        switch (caseSensitivity) {
            case CASE_SENSITIVE:
                return EmptySnapshotHierarchy.CASE_SENSITIVE;
            case CASE_INSENSITIVE:
                return EmptySnapshotHierarchy.CASE_INSENSITIVE;
            default:
                throw new AssertionError("Unknown case sensitivity: " + caseSensitivity);
        }
    }

    @Override
    public Optional<MetadataSnapshot> getMetadata(String absolutePath) {
        VfsRelativePath relativePath = VfsRelativePath.of(absolutePath);
        String pathToParent = rootNode.getPathToParent();
        if (!relativePath.hasPrefix(pathToParent, caseSensitivity)) {
            return Optional.empty();
        }
        return getSnapshotFromChild(rootNode, relativePath, caseSensitivity);
    }

    @Override
    public SnapshotHierarchy store(String absolutePath, MetadataSnapshot snapshot) {
        VfsRelativePath relativePath = VfsRelativePath.of(absolutePath);
        return new DefaultSnapshotHierarchy(storeSingleChild(rootNode, relativePath, caseSensitivity, snapshot), caseSensitivity);
    }

    @Override
    public SnapshotHierarchy invalidate(String absolutePath) {
        VfsRelativePath relativePath = VfsRelativePath.of(absolutePath);
        return invalidateSingleChild(rootNode, relativePath, caseSensitivity)
            .<SnapshotHierarchy>map(newRootNode -> new DefaultSnapshotHierarchy(newRootNode, caseSensitivity))
            .orElse(empty());
    }

    @Override
    public SnapshotHierarchy empty() {
        return empty(caseSensitivity);
    }

    @Override
    public void visitSnapshots(SnapshotVisitor snapshotVisitor) {
        rootNode.accept((node, rootOfCompleteHierarchy) -> node.getSnapshot().ifPresent(snapshot -> {
            if (snapshot instanceof CompleteFileSystemLocationSnapshot) {
                snapshotVisitor.visitSnapshot((CompleteFileSystemLocationSnapshot) snapshot, rootOfCompleteHierarchy);
            }
        }), false);
    }

    private enum EmptySnapshotHierarchy implements SnapshotHierarchy {
        CASE_SENSITIVE(CaseSensitivity.CASE_SENSITIVE),
        CASE_INSENSITIVE(CaseSensitivity.CASE_INSENSITIVE);

        private final CaseSensitivity caseSensitivity;

        EmptySnapshotHierarchy(CaseSensitivity caseInsensitive) {
            this.caseSensitivity = caseInsensitive;
        }

        @Override
        public Optional<MetadataSnapshot> getMetadata(String absolutePath) {
            return Optional.empty();
        }

        @Override
        public SnapshotHierarchy store(String absolutePath, MetadataSnapshot snapshot) {
            return from(absolutePath, snapshot, caseSensitivity);
        }

        @Override
        public SnapshotHierarchy invalidate(String absolutePath) {
            return this;
        }

        @Override
        public SnapshotHierarchy empty() {
            return this;
        }

        @Override
        public void visitSnapshots(SnapshotVisitor snapshotVisitor) {}
    }
}
