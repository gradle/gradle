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


package org.gradle.api.internal.provider.provenance;

/**
 * A single property mutation: who did it, and what they did.
 * <p>
 * Records are immutable and interned, so recording provenance on a property costs one reference and no
 * allocation. Applying the mutation and retaining its record are conceptually one atomic step: a rejected
 * mutation leaves no record behind.
 */
public final class MutationRecord {

    private final MutationOrigin origin;
    private final MutationKind kind;

    public MutationRecord(MutationOrigin origin, MutationKind kind) {
        this.origin = origin;
        this.kind = kind;
    }

    public MutationOrigin getOrigin() {
        return origin;
    }

    public MutationKind getKind() {
        return kind;
    }

    /**
     * Is there a contributor worth naming? An unattributed mutation is still recorded, but there is nothing
     * useful to tell the user about it.
     */
    public boolean isAttributed() {
        return origin.getContributor().isKnown();
    }

    /**
     * A phrase of the form {@code set by plugin 'com.example.feature'}.
     */
    public String describe() {
        return kind.getVerb() + " by " + origin.getDisplayName();
    }

    @Override
    public String toString() {
        return describe();
    }
}
