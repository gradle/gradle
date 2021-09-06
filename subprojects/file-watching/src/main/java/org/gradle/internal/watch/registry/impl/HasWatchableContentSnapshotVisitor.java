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

package org.gradle.internal.watch.registry.impl;

import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;

public class HasWatchableContentSnapshotVisitor implements SnapshotHierarchy.SnapshotVisitor {
    private final WatchableHierarchies watchableHierarchies;
    private boolean empty = true;

    public HasWatchableContentSnapshotVisitor(WatchableHierarchies watchableHierarchies) {
        this.watchableHierarchies = watchableHierarchies;
    }

    @Override
    public void visitSnapshotRoot(FileSystemLocationSnapshot rootSnapshot) {
        if (rootSnapshot.getType() != FileType.Missing
            && !watchableHierarchies.ignoredForWatching(rootSnapshot)) {
            empty = false;
        }
    }

    public boolean isEmpty() {
        return empty;
    }
}
