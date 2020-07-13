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

package org.gradle.internal.watch.vfs.impl;

import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.vfs.VirtualFileSystem;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public class RecentlyCapturedSnapshots implements VirtualFileSystem.RecentlyCreatedSnapshotsListener {
    private final AtomicReference<FileHierarchySet> producedByCurrentBuild = new AtomicReference<>(DefaultFileHierarchySet.of());
    private volatile boolean buildRunning;

    public void snapshotsCreated(Iterable<String> locations) {
        if (buildRunning) {
            producedByCurrentBuild.updateAndGet(currentValue -> {
                FileHierarchySet newValue = currentValue;
                for (String location : locations) {
                    newValue = newValue.plus(new File(location));
                }
                return newValue;
            });
        }
    }

    public boolean canBeInvalidatedByFileSystemEvents(String location) {
        return !producedByCurrentBuild.get().contains(location);
    }

    public void buildStarted() {
        producedByCurrentBuild.set(DefaultFileHierarchySet.of());
        buildRunning = true;
    }

    public void buildFinished() {
        buildRunning = false;
        producedByCurrentBuild.set(DefaultFileHierarchySet.of());
    }
}
