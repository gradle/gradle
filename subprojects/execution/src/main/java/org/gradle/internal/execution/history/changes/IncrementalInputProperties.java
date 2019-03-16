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
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

public interface IncrementalInputProperties {
    String getPropertyNameFor(Object value);
    InputFileChanges nonIncrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current);
    InputFileChanges incrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current);

    IncrementalInputProperties NONE = new IncrementalInputProperties() {
        @Override
        public String getPropertyNameFor(Object value) {
            throw new InvalidUserDataException("Cannot query incremental changes for property " + value + ": No incremental properties declared.");
        }

        @Override
        public InputFileChanges nonIncrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            return new DefaultInputFileChanges(previous, current);
        }

        @Override
        public InputFileChanges incrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            return InputFileChanges.EMPTY;
        }
    };

    IncrementalInputProperties ALL = new IncrementalInputProperties() {
        @Override
        public String getPropertyNameFor(Object value) {
            throw new InvalidUserDataException("Cannot query incremental changes for property " + value + ": Requires using 'InputChanges'.");
        }

        @Override
        public InputFileChanges nonIncrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            return InputFileChanges.EMPTY;
        }

        @Override
        public InputFileChanges incrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            return new DefaultInputFileChanges(previous, current);
        }
    };
}
