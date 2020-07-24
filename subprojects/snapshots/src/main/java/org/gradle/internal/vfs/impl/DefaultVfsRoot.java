/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.snapshot.MetadataSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.SnapshotVisitor;
import org.gradle.internal.vfs.VfsRoot;

import java.util.Optional;

public class DefaultVfsRoot implements VfsRoot {

    private SnapshotHierarchy delegate;
    private final SnapshotHierarchy.UpdateFunctionRunner updateFunctionRunner;

    public DefaultVfsRoot(SnapshotHierarchy delegate, SnapshotHierarchy.UpdateFunctionRunner updateFunctionRunner) {
        this.delegate = delegate;
        this.updateFunctionRunner = updateFunctionRunner;
    }

    @Override
    public Optional<MetadataSnapshot> getMetadata(String absolutePath) {
        return delegate.getMetadata(absolutePath);
    }

    @Override
    public void store(String absolutePath, MetadataSnapshot snapshot) {
        runUpdateFunction((root, diffListener) -> root.store(absolutePath, snapshot, diffListener));
    }

    @Override
    public void invalidate(String absolutePath) {
        runUpdateFunction((root, diffListener) -> root.invalidate(absolutePath, diffListener));
    }

    @Override
    public void invalidateAll() {
        delegate = updateFunctionRunner.runUpdateFunction((currentRoot, diffListener) -> {
            if (diffListener != SnapshotHierarchy.NodeDiffListener.NOOP) {
                currentRoot.visitSnapshotRoots(diffListener::nodeRemoved);
            }
            return currentRoot.empty();
        }, delegate);
    }

    @Override
    public void visitSnapshotRoots(SnapshotVisitor snapshotVisitor) {
        delegate.visitSnapshotRoots(snapshotVisitor);
    }

    private void runUpdateFunction(SnapshotHierarchy.UpdateFunction updateFunction) {
        delegate = updateFunctionRunner.runUpdateFunction(updateFunction, delegate);
    }

    public SnapshotHierarchy getDelegate() {
        return delegate;
    }
}
