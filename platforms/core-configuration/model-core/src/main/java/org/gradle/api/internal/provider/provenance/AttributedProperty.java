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

package org.gradle.api.internal.provider.provenance;

import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.PropertyUpdateClassifier;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.DisplayName;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

import static org.gradle.api.internal.provider.provenance.EffectiveProvenanceView.SourceSelection.CONVENTION;
import static org.gradle.api.internal.provider.provenance.EffectiveProvenanceView.SourceSelection.EXPLICIT;

/** Opt-in effective local provenance. Provider evaluation and lifecycle remain owned by the ordinary property. */
public class AttributedProperty<T> extends DefaultProperty<T> {
    private static final SemanticOperation MAP_UPDATE = SemanticOperation.update(SemanticOperation.Shape.MAP);
    private static final String UNCLASSIFIED_REPLACE = "The returned replace provider is not a recognized map of the captured previous plan.";
    @Nullable
    private PropertyProvenanceHost provenanceHost;
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

    public AttributedProperty(PropertyProvenanceHost host, Class<T> type) {
        super(host, type);
        provenanceHost = host;
        ownerScope = host.getOwnerScope();
        occurrenceScope = host.newOccurrenceScope();
    }

    /** Returns the latest accepted fact without evaluating or describing the configured value. */
    @Nullable
    public MutationOccurrence getLastAcceptedMutation() {
        return lastAcceptedMutation;
    }

    public TargetContext getProvenanceTarget() {
        return new TargetContext(ownerScope, modelPath());
    }

    private String modelPath() {
        DisplayName name = getDeclaredDisplayName();
        return name == null ? occurrenceScope : name.getDisplayName();
    }

    /** Presence is deliberately not queried: an explicit missing provider is still selected. */
    public EffectiveProvenanceView getEffectiveProvenance() {
        if (finalizedProvenance != null) {
            return finalizedProvenance;
        }
        return EffectiveProvenanceView.captured(getProvenanceTarget(), source, updates, convention);
    }

    @Override
    public ProvenanceSnapshot<T> shallowCopy() {
        EffectiveProvenanceView finalized = finalizedProvenance;
        if (finalized != null) {
            return new ProvenanceSnapshot<>(getType(), captureSupplier(), finalized.getTarget().getOwner(), finalized.getTarget().getModelPath(),
                finalized.getSource(), finalized.getUpdates(), finalized.getShadowedConfiguration().isEmpty() ? null : finalized.getShadowedConfiguration().get(0));
        }
        return new ProvenanceSnapshot<>(getType(), captureSupplier(), ownerScope, modelPath(), source, updates, convention);
    }

    @Override
    public void replace(Transformer<? extends @Nullable Provider<? extends T>, ? super Provider<T>> transformation) {
        ProvenanceSnapshot<T> previous = shallowCopy();
        Provider<? extends T> candidate = transformation.transform(previous);
        if (candidate == null) {
            set((T) null);
            return;
        }
        int mapCount = PropertyUpdateClassifier.mapCount(candidate, previous);
        // Validate and accept through the existing engine before allocating an occurrence.
        super.setSupplier(sanitizeProvider(candidate));
        MutationOccurrence occurrence = accepted(mapCount > 0 ? mapOperation(mapCount) : SemanticOperation.unclassifiedBinding(UNCLASSIFIED_REPLACE));
        if (mapCount > 0) {
            source = previous.getSource();
            updates = previous.getUpdates().append(occurrence);
        } else {
            source = EffectiveProvenanceView.Source.known(EXPLICIT, occurrence);
            updates = UpdateSequence.empty();
        }
    }

    @Override
    protected void setSupplier(ProviderInternal<? extends T> supplier) {
        super.setSupplier(supplier);
        source = EffectiveProvenanceView.Source.known(EXPLICIT, accepted(SemanticOperation.EXPLICIT_BINDING));
        updates = UpdateSequence.empty();
    }

    @Override
    protected void setConvention(ProviderInternal<? extends T> supplier) {
        super.setConvention(supplier);
        convention = accepted(SemanticOperation.CONVENTION_BINDING);
        if (!isExplicit()) {
            selectConvention();
        }
    }

    @Override
    protected void discardValue() {
        super.discardValue();
        accepted(SemanticOperation.CLEAR_EXPLICIT);
        selectConvention();
    }

    @Override
    protected void discardConvention() {
        super.discardConvention();
        accepted(SemanticOperation.CLEAR_CONVENTION);
        convention = null;
        if (!isExplicit()) {
            selectConvention();
        }
    }

    @Override
    protected SupportsConvention setToConvention() {
        super.setToConvention();
        accepted(SemanticOperation.PROMOTE_CONVENTION);
        selectConvention();
        return this;
    }

    @Override
    protected SupportsConvention setToConventionIfUnset() {
        boolean changesSelection = !isExplicit() && !isDefaultConvention();
        super.setToConventionIfUnset();
        if (changesSelection) {
            accepted(SemanticOperation.PROMOTE_CONVENTION);
            selectConvention();
        }
        return this;
    }

    private static SemanticOperation mapOperation(int count) {
        if (count == 1) {
            return MAP_UPDATE;
        }
        SemanticOperation.Shape[] shapes = new SemanticOperation.Shape[count];
        Arrays.fill(shapes, SemanticOperation.Shape.MAP);
        return SemanticOperation.update(shapes);
    }

    private void selectConvention() {
        source = convention == null ? EffectiveProvenanceView.Source.unconfigured()
            : EffectiveProvenanceView.Source.known(isExplicit() ? EXPLICIT : CONVENTION, convention);
        updates = UpdateSequence.empty();
    }

    @Override
    protected ProviderInternal<? extends T> finalValue(EvaluationScopeContext context, ProviderInternal<? extends T> value, ValueConsumer consumer) {
        EffectiveProvenanceView checkpoint = getEffectiveProvenance();
        ProviderInternal<? extends T> result = super.finalValue(context, value, consumer);
        // AbstractProperty installs this result and the final state immediately after return.
        // A failed evaluation never installs a checkpoint or releases the mutable host.
        finalizedProvenance = checkpoint;
        convention = null;
        provenanceHost = null;
        return result;
    }

    private MutationOccurrence accepted(SemanticOperation operation) {
        MutationOccurrence occurrence = new MutationOccurrence(occurrenceScope, nextSequence++, Objects.requireNonNull(provenanceHost).currentAttribution(), operation);
        lastAcceptedMutation = occurrence;
        return occurrence;
    }
}
