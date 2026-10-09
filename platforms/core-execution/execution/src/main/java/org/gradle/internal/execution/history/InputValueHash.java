/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution.history;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

/**
 * Hashes a non-file input value for the execution history.
 *
 * The snapshot type is part of the hash: {@link ValueSnapshot#appendToHasher(Hasher)} alone does not
 * distinguish some snapshot types with the same content (for example a short and an int of the same value),
 * but a change of the value's type must still make the work out of date.
 * The build cache key is calculated separately and is not affected.
 */
public final class InputValueHash {
    private InputValueHash() {
    }

    public static HashCode of(ValueSnapshot snapshot) {
        Hasher hasher = Hashing.newHasher();
        hasher.putString(snapshot.getClass().getName());
        snapshot.appendToHasher(hasher);
        return hasher.hash();
    }

    public static ImmutableSortedMap<String, HashCode> ofAll(ImmutableSortedMap<String, ValueSnapshot> snapshots) {
        return copyOfSorted(transformValues(snapshots, InputValueHash::of));
    }
}
