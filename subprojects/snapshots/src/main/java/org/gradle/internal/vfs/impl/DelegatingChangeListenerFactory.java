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
import org.gradle.internal.vfs.VirtualFileSystem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DelegatingChangeListenerFactory implements ChangeListenerFactory {

    private VirtualFileSystem.VirtualFileSystemChangeListener vfsChangeListener;

    public void setVfsChangeListener(@Nullable VirtualFileSystem.VirtualFileSystemChangeListener vfsChangeListener) {
        this.vfsChangeListener = vfsChangeListener;
    }

    public LifecycleAwareChangeListener newChangeListener() {
        VirtualFileSystem.VirtualFileSystemChangeListener currentListener = vfsChangeListener;
        if (currentListener == null) {
            return LifecycleAwareChangeListener.NOOP;
        }

        return new DefaultLifecycleAwareChangeListener(currentListener);
    }

    private static class DefaultLifecycleAwareChangeListener implements LifecycleAwareChangeListener {
        private final List<FileSystemNode> removedNodes;
        private final List<FileSystemNode> addedNodes;
        private final VirtualFileSystem.VirtualFileSystemChangeListener currentListener;

        public DefaultLifecycleAwareChangeListener(VirtualFileSystem.VirtualFileSystemChangeListener currentListener) {
            this.currentListener = currentListener;
            removedNodes = new ArrayList<>();
            addedNodes = new ArrayList<>();
        }

        @Override
        public void start() {
            removedNodes.clear();
            addedNodes.clear();
        }

        @Override
        public void finish() {
            currentListener.changed(removedNodes, addedNodes);
        }

        @Override
        public void nodeRemoved(FileSystemNode node) {
            removedNodes.add(node);
        }

        @Override
        public void nodeAdded(FileSystemNode node) {
            addedNodes.add(node);
        }
    }
}
