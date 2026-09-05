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
 * Read-only view of effective provenance retained inline by an enabled property value state.
 *
 * <p>This deliberately is not mutation history. A replacing {@code set} replaces the explicit source,
 * and a convention is shown outside the source trace while an explicit source is selected.</p>
 */
public interface PropertyProvenanceState {
    /**
     * A binding record or a descriptor-only finalized snapshot, never a provider or host.
     */
    @Nullable Object getExplicitSourceOrSnapshot();

    @Nullable PropertyProvenanceRecord getConvention();

    boolean isExplicitSelected();

    boolean isConventionPromoted();

    default PropertyProvenanceState copy() {
        return new Detached(this);
    }

    default @Nullable Snapshot getFinalizedSnapshot() {
        Object source = getExplicitSourceOrSnapshot();
        return source instanceof Snapshot ? (Snapshot) source : null;
    }

    default @Nullable PropertyProvenanceRecord getExplicitSource() {
        Object source = getExplicitSourceOrSnapshot();
        return source instanceof PropertyProvenanceRecord ? (PropertyProvenanceRecord) source : null;
    }

    default boolean hasShadowedConvention() {
        return isExplicitSelected() && getConvention() != null && !isConventionPromoted();
    }

    /**
     * Shallow copies detach only diagnostic data; they must not retain the mutable value state.
     */
    final class Detached implements PropertyProvenanceState {
        private final @Nullable Object explicitSourceOrSnapshot;
        private final @Nullable PropertyProvenanceRecord convention;
        private final boolean explicitSelected;
        private final boolean conventionPromoted;

        private Detached(PropertyProvenanceState source) {
            explicitSourceOrSnapshot = source.getExplicitSourceOrSnapshot();
            convention = source.getConvention();
            explicitSelected = source.isExplicitSelected();
            conventionPromoted = source.isConventionPromoted();
        }

        @Override
        public @Nullable Object getExplicitSourceOrSnapshot() {
            return explicitSourceOrSnapshot;
        }

        @Override
        public @Nullable PropertyProvenanceRecord getConvention() {
            return convention;
        }

        @Override
        public boolean isExplicitSelected() {
            return explicitSelected;
        }

        @Override
        public boolean isConventionPromoted() {
            return conventionPromoted;
        }
    }
}
