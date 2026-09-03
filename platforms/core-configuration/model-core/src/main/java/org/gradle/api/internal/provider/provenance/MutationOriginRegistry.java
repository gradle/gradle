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

import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interns mutation origins and records for the build tree.
 * <p>
 * A property mutation happens often enough that it must not allocate. Every distinct
 * (user code source, mutation kind) pair maps to one shared {@link MutationRecord}, so recording provenance on a
 * property is a single reference write.
 * <p>
 * Sources are keyed by identity: {@link UserCodeSource} has no value equality, and one instance is created per
 * application of a plugin or script, which bounds the table by the number of applications in the build.
 */
@ServiceScope(Scope.BuildTree.class)
public class MutationOriginRegistry {

    private static final int KIND_COUNT = MutationKind.values().length;

    /**
     * Budget for located records, mirroring the cap the problem diagnostics factory puts on stack captures.
     * Locations past it are dropped rather than slowing the build down further.
     */
    static final int MAX_LOCATED_RECORDS = 2000;

    private final boolean enabled;
    private final boolean capturingLocations;
    private final boolean walkingStackForLocations;
    private final Map<UserCodeSource, MutationRecord[]> recordsBySource = new ConcurrentHashMap<>();
    private final Map<UserCodeSource, MutationOrigin> originsBySource = new ConcurrentHashMap<>();
    private final MutationRecord[] unattributedRecords = new MutationRecord[KIND_COUNT];
    private final AtomicInteger locationBudget = new AtomicInteger(MAX_LOCATED_RECORDS);

    public MutationOriginRegistry(boolean enabled) {
        this(enabled, false, true);
    }

    public MutationOriginRegistry(boolean enabled, boolean capturingLocations) {
        this(enabled, capturingLocations, true);
    }

    public MutationOriginRegistry(boolean enabled, boolean capturingLocations, boolean walkingStackForLocations) {
        this.enabled = enabled;
        this.capturingLocations = capturingLocations;
        this.walkingStackForLocations = walkingStackForLocations;
        // Instrumented build logic cannot reach this service, so the switch it reads is static.
        PropertyCallSites.setEnabled(capturingLocations);
        for (MutationKind kind : MutationKind.values()) {
            unattributedRecords[kind.ordinal()] = new MutationRecord(MutationOrigin.UNKNOWN, kind);
        }
    }

    /**
     * Is provenance capture switched on for this build? When off, callers must not record anything, so that
     * behaviour and diagnostics are byte-for-byte what they were before.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Should the call site of each mutation be captured as well as its contributor?
     */
    public boolean isCapturingLocations() {
        return capturingLocations;
    }

    /**
     * Should a mutation with no instrumented call site fall back to walking the stack? Instrumentation covers
     * build logic but not Gradle's own code, so the fallback is what makes locations general. Turning it off
     * isolates what instrumentation alone provides.
     */
    public boolean isWalkingStackForLocations() {
        return walkingStackForLocations;
    }

    /**
     * Is there budget left to capture a call site? Locations cannot be interned, so they are both allocating
     * and stack-walking, and are capped for the build.
     */
    public boolean claimLocationBudget() {
        return locationBudget.getAndDecrement() > 0;
    }

    /**
     * Returns a record carrying the call site that performed the mutation. Unlike the interned records, this
     * allocates: a location is per call site, not per contributor.
     */
    public MutationRecord recordFor(@Nullable UserCodeSource source, MutationKind kind, @Nullable String location) {
        if (location == null) {
            return recordFor(source, kind);
        }
        // Only the record varies per call site; the origin behind it is still shared.
        return new MutationRecord(originFor(source), kind, location);
    }

    private MutationOrigin originFor(@Nullable UserCodeSource source) {
        if (source == null) {
            return MutationOrigin.UNKNOWN;
        }
        return originsBySource.computeIfAbsent(source, MutationOrigin::of);
    }

    /**
     * Returns the shared record for a mutation of the given kind by the given user code, or the unattributed
     * record when no user code was active.
     */
    public MutationRecord recordFor(@Nullable UserCodeSource source, MutationKind kind) {
        if (source == null) {
            return unattributedRecords[kind.ordinal()];
        }
        MutationRecord[] byKind = recordsBySource.computeIfAbsent(source, ignored -> new MutationRecord[KIND_COUNT]);
        MutationRecord record = byKind[kind.ordinal()];
        if (record == null) {
            // A benign race here produces two equivalent immutable records, one of which is discarded.
            record = new MutationRecord(originFor(source), kind);
            byKind[kind.ordinal()] = record;
        }
        return record;
    }
}
