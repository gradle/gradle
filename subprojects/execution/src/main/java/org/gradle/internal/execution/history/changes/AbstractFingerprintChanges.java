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
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

public abstract class AbstractFingerprintChanges implements ChangeContainer {
    protected final ImmutableSortedMap<String, FileCollectionFingerprint> previous;
    protected final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current;
    private final String title;

    protected AbstractFingerprintChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current, String title) {
        this.previous = previous;
        this.current = current;
        this.title = title;
    }

    protected boolean accept(final ChangeVisitor visitor, final boolean includeAdded) {
        return SortedMapDiffUtil.diff(previous, current, new PropertyDiffListener<String, FileCollectionFingerprint, CurrentFileCollectionFingerprint>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileCollectionFingerprint previousFingerprint, CurrentFileCollectionFingerprint currentFingerprint) {
                String propertyTitle = title + " property '" + property + "'";
                return currentFingerprint.visitChangesSince(previousFingerprint, propertyTitle, includeAdded, visitor);
            }
        });
    }
}
