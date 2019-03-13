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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Cast;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.CollectingChangeVisitor;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.gradle.internal.execution.history.changes.IncrementalInputChanges.determinePropertyName;

public class NonIncrementalInputChanges implements InputChangesInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonIncrementalInputChanges.class);

    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs;
    private final ImmutableListMultimap<Object, String> propertyNameByValue;

    public NonIncrementalInputChanges(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> currentInputs, ImmutableListMultimap<Object, String> propertyNamesByValue) {
        this.currentInputs = currentInputs;
        this.propertyNameByValue = propertyNamesByValue;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public Iterable<InputFileDetails> getFileChanges(Object property) {
        CollectingChangeVisitor visitor = new CollectingChangeVisitor();
        CurrentFileCollectionFingerprint currentFileCollectionFingerprint = currentInputs.get(determinePropertyName(property, propertyNameByValue));
        visitAllFileChanges(currentFileCollectionFingerprint, visitor);
        return Cast.uncheckedNonnullCast(visitor.getChanges());
    }

    @Override
    public Iterable<Change> getAllFileChanges() {
        CollectingChangeVisitor changeVisitor = new CollectingChangeVisitor();
        for (CurrentFileCollectionFingerprint fingerprint : currentInputs.values()) {
            visitAllFileChanges(fingerprint, changeVisitor);
        }
        return changeVisitor.getChanges();
    }

    private void visitAllFileChanges(CurrentFileCollectionFingerprint currentFileCollectionFingerprint, CollectingChangeVisitor visitor) {
        currentFileCollectionFingerprint.visitChangesSince(FileCollectionFingerprint.EMPTY, "Input", true, visitor);
    }
}
