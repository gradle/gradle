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

import static org.gradle.api.internal.provenance.EffectiveProvenanceView.SourceSelection.CONVENTION;
import static org.gradle.api.internal.provenance.EffectiveProvenanceView.SourceSelection.EXPLICIT;

/**
 * Descriptor-only state for ordinary source replacement and captured-root updates.
 * The caller supplies facts only after its own mutation checks succeed, and supplies selection
 * without querying value presence. This class neither evaluates values nor authorizes mutations.
 * Collaborative source rebinding and complete accepted history require a different policy.
 */
public final class OrdinaryProvenanceState {
    private final ScopeIdentity ownerScope;
    private final String occurrenceScope;
    private long nextSequence;
    @Nullable
    private MutationOccurrence lastAcceptedMutation;
    @Nullable
    private MutationOccurrence convention;
    private EffectiveProvenanceView.Source source = EffectiveProvenanceView.Source.unconfigured();
    private UpdateSequence updates = UpdateSequence.empty();
    @Nullable
    private EffectiveProvenanceView finalizedProvenance;

    public OrdinaryProvenanceState(ScopeIdentity ownerScope, String occurrenceScope) {
        this.ownerScope = ownerScope;
        this.occurrenceScope = occurrenceScope;
    }

    public ScopeIdentity getOwnerScope() {
        return ownerScope;
    }

    public String getOccurrenceScope() {
        return occurrenceScope;
    }

    @Nullable
    public MutationOccurrence getLastAcceptedMutation() {
        return lastAcceptedMutation;
    }

    public EffectiveProvenanceView getEffectiveProvenance(String modelPath) {
        return finalizedProvenance == null
            ? EffectiveProvenanceView.captured(new TargetContext(ownerScope, modelPath), source, updates, convention) : finalizedProvenance;
    }

    public EffectiveProvenanceView.Source getSource() {
        return finalizedProvenance == null ? source : finalizedProvenance.getSource();
    }

    public UpdateSequence getUpdates() {
        return finalizedProvenance == null ? updates : finalizedProvenance.getUpdates();
    }

    @Nullable
    public MutationOccurrence getConvention() {
        if (finalizedProvenance == null) {
            return convention;
        }
        return finalizedProvenance.getShadowedConfiguration().isEmpty() ? null : finalizedProvenance.getShadowedConfiguration().get(0);
    }

    public String getModelPath(String currentModelPath) {
        return finalizedProvenance == null ? currentModelPath : finalizedProvenance.getTarget().getModelPath();
    }

    public boolean isFinalized() {
        return finalizedProvenance != null;
    }

    public void acceptedBinding(Attribution attribution, SemanticOperation operation) {
        source = EffectiveProvenanceView.Source.known(EXPLICIT, accepted(attribution, operation));
        updates = UpdateSequence.empty();
    }

    public void acceptedConvention(Attribution attribution, boolean explicit) {
        convention = accepted(attribution, SemanticOperation.CONVENTION_BINDING);
        if (!explicit) {
            selectConvention(false);
        }
    }

    public void acceptedClearExplicit(Attribution attribution) {
        accepted(attribution, SemanticOperation.CLEAR_EXPLICIT);
        selectConvention(false);
    }

    public void acceptedClearConvention(Attribution attribution, boolean explicit) {
        accepted(attribution, SemanticOperation.CLEAR_CONVENTION);
        convention = null;
        if (!explicit) {
            selectConvention(false);
        }
    }

    public void acceptedPromotion(Attribution attribution) {
        accepted(attribution, SemanticOperation.PROMOTE_CONVENTION);
        selectConvention(true);
    }

    public void acceptedUpdate(Attribution attribution, SemanticOperation operation, EffectiveProvenanceView.Source capturedSource, UpdateSequence capturedUpdates) {
        MutationOccurrence occurrence = accepted(attribution, operation);
        source = capturedSource;
        updates = capturedUpdates.append(occurrence);
    }

    /** Called only after successful value finalization; the checkpoint precedes value calculation. */
    public void freeze(EffectiveProvenanceView checkpoint) {
        finalizedProvenance = checkpoint;
        convention = null;
        source = checkpoint.getSource();
        updates = checkpoint.getUpdates();
    }

    private void selectConvention(boolean explicit) {
        source = convention == null ? EffectiveProvenanceView.Source.unconfigured()
            : EffectiveProvenanceView.Source.known(explicit ? EXPLICIT : CONVENTION, convention);
        updates = UpdateSequence.empty();
    }

    private MutationOccurrence accepted(Attribution attribution, SemanticOperation operation) {
        MutationOccurrence occurrence = new MutationOccurrence(occurrenceScope, nextSequence++, attribution, operation);
        lastAcceptedMutation = occurrence;
        return occurrence;
    }
}
