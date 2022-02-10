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

import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;

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
                if (previous == current) {
                    return true;
                }
                if (previous != FileSystemSnapshot.EMPTY && current != FileSystemSnapshot.EMPTY) {
                    FileSystemLocationSnapshot previousSnapshot = (FileSystemLocationSnapshot) previous;
                    FileSystemLocationSnapshot currentSnapshot = (FileSystemLocationSnapshot) current;
                    if (previousSnapshot.getHash().equals(currentSnapshot.getHash())) {
                        return true;
                    }
                }

                Map<String, FileSystemLocationFingerprint> previousFingerprint = collectFingerprints(previous);
                Map<String, FileSystemLocationFingerprint> currentFingerprint = collectFingerprints(current);

                return NormalizedPathChangeDetector.INSTANCE.visitChangesSince(
                    previousFingerprint,
                    currentFingerprint,
                    "Output property '" + property + "'",
                    visitor);
            }
        });
    }

    private Map<String, FileSystemLocationFingerprint> collectFingerprints(FileSystemSnapshot roots) {
        Map<String, FileSystemLocationFingerprint> result = new LinkedHashMap<>();
        RelativePathTracker pathTracker = new RelativePathTracker();
        roots.accept(pathTracker,
            (snapshot, relativePath) -> {
                result.put(snapshot.getAbsolutePath(),
                    new DefaultFileSystemLocationFingerprint(
                        relativePath.toRelativePath(),
                        snapshot.getType(),
                        snapshot.getHash()
                    ));
                return SnapshotVisitResult.CONTINUE;
            }
        );
        return result;
    }
}
