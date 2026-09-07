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

package org.gradle.api.internal.provenance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One accepted local mutation. The owner assigns a unique sequence within an explicit occurrence scope.
 * Copies and descriptor transport preserve that pair; they do not allocate a new mutation identity.
 * Scope allocation belongs to the owner, not a global counter or a contributor's display label.
 */
public final class MutationOccurrence {
    private final String scope;
    private final long sequence;
    private final Attribution attribution;
    private final SemanticOperation operation;

    public MutationOccurrence(String scope, long sequence, Attribution attribution, SemanticOperation operation) {
        if (sequence < 0) {
            throw new IllegalArgumentException("An occurrence sequence must be nonnegative.");
        }
        this.scope = Objects.requireNonNull(scope);
        this.sequence = sequence;
        this.attribution = Objects.requireNonNull(attribution);
        this.operation = Objects.requireNonNull(operation);
    }

    public String getScope() {
        return scope;
    }

    public long getSequence() {
        return sequence;
    }

    public Attribution getAttribution() {
        return attribution;
    }

    public SemanticOperation getOperation() {
        return operation;
    }

    /** Equality describes the mutation, not equality of its diagnostic fields. */
    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MutationOccurrence)) {
            return false;
        }
        MutationOccurrence that = (MutationOccurrence) other;
        return sequence == that.sequence && scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        return 31 * scope.hashCode() + Long.hashCode(sequence);
    }
}
