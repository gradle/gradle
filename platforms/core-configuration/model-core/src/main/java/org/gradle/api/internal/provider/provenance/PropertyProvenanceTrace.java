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

import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.internal.logging.text.TreeFormatter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Temporary traversal state for failure reporting and finalization snapshots.
 *
 * <p>The identity set prevents malformed or opaque provider graphs from turning diagnostics into
 * recursion. Provider nodes are never evaluated while this trace is assembled.</p>
 */
public final class PropertyProvenanceTrace {
    private static final int MAX_NODES = 128;
    private static final PropertyProvenanceRecord[] NO_RECORDS = new PropertyProvenanceRecord[0];
    private static final Limitation[] NO_LIMITATIONS = new Limitation[0];
    private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<PropertyProvenanceRecord> selectedSources = new ArrayList<>();
    private final List<PropertyProvenanceRecord> shadowedConventions = new ArrayList<>();
    private final Set<Limitation> limitations = EnumSet.noneOf(Limitation.class);
    private @Nullable PropertyHost host;

    public enum Limitation {
        MAPPING("map() dependencies are shown, not a proven causal failure path."),
        OPAQUE("Upstream provenance is unavailable beyond an opaque or unsupported provider boundary."),
        UNTRACKED("Upstream provenance is unavailable beyond an untracked property."),
        CYCLE("Trace stopped at a repeated provider node."),
        LIMIT("Trace truncated at the diagnostic traversal limit.");

        private final String message;

        Limitation(String message) {
            this.message = message;
        }
    }

    public void host(PropertyHost host) {
        if (this.host == null) {
            this.host = host;
        }
    }

    public void limitation(Limitation limitation) {
        limitations.add(limitation);
    }

    public boolean enter(Object node) {
        if (visited.size() >= MAX_NODES) {
            limitation(Limitation.LIMIT);
            return false;
        }
        if (!visited.add(node)) {
            limitation(Limitation.CYCLE);
            return false;
        }
        return true;
    }

    public void property(PropertyProvenanceState state) {
        PropertyProvenanceRecord selected = state.isExplicitSelected()
            ? state.getExplicitSource()
            : state.getConvention();
        if (selected != null) {
            addBounded(selectedSources, selected);
        }
        PropertyProvenanceRecord convention = state.getConvention();
        if (state.hasShadowedConvention() && convention != null) {
            addBounded(shadowedConventions, convention);
        }
    }

    private void addBounded(List<PropertyProvenanceRecord> records, PropertyProvenanceRecord record) {
        if (records.size() < MAX_NODES) {
            records.add(record);
        } else {
            limitation(Limitation.LIMIT);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(this);
    }

    public void append(Snapshot snapshot) {
        for (PropertyProvenanceRecord record : snapshot.selectedSources) {
            addBounded(selectedSources, record);
        }
        for (PropertyProvenanceRecord record : snapshot.shadowedConventions) {
            addBounded(shadowedConventions, record);
        }
        Collections.addAll(limitations, snapshot.limitations);
    }

    /**
     * Immutable descriptors only: never retain the visited providers, property host, or failed operation.
     */
    public static final class Snapshot {
        private final PropertyProvenanceRecord[] selectedSources;
        private final PropertyProvenanceRecord[] shadowedConventions;
        private final Limitation[] limitations;

        private Snapshot(PropertyProvenanceTrace trace) {
            selectedSources = trace.selectedSources.toArray(NO_RECORDS);
            shadowedConventions = trace.shadowedConventions.toArray(NO_RECORDS);
            limitations = trace.limitations.toArray(NO_LIMITATIONS);
        }
    }

    public void describeFailure(TreeFormatter formatter) {
        if (host != null) {
            describeFailure(formatter, host.currentPropertyFailure(PropertyProvenanceKind.GET));
        }
    }

    public void describeFailure(TreeFormatter formatter, @Nullable PropertyProvenanceRecord failure) {
        if (failure == null && selectedSources.isEmpty()) {
            return;
        }

        formatter.node("Failure trace to source:");
        if (failure != null) {
            formatter.node("    " + failure.formatFrame());
        }
        for (PropertyProvenanceRecord source : selectedSources) {
            formatter.node("    " + source.formatFrame());
        }

        if (!shadowedConventions.isEmpty()) {
            formatter.blankLine();
            formatter.node("Shadowed configuration:");
            for (PropertyProvenanceRecord convention : shadowedConventions) {
                formatter.node("    " + convention.formatFrame());
            }
        }
        if (!limitations.isEmpty()) {
            formatter.blankLine();
            formatter.node("Trace limitations:");
            for (Limitation limitation : limitations) {
                formatter.node("    " + limitation.message);
            }
        }
    }
}
