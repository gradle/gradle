/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Cast;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;

import java.io.File;
import java.util.stream.Stream;

public class NonIncrementalInputChanges implements InputChangesInternal {
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs;
    private final IncrementalInputProperties incrementalInputProperties;

    public NonIncrementalInputChanges(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs, IncrementalInputProperties incrementalInputProperties) {
        this.currentInputs = currentInputs;
        this.incrementalInputProperties = incrementalInputProperties;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public Iterable<FileChange> getFileChanges(Object parameterValue) {
        CurrentFileCollectionFingerprint currentFileCollectionFingerprint = currentInputs.get(incrementalInputProperties.getPropertyNameFor(parameterValue));
        return () -> getAllFileChanges(currentFileCollectionFingerprint).iterator();
    }

    @Override
    public Iterable<InputFileDetails> getAllFileChanges() {
        Iterable<FileChange> changes = () -> currentInputs.values().stream().flatMap(NonIncrementalInputChanges::getAllFileChanges).iterator();
        return Cast.uncheckedNonnullCast(changes);
    }

    private static Stream<FileChange> getAllFileChanges(CurrentFileCollectionFingerprint currentFileCollectionFingerprint) {
        return currentFileCollectionFingerprint.getFingerprints().keySet().stream().map(RebuildFileChange::new);
    }

    private static class RebuildFileChange implements FileChange, InputFileDetails {
        private final String path;

        public RebuildFileChange(String path) {
            this.path = path;
        }

        @Override
        public File getFile() {
            return new File(path);
        }

        @Override
        public ChangeType getChangeType() {
            return ChangeType.ADDED;
        }

        @Override
        public boolean isAdded() {
            return false;
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }
    }
}
