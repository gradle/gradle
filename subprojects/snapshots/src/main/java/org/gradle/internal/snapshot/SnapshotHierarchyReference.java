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

package org.gradle.internal.snapshot;

import org.gradle.internal.vfs.SnapshotHierarchy;
import org.gradle.internal.vfs.impl.ChangeListenerFactory;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

public class SnapshotHierarchyReference {
    private volatile SnapshotHierarchy root;
    private final ReentrantLock updateLock = new ReentrantLock();

    public SnapshotHierarchyReference(SnapshotHierarchy root) {
        this.root = root;
    }

    public SnapshotHierarchy get() {
        return root;
    }

    public void update(UnaryOperator<SnapshotHierarchy> updateFunction, ChangeListenerFactory.LifecycleAwareChangeListener changeListener) {
        updateLock.lock();
        try {
            changeListener.start();
            @SuppressWarnings("UnnecessaryLocalVariable") // Required for atomic volatile operation
            SnapshotHierarchy newRoot = updateFunction.apply(root);
            root = newRoot;
            changeListener.finish();
        } finally {
            updateLock.unlock();
        }
    }
}
