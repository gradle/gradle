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

import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.MutationOccurrence;
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.api.internal.provenance.TargetContext;
import org.gradle.api.internal.provenance.UpdateSequence;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

/** A captured supplier and descriptor checkpoint, with no reference to its former property owner. */
public final class ProvenanceSnapshot<T> extends AbstractMinimalProvider<T> {
    private final Class<T> type;
    private final ProviderInternal<? extends T> supplier;
    private final ScopeIdentity ownerScope;
    private final String modelPath;
    private final EffectiveProvenanceView.Source source;
    private final UpdateSequence updates;
    @Nullable
    private final MutationOccurrence convention;

    ProvenanceSnapshot(
        Class<T> type,
        ProviderInternal<? extends T> supplier,
        ScopeIdentity ownerScope,
        String modelPath,
        EffectiveProvenanceView.Source source,
        UpdateSequence updates,
        @Nullable MutationOccurrence convention
    ) {
        this.type = type;
        this.supplier = supplier;
        this.ownerScope = ownerScope;
        this.modelPath = modelPath;
        this.source = source;
        this.updates = updates;
        this.convention = convention;
    }

    public EffectiveProvenanceView getEffectiveProvenance() {
        return EffectiveProvenanceView.captured(new TargetContext(ownerScope, modelPath), source, updates, convention);
    }

    EffectiveProvenanceView.Source getSource() {
        return source;
    }

    UpdateSequence getUpdates() {
        return updates;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public ValueProducer getProducer() {
        try (EvaluationScopeContext ignored = openScope()) {
            return supplier.getProducer();
        }
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        try (EvaluationScopeContext ignored = openScope()) {
            return supplier.calculateExecutionTimeValue();
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            return supplier.calculateValue(consumer);
        }
    }
}
