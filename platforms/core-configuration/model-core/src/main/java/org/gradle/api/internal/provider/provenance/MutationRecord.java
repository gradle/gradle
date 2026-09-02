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

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A single property mutation: who did it, and what they did.
 * <p>
 * Records are immutable and interned, so recording provenance on a property costs one reference and no
 * allocation. Applying the mutation and retaining its record are conceptually one atomic step: a rejected
 * mutation leaves no record behind.
 */
public final class MutationRecord implements MutationHistory {

    private final MutationOrigin origin;
    private final MutationKind kind;
    private final @Nullable String location;

    public MutationRecord(MutationOrigin origin, MutationKind kind) {
        this(origin, kind, null);
    }

    public MutationRecord(MutationOrigin origin, MutationKind kind, @Nullable String location) {
        this.origin = origin;
        this.kind = kind;
        this.location = location;
    }

    /**
     * The call site that performed the mutation, as {@code file:line}, when locations are being captured.
     */
    public @Nullable String getLocation() {
        return location;
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
        String described = kind.getVerb() + " by " + origin.getDisplayName();
        return location != null ? described + " at " + location : described;
    }

    @Override
    public List<MutationRecord> getRecords() {
        return Collections.singletonList(this);
    }

    @Override
    public int getNotRetainedCount() {
        return 0;
    }

    @Override
    public @Nullable MutationRecord lastExplicit() {
        return kind.isConvention() ? null : this;
    }

    @Override
    public @Nullable MutationRecord lastConvention() {
        return kind.isConvention() ? this : null;
    }

    @Override
    public String describeForMessage() {
        return isAttributed() ? " It was last " + describe() + "." : "";
    }

    @Override
    public String toString() {
        return describe();
    }
}
