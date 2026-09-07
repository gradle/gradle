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

package org.gradle.api.internal.provider;

import org.gradle.api.Transformer;
import org.gradle.api.internal.provenance.Attribution;
import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.MutationOccurrence;
import org.gradle.api.internal.provenance.OrdinaryProvenanceState;
import org.gradle.api.internal.provenance.SemanticOperation;
import org.gradle.api.internal.provenance.TargetContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.DisplayName;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Bridges successful ordinary scalar operations to provider-independent provenance state. */
public class AttributedProperty<T> extends DefaultProperty<T> {
    @Nullable
    private PropertyProvenanceHost provenanceHost;
    private final OrdinaryProvenanceState provenance;

    public AttributedProperty(PropertyProvenanceHost host, Class<T> type) {
        super(host, type);
        provenanceHost = host;
        provenance = new OrdinaryProvenanceState(host.getOwnerScope(), host.newOccurrenceScope());
    }

    @Nullable
    public MutationOccurrence getLastAcceptedMutation() {
        return provenance.getLastAcceptedMutation();
    }

    public TargetContext getProvenanceTarget() {
        return new TargetContext(provenance.getOwnerScope(), modelPath());
    }

    private String modelPath() {
        DisplayName name = getDeclaredDisplayName();
        return name == null ? provenance.getOccurrenceScope() : name.getDisplayName();
    }

    /** Reading provenance never queries value presence or producer tasks. */
    public EffectiveProvenanceView getEffectiveProvenance() {
        return provenance.getEffectiveProvenance(modelPath());
    }

    @Override
    public ProvenanceSnapshot<T> shallowCopy() {
        return newSnapshot(captureSupplier(), provenance, modelPath());
    }

    protected ProvenanceSnapshot<T> newSnapshot(ProviderInternal<? extends T> supplier, OrdinaryProvenanceState state, String modelPath) {
        return new ProvenanceSnapshot<>(getType(), supplier, state, modelPath);
    }

    @Override
    public void replace(Transformer<? extends @Nullable Provider<? extends T>, ? super Provider<T>> transformation) {
        ProvenanceSnapshot<T> previous = shallowCopy();
        Provider<? extends T> candidate = transformation.transform(previous);
        if (candidate == null) {
            super.set((T) null);
            return;
        }
        SemanticOperation operation = PropertyUpdateClassifier.classifyReplace(candidate, previous);
        super.setSupplier(sanitizeProvider(candidate));
        if (operation.getKind() == SemanticOperation.Kind.UPDATE) {
            provenance.acceptedUpdate(currentAttribution(), operation, previous.getSource(), previous.getUpdates());
        } else {
            provenance.acceptedBinding(currentAttribution(), operation);
        }
    }

    @Override
    protected void setSupplier(ProviderInternal<? extends T> supplier) {
        super.setSupplier(supplier);
        provenance.acceptedBinding(currentAttribution(), SemanticOperation.EXPLICIT_BINDING);
    }

    @Override
    protected void setConvention(ProviderInternal<? extends T> supplier) {
        super.setConvention(supplier);
        provenance.acceptedConvention(currentAttribution(), isExplicit());
    }

    @Override
    protected void discardValue() {
        super.discardValue();
        provenance.acceptedClearExplicit(currentAttribution());
    }

    @Override
    protected void discardConvention() {
        super.discardConvention();
        provenance.acceptedClearConvention(currentAttribution(), isExplicit());
    }

    @Override
    protected SupportsConvention setToConvention() {
        super.setToConvention();
        provenance.acceptedPromotion(currentAttribution());
        return this;
    }

    @Override
    protected SupportsConvention setToConventionIfUnset() {
        boolean changesSelection = !isExplicit() && !isDefaultConvention();
        super.setToConventionIfUnset();
        if (changesSelection) {
            provenance.acceptedPromotion(currentAttribution());
        }
        return this;
    }

    @Override
    protected ProviderInternal<? extends T> finalValue(EvaluationScopeContext context, ProviderInternal<? extends T> value, ValueConsumer consumer) {
        EffectiveProvenanceView checkpoint = getEffectiveProvenance();
        ProviderInternal<? extends T> result = super.finalValue(context, value, consumer);
        // AbstractProperty immediately installs the result and final state after return.
        provenance.freeze(checkpoint);
        provenanceHost = null;
        return result;
    }

    /** Finalized properties deliberately no longer retain a runtime attribution service. */
    @Nullable
    protected Attribution failureAttribution() {
        return provenanceHost == null ? null : provenanceHost.currentAttribution();
    }

    private Attribution currentAttribution() {
        return Objects.requireNonNull(provenanceHost).currentAttribution();
    }
}
