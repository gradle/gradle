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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

import java.util.SortedMap;

public class DefaultInputFileChanges extends AbstractFingerprintChanges implements InputFileChanges {
    private static final String TITLE = "Input";

    public DefaultInputFileChanges(SortedMap<String, FileCollectionFingerprint> previous, SortedMap<String, CurrentFileCollectionFingerprint> current) {
        super(previous, current, TITLE);
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        return accept(visitor, true);
    }

    @Override
    public boolean accept(String propertyName, ChangeVisitor visitor) {
        CurrentFileCollectionFingerprint currentFileCollectionFingerprint = current.get(propertyName);
        FileCollectionFingerprint previousFileCollectionFingerprint = previous.get(propertyName);
        return currentFileCollectionFingerprint.visitChangesSince(previousFileCollectionFingerprint, TITLE, true, visitor);
    }

    public InputFileChanges nonIncrementalChanges(ImmutableSet<String> incrementalPropertyNames) {
        if (incrementalPropertyNames.isEmpty()) {
            return this;
        }
        if (current.keySet().equals(incrementalPropertyNames)) {
            return InputFileChanges.EMPTY;
        }

        return new DefaultInputFileChanges(
            Maps.filterKeys(previous, propertyName -> !incrementalPropertyNames.contains(propertyName)),
            Maps.filterKeys(current, propertyName -> !incrementalPropertyNames.contains(propertyName))
        );
    }

    public InputFileChanges incrementalChanges(ImmutableSet<String> incrementalPropertyNames) {
        if (incrementalPropertyNames.isEmpty()) {
            return InputFileChanges.EMPTY;
        }
        if (current.keySet().equals(incrementalPropertyNames)) {
            return this;
        }

        return new DefaultInputFileChanges(
            ImmutableSortedMap.copyOfSorted(Maps.filterKeys(previous, propertyName -> incrementalPropertyNames.contains(propertyName))),
            ImmutableSortedMap.copyOfSorted(Maps.filterKeys(current, propertyName -> incrementalPropertyNames.contains(propertyName)))
        );
    }
}
