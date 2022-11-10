/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import org.gradle.internal.RelativePathSupplier;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.BiFunction;

import static org.gradle.internal.execution.history.changes.AbstractFingerprintCompareStrategy.FINGERPRINT_CHANGE_FACTORY;

public class OutputFileChanges implements ChangeContainer {

    private final SortedMap<String, FileSystemSnapshot> previous;
    private final SortedMap<String, FileSystemSnapshot> current;

    public OutputFileChanges(SortedMap<String, FileSystemSnapshot> previous, SortedMap<String, FileSystemSnapshot> current) {
        this.previous = previous;
        this.current = current;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        return SortedMapDiffUtil.diff(previous, current, new PropertyDiffListener<String, FileSystemSnapshot, FileSystemSnapshot>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileSystemSnapshot previous, FileSystemSnapshot current) {
                String propertyTitle = "Output property '" + property + "'";
                return visitChangesSince(previous, current, propertyTitle, visitor);
            }
        });
    }

    public boolean visitChangesSince(FileSystemSnapshot previous, FileSystemSnapshot current, String propertyTitle, ChangeVisitor visitor) {
        if (previous == current) {
            return true;
        }
        if (previous == FileSystemSnapshot.EMPTY) {
            return visitAllChanges(current, visitor, (relativePath, snapshot) -> FINGERPRINT_CHANGE_FACTORY.added(
                snapshot.getAbsolutePath(),
                propertyTitle,
                createFingerprint(relativePath, snapshot)
            ));
        } else if (current == FileSystemSnapshot.EMPTY) {
            return visitAllChanges(previous, visitor, (relativePath, snapshot) -> FINGERPRINT_CHANGE_FACTORY.removed(
                snapshot.getAbsolutePath(),
                propertyTitle,
                createFingerprint(relativePath, snapshot)
            ));
        } else {
            FileSystemLocationSnapshot previousSnapshot = (FileSystemLocationSnapshot) previous;
            FileSystemLocationSnapshot currentSnapshot = (FileSystemLocationSnapshot) current;
            if (previousSnapshot.getHash().equals(currentSnapshot.getHash())) {
                return true;
            } else if (currentSnapshot.getType() != FileType.Directory || previousSnapshot.getType() != FileType.Directory) {
                // The hash is different and one of the two snapshots is not a directory falls into cases:
                // 1. The type of the snapshot is different.
                // 2. The content hash of a regular file changed.
                // In any case, the root needs to be reported as changed:
                if (visitor.visitChange(FINGERPRINT_CHANGE_FACTORY.modified(
                    currentSnapshot.getAbsolutePath(),
                    propertyTitle,
                    createRootFingerprint(previousSnapshot),
                    createRootFingerprint(currentSnapshot)
                ))) {
                    // Only one of the types will be a directory, so we now need to report all children of the other one.
                    if (visitAllChildChanges(previousSnapshot, visitor, (relativePath, snapshot) -> FINGERPRINT_CHANGE_FACTORY.removed(
                        snapshot.getAbsolutePath(),
                        propertyTitle,
                        createFingerprint(relativePath, snapshot)
                    ))) {
                        return visitAllChildChanges(currentSnapshot, visitor, (relativePath, snapshot) -> FINGERPRINT_CHANGE_FACTORY.added(
                            snapshot.getAbsolutePath(),
                            propertyTitle,
                            createFingerprint(relativePath, snapshot)
                        ));
                    }
                }
                return false;
            }
        }

        Map<String, FileSystemLocationFingerprint> previousFingerprint = collectFingerprints(previous);
        Map<String, FileSystemLocationFingerprint> currentFingerprint = collectFingerprints(current);

        return NormalizedPathChangeDetector.INSTANCE.visitChangesSince(
            previousFingerprint,
            currentFingerprint,
            propertyTitle,
            visitor);
    }

    private static boolean visitAllChanges(FileSystemSnapshot root, ChangeVisitor visitor, BiFunction<RelativePathSupplier, FileSystemLocationSnapshot, Change> changeFactory) {
        return root.accept(new RelativePathTracker(), (snapshot, relativePath) -> {
            boolean shouldContinue = visitor.visitChange(
                changeFactory.apply(relativePath, snapshot)
            );
            return shouldContinue ? SnapshotVisitResult.CONTINUE : SnapshotVisitResult.TERMINATE;
        }) == SnapshotVisitResult.CONTINUE;
    }

    private static boolean visitAllChildChanges(FileSystemSnapshot root, ChangeVisitor visitor, BiFunction<RelativePathSupplier, FileSystemLocationSnapshot, Change> changeFactory) {
        return root.accept(new RelativePathTracker(), (snapshot, relativePath) -> {
            if (relativePath.isRoot()) {
                return SnapshotVisitResult.CONTINUE;
            }
            boolean shouldContinue = visitor.visitChange(
                changeFactory.apply(relativePath, snapshot)
            );
            return shouldContinue ? SnapshotVisitResult.CONTINUE : SnapshotVisitResult.TERMINATE;
        }) == SnapshotVisitResult.CONTINUE;
    }

    private static Map<String, FileSystemLocationFingerprint> collectFingerprints(FileSystemSnapshot roots) {
        Map<String, FileSystemLocationFingerprint> result = new LinkedHashMap<>();
        roots.accept(new RelativePathTracker(),
            (snapshot, relativePath) -> {
                result.put(snapshot.getAbsolutePath(), createFingerprint(relativePath, snapshot));
                return SnapshotVisitResult.CONTINUE;
            }
        );
        return result;
    }

    private static DefaultFileSystemLocationFingerprint createFingerprint(RelativePathSupplier relativePath, FileSystemLocationSnapshot snapshot) {
        return createFingerprint(relativePath.toRelativePath(), snapshot);
    }

    private static DefaultFileSystemLocationFingerprint createRootFingerprint(FileSystemLocationSnapshot snapshot) {
        return createFingerprint("", snapshot);
    }

    private static DefaultFileSystemLocationFingerprint createFingerprint(String relativePath, FileSystemLocationSnapshot snapshot) {
        return new DefaultFileSystemLocationFingerprint(
            relativePath,
            snapshot.getType(),
            snapshot.getHash()
        );
    }
}
