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
    private final boolean enabled;
    private final boolean captureLocations;
    private final Map<UserCodeSource, BindingRecords> recordsBySource = new ConcurrentHashMap<>();
    private final BindingRecords unknownRecords = new BindingRecords(PropertyProvenanceOrigin.UNKNOWN);

    public PropertyProvenanceRegistry(boolean enabled) {
        this(enabled, false);
    }

    public PropertyProvenanceRegistry(boolean enabled, boolean captureLocations) {
        this.enabled = enabled;
        this.captureLocations = enabled && captureLocations;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean capturesLocations() {
        return captureLocations;
    }

    /**
     * Returns a successful mutation record. Source bindings are shared without locations; structural
     * updates share the origin descriptor but get their operation record only when used.
     * Failed operations must use {@link #failureFor(String, PropertyProvenanceKind, String)};
     * their records are never interned.
     */
    public PropertyProvenanceRecord recordFor(
        @Nullable UserCodeSource source,
        PropertyProvenanceKind kind,
        @Nullable String location
    ) {
        if (kind != PropertyProvenanceKind.EXPLICIT_SOURCE && kind != PropertyProvenanceKind.CONVENTION && kind != PropertyProvenanceKind.MAP_UPDATE) {
            throw new IllegalArgumentException("Not a successful binding kind: " + kind);
        }
        BindingRecords records = source == null ? unknownRecords : recordsFor(source);
        if (kind == PropertyProvenanceKind.MAP_UPDATE) {
            return new PropertyProvenanceRecord(records.explicitSource.getOrigin(), kind, captureLocations ? location : null);
        }
        PropertyProvenanceRecord record = kind == PropertyProvenanceKind.EXPLICIT_SOURCE ? records.explicitSource : records.convention;
        return captureLocations && location != null ? new PropertyProvenanceRecord(record.getOrigin(), kind, location) : record;
    }

    private BindingRecords recordsFor(UserCodeSource source) {
        // Publish a complete immutable pair: deferred callbacks may use the same origin in parallel.
        // One descriptor per application source, not per mutation or per property. Do not merge
        // applications merely because their plugin IDs or display names match.
        return recordsBySource.computeIfAbsent(source, key -> new BindingRecords(PropertyProvenanceOrigin.from(key)));
    }

    public PropertyProvenanceRecord failureFor(String originDisplayName, PropertyProvenanceKind kind, @Nullable String location) {
        return new PropertyProvenanceRecord(originDisplayName, kind, captureLocations ? location : null);
    }

    private static final class BindingRecords {
        private final PropertyProvenanceRecord explicitSource;
        private final PropertyProvenanceRecord convention;

        private BindingRecords(PropertyProvenanceOrigin origin) {
            explicitSource = new PropertyProvenanceRecord(origin, PropertyProvenanceKind.EXPLICIT_SOURCE, null);
            convention = new PropertyProvenanceRecord(origin, PropertyProvenanceKind.CONVENTION, null);
        }
    }
}
