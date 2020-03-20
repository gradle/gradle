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

import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.vfs.SnapshotHierarchy;

public interface ChangeListenerFactory {
    ChangeListenerFactory NOOP = () -> LifecycleAwareChangeListener.NOOP;

    LifecycleAwareChangeListener newChangeListener();

    interface LifecycleAwareChangeListener extends SnapshotHierarchy.ChangeListener {
        DelegatingChangeListenerFactory.LifecycleAwareChangeListener NOOP = new DelegatingChangeListenerFactory.LifecycleAwareChangeListener() {
            @Override
            public void start() {
            }

            @Override
            public void finish() {
            }

            @Override
            public void nodeRemoved(FileSystemNode node) {
            }

            @Override
            public void nodeAdded(FileSystemNode node) {
            }
        };

        void start();
        void finish();
    }
}
