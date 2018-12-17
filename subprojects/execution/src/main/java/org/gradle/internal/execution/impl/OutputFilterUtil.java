/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters out fingerprints that are not considered outputs. Entries that are considered outputs are:
 * <ul>
 * <li>an entry that did not exist before the execution, but exists after the execution</li>
 * <li>an entry that did exist before the execution, and has been changed during the execution</li>
 * <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
 * </ul>
 */
public class OutputFilterUtil {

    public static ImmutableSortedMap<String, CurrentFileCollectionFingerprint> filterOutputFingerprints(
        final @Nullable ImmutableSortedMap<String, FileCollectionFingerprint> outputsAfterPreviousExecution,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputsBeforeExecution,
        final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputsAfterExecution
    ) {
        return ImmutableSortedMap.copyOfSorted(Maps.transformEntries(outputsBeforeExecution, new Maps.EntryTransformer<String, CurrentFileCollectionFingerprint, CurrentFileCollectionFingerprint>() {
            @Override
            @SuppressWarnings("NullableProblems")
            public CurrentFileCollectionFingerprint transformEntry(String propertyName, CurrentFileCollectionFingerprint outputBeforeExecution) {
                CurrentFileCollectionFingerprint outputAfterExecution = outputsAfterExecution.get(propertyName);
                FileCollectionFingerprint outputAfterPreviousExecution = getFingerprintForProperty(outputsAfterPreviousExecution, propertyName);
                return filterOutputFingerprint(outputAfterPreviousExecution, outputBeforeExecution, outputAfterExecution);
            }
        }));
    }

    private static FileCollectionFingerprint getFingerprintForProperty(@Nullable ImmutableSortedMap<String, FileCollectionFingerprint> fingerprinters, String propertyName) {
        if (fingerprinters != null) {
            FileCollectionFingerprint afterPreviousExecution = fingerprinters.get(propertyName);
            if (afterPreviousExecution != null) {
                return afterPreviousExecution;
            }
        }
        return FileCollectionFingerprint.EMPTY;
    }

    @VisibleForTesting
    static CurrentFileCollectionFingerprint filterOutputFingerprint(
            @Nullable FileCollectionFingerprint afterPreviousExecution,
            CurrentFileCollectionFingerprint beforeExecution,
            CurrentFileCollectionFingerprint afterExecution
    ) {
        CurrentFileCollectionFingerprint filesFingerprint;
        final Map<String, FileSystemLocationSnapshot> beforeExecutionSnapshots = getAllSnapshots(beforeExecution);
        if (!beforeExecution.getFingerprints().isEmpty() && !afterExecution.getFingerprints().isEmpty()) {
            @SuppressWarnings("RedundantTypeArguments")
            final Map<String, FileSystemLocationFingerprint> afterPreviousFingerprints = afterPreviousExecution != null
                ? afterPreviousExecution.getFingerprints()
                : ImmutableMap.<String, FileSystemLocationFingerprint>of();

            final List<FileSystemSnapshot> newRoots = new ArrayList<FileSystemSnapshot>();
            final MutableBoolean hasBeenFiltered = new MutableBoolean(false);

            afterExecution.accept(new FileSystemSnapshotVisitor() {
                private MerkleDirectorySnapshotBuilder merkleBuilder;
                private boolean currentRootFiltered = false;
                private DirectorySnapshot currentRoot;

                @Override
                public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                    if (merkleBuilder == null) {
                        merkleBuilder = MerkleDirectorySnapshotBuilder.noSortingRequired();
                        currentRoot = directorySnapshot;
                        currentRootFiltered = false;
                    }
                    merkleBuilder.preVisitDirectory(directorySnapshot);
                    return true;
                }

                @Override
                public void visit(FileSystemLocationSnapshot fileSnapshot) {
                    if (!isOutputEntry(fileSnapshot, beforeExecutionSnapshots, afterPreviousFingerprints)) {
                        hasBeenFiltered.set(true);
                        currentRootFiltered = true;
                        return;
                    }
                    if (merkleBuilder == null) {
                        newRoots.add(fileSnapshot);
                    } else {
                        merkleBuilder.visit(fileSnapshot);
                    }
                }

                @Override
                public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                    boolean isOutputDir = isOutputEntry(directorySnapshot, beforeExecutionSnapshots, afterPreviousFingerprints);
                    boolean includedDir = merkleBuilder.postVisitDirectory(isOutputDir);
                    if (!includedDir) {
                        currentRootFiltered = true;
                        hasBeenFiltered.set(true);
                    }
                    if (merkleBuilder.isRoot()) {
                        FileSystemLocationSnapshot result = merkleBuilder.getResult();
                        if (result != null) {
                            newRoots.add(currentRootFiltered ? result : currentRoot);
                        }
                        merkleBuilder = null;
                        currentRoot = null;
                    }
                }
            });


            // Are all file snapshots after execution accounted for as new entries?
            if (!hasBeenFiltered.get()) {
                filesFingerprint = afterExecution;
            } else {
                filesFingerprint = DefaultCurrentFileCollectionFingerprint.from(newRoots, AbsolutePathFingerprintingStrategy.IGNORE_MISSING);
            }
        } else {
            filesFingerprint = afterExecution;
        }
        return filesFingerprint;
    }

    private static Map<String, FileSystemLocationSnapshot> getAllSnapshots(CurrentFileCollectionFingerprint fingerprint) {
        GetAllSnapshotsVisitor afterExecutionVisitor = new GetAllSnapshotsVisitor();
        fingerprint.accept(afterExecutionVisitor);
        return afterExecutionVisitor.getSnapshots();
    }

    /**
     * Decide whether an entry should be considered to be part of the output. See class Javadoc for definition of what is considered output.
     */
    private static boolean isOutputEntry(FileSystemLocationSnapshot snapshot, Map<String, FileSystemLocationSnapshot> beforeSnapshots, Map<String, FileSystemLocationFingerprint> afterPreviousFingerprints) {
        if (snapshot.getType() == FileType.Missing) {
            return false;
        }
        FileSystemLocationSnapshot beforeSnapshot = beforeSnapshots.get(snapshot.getAbsolutePath());
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!snapshot.isContentAndMetadataUpToDate(beforeSnapshot)) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        return afterPreviousFingerprints.containsKey(snapshot.getAbsolutePath());
    }

    private static class GetAllSnapshotsVisitor implements FileSystemSnapshotVisitor {
        private final Map<String, FileSystemLocationSnapshot> snapshots = new HashMap<String, FileSystemLocationSnapshot>();

        @Override
        public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
            snapshots.put(directorySnapshot.getAbsolutePath(), directorySnapshot);
            return true;
        }

        @Override
        public void visit(FileSystemLocationSnapshot fileSnapshot) {
            snapshots.put(fileSnapshot.getAbsolutePath(), fileSnapshot);
        }

        @Override
        public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
        }

        public Map<String, FileSystemLocationSnapshot> getSnapshots() {
            return snapshots;
        }
    }
}
