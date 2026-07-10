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

package org.gradle.internal.snapshot

import org.gradle.internal.RelativePathSupplier

import static org.gradle.internal.snapshot.SnapshotVisitResult.CONTINUE

class SnapshotVisitorUtil {
    static List<String> getAbsolutePaths(FileSystemSnapshot snapshot, boolean includeRoots = false) {
        def absolutePaths = []
        snapshot.accept(new RootTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            SnapshotVisitResult visitEntry(FileSystemLocationSnapshot entrySnapshot, boolean isRoot) {
                if (includeRoots || !isRoot) {
                    absolutePaths << entrySnapshot.absolutePath
                }
                CONTINUE
            }
        })
        return absolutePaths
    }

    static List<String> getRelativePaths(FileSystemSnapshot snapshot, boolean includeRoots = false) {
        def visitor = new RelativePathVisitor(includeRoots)
        snapshot.accept(new RelativePathTracker(), visitor)
        return visitor.relativePaths
    }

    private static class RelativePathVisitor implements RelativePathTrackingFileSystemSnapshotHierarchyVisitor {
        private final boolean includeRoots
        List<String> relativePaths = []

        RelativePathVisitor(boolean includeRoots) {
            this.includeRoots = includeRoots
        }

        @Override
        SnapshotVisitResult visitEntry(FileSystemLocationSnapshot entrySnapshot, RelativePathSupplier relativePath) {
            if (includeRoots || !relativePath.root) {
                relativePaths << relativePath.toRelativePath()
            }
            CONTINUE
        }
    }
}
