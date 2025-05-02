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

package org.gradle.api.internal.file;

import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;

public class TestVirtualFileSystem extends AbstractVirtualFileSystem {

    public TestVirtualFileSystem(SnapshotHierarchy root) {
        super(root);
    }

    @Override
    protected SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction) {
        return updateFunction.update(SnapshotHierarchy.NodeDiffListener.NOOP);
    }

    public void setRoot(SnapshotHierarchy newRoot) {
        updateRootUnderLock(root -> newRoot);
    }

    public SnapshotHierarchy getRoot() {
        return root;
    }
}
