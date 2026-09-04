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

import org.gradle.api.internal.provider.provenance.PropertyProvenanceTrace.Snapshot;
import org.jspecify.annotations.Nullable;

/**
 * Effective provenance retained by an ordinary property.
 *
 * <p>This deliberately is not mutation history. A replacing {@code set} replaces the explicit source,
 * and a convention is shown outside the source trace while an explicit source is selected.</p>
 */
public final class PropertyProvenanceState {
    // A PropertyProvenanceRecord while mutable, or a Snapshot after finalization. Reuse the slot
    // so finalization support does not add a reference to every unfinalized property's metadata.
    private @Nullable Object explicitSourceOrSnapshot;
    private @Nullable PropertyProvenanceRecord convention;
    private boolean explicitSelected;
    private boolean conventionPromoted;

    public void explicitSource(PropertyProvenanceRecord source) {
        explicitSourceOrSnapshot = source;
        explicitSelected = true;
        conventionPromoted = false;
    }

    public void convention(PropertyProvenanceRecord source) {
        convention = source;
        // Even an interned record from the same origin represents a new binding occurrence.
        conventionPromoted = false;
    }

    public void selectExplicit() {
        explicitSelected = true;
        conventionPromoted = false;
    }

    public void selectConvention() {
        explicitSelected = false;
        explicitSourceOrSnapshot = null;
        conventionPromoted = false;
    }

    /**
     * Promoting a convention freezes its binding, even if a different convention is supplied later.
     */
    public void promoteConvention() {
        explicitSourceOrSnapshot = convention;
        explicitSelected = true;
        conventionPromoted = true;
    }

    public PropertyProvenanceState copy() {
        PropertyProvenanceState copy = new PropertyProvenanceState();
        copy.explicitSourceOrSnapshot = explicitSourceOrSnapshot;
        copy.convention = convention;
        copy.explicitSelected = explicitSelected;
        copy.conventionPromoted = conventionPromoted;
        return copy;
    }

    public void finalizeProvenance(PropertyProvenanceTrace.Snapshot snapshot) {
        explicitSourceOrSnapshot = snapshot;
        // The snapshot contains the local binding as well as the upstream bindings.
        convention = null;
    }

    public @Nullable Snapshot getFinalizedSnapshot() {
        return explicitSourceOrSnapshot instanceof Snapshot ? (Snapshot) explicitSourceOrSnapshot : null;
    }

    public void discardConvention() {
        convention = null;
    }

    public @Nullable PropertyProvenanceRecord getExplicitSource() {
        return explicitSourceOrSnapshot instanceof PropertyProvenanceRecord ? (PropertyProvenanceRecord) explicitSourceOrSnapshot : null;
    }

    public @Nullable PropertyProvenanceRecord getConvention() {
        return convention;
    }

    public boolean isExplicitSelected() {
        return explicitSelected;
    }

    public boolean hasShadowedConvention() {
        return explicitSelected && convention != null && !conventionPromoted;
    }
}
