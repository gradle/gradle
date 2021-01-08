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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;

public interface InputFingerprinter {
    Result fingerprintInputProperties(
        UnitOfWork work,
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
        ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFingerprints,
        InputPropertyPredicate filter
    );

    static <K extends Comparable<?>, V> ImmutableSortedMap<K, V> union(
        ImmutableSortedMap<K, V> a,
        ImmutableSortedMap<K, V> b
    ) {
        if (a.isEmpty()) {
            return b;
        } else if (b.isEmpty()) {
            return a;
        } else {
            return ImmutableSortedMap.<K, V>naturalOrder()
                .putAll(a)
                .putAll(b)
                .build();
        }
    }

    interface InputPropertyPredicate {
        boolean include(String propertyName, UnitOfWork.InputPropertyType type, UnitOfWork.IdentityKind identity);
    }

    interface Result {
        ImmutableSortedMap<String, ValueSnapshot> getValueSnapshots();
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFileFingerprints();
    }
}
