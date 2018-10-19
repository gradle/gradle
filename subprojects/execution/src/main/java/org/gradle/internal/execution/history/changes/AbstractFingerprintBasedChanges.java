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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.change.ChangeContainer;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

public abstract class AbstractFingerprintBasedChanges implements ChangeContainer {
    protected final PreviousExecutionState previous;
    protected final BeforeExecutionState current;
    private final String title;

    protected AbstractFingerprintBasedChanges(PreviousExecutionState previous, BeforeExecutionState current, String title) {
        this.previous = previous;
        this.current = current;
        this.title = title;
    }

    private ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getPrevious() {
        return getFingerprints(previous);
    }

    private ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getCurrent() {
        return getFingerprints(current);
    }

    protected abstract ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getFingerprints(ExecutionState execution);

    protected boolean accept(final ChangeVisitor visitor, final boolean includeAdded) {
        return SortedMapDiffUtil.diff(getPrevious(), getCurrent(), new PropertyDiffListener<String, FileCollectionFingerprint>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileCollectionFingerprint previousFingerprint, FileCollectionFingerprint currentFingerprint) {
                String propertyTitle = title + " property '" + property + "'";
                return currentFingerprint.visitChangesSince(previousFingerprint, propertyTitle, includeAdded, visitor);
            }
        });
    }
}
