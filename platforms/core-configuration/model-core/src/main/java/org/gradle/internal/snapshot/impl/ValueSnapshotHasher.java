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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.ImmutableSortedMap.copyOfSorted;
import static com.google.common.collect.Maps.transformValues;

/**
 * Hashes value snapshots so that two snapshots have the same hash exactly when they are equal.
 *
 * <p>{@link ValueSnapshot#appendToHasher(Hasher)} feeds the build cache key and may conflate snapshots that
 * {@code equals()} tells apart, for example a short and an int of the same value. This hasher includes the
 * snapshot type at every level, hashes strings by UTF-16 code units, canonicalizes NaNs and ignores the
 * iteration order of sets. The execution history uses it to keep only the hash of each input value.</p>
 */
public final class ValueSnapshotHasher {
    private ValueSnapshotHasher() {
    }

    public static HashCode hash(ValueSnapshot snapshot) {
        Hasher hasher = Hashing.newHasher();
        appendToHasher(hasher, snapshot);
        return hasher.hash();
    }

    public static ImmutableSortedMap<String, HashCode> hashAll(ImmutableSortedMap<String, ValueSnapshot> snapshots) {
        return copyOfSorted(transformValues(snapshots, ValueSnapshotHasher::hash));
    }

    public static void appendToHasher(Hasher hasher, ValueSnapshot snapshot) {
        hasher.putString(snapshot.getClass().getName());
        if (snapshot instanceof StringValueSnapshot) {
            putString(hasher, ((StringValueSnapshot) snapshot).getValue());
        } else if (snapshot instanceof FileValueSnapshot) {
            putString(hasher, ((FileValueSnapshot) snapshot).getValue());
        } else if (snapshot instanceof AttributeDefinitionSnapshot) {
            // The content hash encodes the name as UTF-8; the type fingerprint it adds is kept as it is
            putString(hasher, ((AttributeDefinitionSnapshot) snapshot).getValue().getName());
            snapshot.appendToHasher(hasher);
        } else if (snapshot instanceof EnumValueSnapshot) {
            EnumValueSnapshot enumSnapshot = (EnumValueSnapshot) snapshot;
            putString(hasher, enumSnapshot.getClassName());
            putString(hasher, enumSnapshot.getName());
        } else if (snapshot instanceof ImmutableManagedValueSnapshot) {
            ImmutableManagedValueSnapshot managedSnapshot = (ImmutableManagedValueSnapshot) snapshot;
            putString(hasher, managedSnapshot.getClassName());
            putNullableString(hasher, managedSnapshot.getValue());
        } else if (snapshot instanceof ManagedValueSnapshot) {
            ManagedValueSnapshot managedSnapshot = (ManagedValueSnapshot) snapshot;
            putString(hasher, managedSnapshot.getClassName());
            appendToHasher(hasher, managedSnapshot.getState());
        } else if (snapshot instanceof ListValueSnapshot) {
            appendElements(hasher, ((ListValueSnapshot) snapshot).getElements());
        } else if (snapshot instanceof ArrayValueSnapshot) {
            appendElements(hasher, ((ArrayValueSnapshot) snapshot).getElements());
        } else if (snapshot instanceof SetValueSnapshot) {
            appendUnorderedElements(hasher, ((SetValueSnapshot) snapshot).getElements());
        } else if (snapshot instanceof MapValueSnapshot) {
            List<MapEntrySnapshot<ValueSnapshot>> entries = ((MapValueSnapshot) snapshot).getEntries();
            hasher.putInt(entries.size());
            for (MapEntrySnapshot<ValueSnapshot> entry : entries) {
                appendToHasher(hasher, entry.getKey());
                appendToHasher(hasher, entry.getValue());
            }
        } else if (snapshot instanceof ArrayOfPrimitiveValueSnapshot) {
            ((ArrayOfPrimitiveValueSnapshot) snapshot).appendCanonicalContentToHasher(hasher);
        } else if (snapshot instanceof JavaSerializedValueSnapshot) {
            JavaSerializedValueSnapshot serializedSnapshot = (JavaSerializedValueSnapshot) snapshot;
            putNullableHash(hasher, serializedSnapshot.getImplementationHash());
            hasher.putBytes(serializedSnapshot.getValue());
        } else if (snapshot instanceof GradleSerializedValueSnapshot) {
            GradleSerializedValueSnapshot serializedSnapshot = (GradleSerializedValueSnapshot) snapshot;
            putNullableHash(hasher, serializedSnapshot.getImplementationHash());
            hasher.putBytes(serializedSnapshot.getValue());
        } else if (snapshot instanceof AbstractScalarValueSnapshot || snapshot instanceof NullValueSnapshot || snapshot instanceof ImplementationSnapshot) {
            // Content hash of these types is already as fine-grained as equals() once the type is known
            snapshot.appendToHasher(hasher);
        } else {
            throw new IllegalArgumentException("Don't know how to hash a value snapshot of type " + snapshot.getClass().getName());
        }
    }

    private static void appendElements(Hasher hasher, Collection<ValueSnapshot> elements) {
        hasher.putInt(elements.size());
        for (ValueSnapshot element : elements) {
            appendToHasher(hasher, element);
        }
    }

    /**
     * Set equality ignores the iteration order, so the element hashes are sorted before they are combined.
     */
    private static void appendUnorderedElements(Hasher hasher, Collection<ValueSnapshot> elements) {
        hasher.putInt(elements.size());
        List<HashCode> elementHashes = new ArrayList<>(elements.size());
        for (ValueSnapshot element : elements) {
            elementHashes.add(hash(element));
        }
        Collections.sort(elementHashes);
        for (HashCode elementHash : elementHashes) {
            hasher.putHash(elementHash);
        }
    }

    /**
     * Hashes the UTF-16 code units. {@link Hasher#putString(CharSequence)} encodes as UTF-8, which maps every
     * unpaired surrogate to the same replacement and so cannot tell such strings apart.
     */
    private static void putString(Hasher hasher, String value) {
        byte[] codeUnits = new byte[value.length() * 2];
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            codeUnits[2 * i] = (byte) (c >> 8);
            codeUnits[2 * i + 1] = (byte) c;
        }
        hasher.putBytes(codeUnits);
    }

    /**
     * A null marker alone is not enough: {@link Hasher#putNull()} produces the same bytes as some short values.
     */
    private static void putNullableString(Hasher hasher, @Nullable String value) {
        hasher.putBoolean(value != null);
        if (value != null) {
            putString(hasher, value);
        }
    }

    private static void putNullableHash(Hasher hasher, @Nullable HashCode value) {
        hasher.putBoolean(value != null);
        if (value != null) {
            hasher.putHash(value);
        }
    }
}
