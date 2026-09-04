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

/**
 * Feature switch and interning table for project-scoped property provenance.
 */
@ServiceScope(Scope.BuildTree.class)
public final class PropertyProvenanceRegistry {
    private static final int KIND_COUNT = PropertyProvenanceKind.values().length;

    private final boolean enabled;
    private final boolean captureLocations;
    private final Map<UserCodeSource, PropertyProvenanceRecord[]> recordsBySource = new ConcurrentHashMap<>();
    private final PropertyProvenanceRecord[] unknownRecords = new PropertyProvenanceRecord[KIND_COUNT];

    public PropertyProvenanceRegistry(boolean enabled) {
        this(enabled, false);
    }

    public PropertyProvenanceRegistry(boolean enabled, boolean captureLocations) {
        this.enabled = enabled;
        this.captureLocations = enabled && captureLocations;
        for (PropertyProvenanceKind kind : PropertyProvenanceKind.values()) {
            unknownRecords[kind.ordinal()] = new PropertyProvenanceRecord(PropertyProvenanceOrigin.UNKNOWN, kind, null);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean capturesLocations() {
        return captureLocations;
    }

    /**
     * Returns a shared record when there is no per-occurrence location.
     */
    public PropertyProvenanceRecord recordFor(
        @Nullable UserCodeSource source,
        PropertyProvenanceKind kind,
        @Nullable String location
    ) {
        PropertyProvenanceRecord record = source == null ? unknownRecords[kind.ordinal()] : recordsFor(source)[kind.ordinal()];
        return captureLocations && location != null ? new PropertyProvenanceRecord(record.getOrigin(), kind, location) : record;
    }

    private PropertyProvenanceRecord[] recordsFor(UserCodeSource source) {
        // Publish a complete immutable table: deferred callbacks may use the same origin in parallel.
        // One descriptor per application source, not per mutation or per property. Do not merge
        // applications merely because their plugin IDs or display names match.
        return recordsBySource.computeIfAbsent(source, key -> {
            PropertyProvenanceRecord[] result = new PropertyProvenanceRecord[KIND_COUNT];
            PropertyProvenanceOrigin origin = PropertyProvenanceOrigin.from(key);
            for (PropertyProvenanceKind operation : PropertyProvenanceKind.values()) {
                result[operation.ordinal()] = new PropertyProvenanceRecord(origin, operation, null);
            }
            return result;
        });
    }

    public PropertyProvenanceRecord failureFor(String originDisplayName, PropertyProvenanceKind kind, @Nullable String location) {
        return new PropertyProvenanceRecord(originDisplayName, kind, captureLocations ? location : null);
    }
}
