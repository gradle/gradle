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

import org.gradle.internal.logging.text.TreeFormatter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Failure-only traversal state for selected sources across an ordinary scalar property chain.
 *
 * <p>The identity set prevents malformed or opaque provider graphs from turning diagnostics into
 * recursion. Provider nodes are never evaluated while this trace is assembled.</p>
 */
public final class PropertyProvenanceTrace {
    private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<PropertyProvenanceRecord> selectedSources = new ArrayList<>();
    private final List<PropertyProvenanceRecord> shadowedConventions = new ArrayList<>();

    public boolean enter(Object node) {
        return visited.add(node);
    }

    public void property(PropertyProvenanceState state) {
        PropertyProvenanceRecord selected = state.isExplicitSelected()
            ? state.getExplicitSource()
            : state.getConvention();
        if (selected != null) {
            selectedSources.add(selected);
        }
        if (state.isExplicitSelected() && state.getConvention() != null) {
            shadowedConventions.add(state.getConvention());
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
    }
}
