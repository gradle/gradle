/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provider.provenance;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Immutable local-update metadata with constant-time append and shared prefixes.
 * This is neither a Provider plan nor a guarantee of a complete accepted-update trace:
 * the property policy supplies completeness and may discard ordinary displaced updates.
 */
public final class UpdateSequence {
    private static final UpdateSequence EMPTY = new UpdateSequence();

    @Nullable
    private final UpdateSequence previous;
    @Nullable
    private final MutationOccurrence last;
    private final int size;

    private UpdateSequence() {
        previous = null;
        last = null;
        size = 0;
    }

    private UpdateSequence(UpdateSequence previous, MutationOccurrence last) {
        this.previous = previous;
        this.last = last;
        this.size = Math.addExact(previous.size, 1);
    }

    public static UpdateSequence empty() {
        return EMPTY;
    }

    /** Appends a fact already accepted by the owner; this method performs no authorization. */
    public UpdateSequence append(MutationOccurrence occurrence) {
        if (occurrence.getOperation().getKind() != SemanticOperation.Kind.UPDATE) {
            throw new IllegalArgumentException("Only local structural updates belong in an update sequence.");
        }
        return new UpdateSequence(this, occurrence);
    }

    public int size() {
        return size;
    }

    /** Traverses the reporting projection without recursive calls or copying the sequence. */
    public Iterator<MutationOccurrence> reverseIterator() {
        return new Iterator<MutationOccurrence>() {
            private UpdateSequence cursor = UpdateSequence.this;

            @Override
            public boolean hasNext() {
                return cursor.size != 0;
            }

            @Override
            public MutationOccurrence next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                MutationOccurrence result = Objects.requireNonNull(cursor.last);
                cursor = Objects.requireNonNull(cursor.previous);
                return result;
            }
        };
    }

    /** Materializes a read-only chronological projection only when requested, in linear time and space. */
    public List<MutationOccurrence> inApplicationOrder() {
        List<MutationOccurrence> result = new ArrayList<>(size);
        reverseIterator().forEachRemaining(result::add);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }
}
