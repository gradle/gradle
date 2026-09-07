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

import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in scalar attribution storage. Only the most recently accepted mutation is retained at this stage.
 * This is not an effective provenance view, a mutation history or a collaborative update trace.
 * Copy/root/update integration belongs to the subsequent effective-provenance milestone.
 */
public class AttributedProperty<T> extends DefaultProperty<T> {
    private final PropertyProvenanceHost provenanceHost;
    private final String occurrenceScope;
    private long nextSequence;
    @Nullable
    private MutationOccurrence lastAcceptedMutation;

    public AttributedProperty(PropertyProvenanceHost host, Class<T> type) {
        super(host, type);
        provenanceHost = host;
        occurrenceScope = host.newOccurrenceScope();
    }

    /** Returns the latest accepted fact without evaluating or describing the configured value. */
    @Nullable
    public MutationOccurrence getLastAcceptedMutation() {
        return lastAcceptedMutation;
    }

    /** Resolves the model display name only when requested; an anonymous property's token is unambiguous locally. */
    public TargetContext getProvenanceTarget() {
        DisplayName name = getDeclaredDisplayName();
        return new TargetContext(provenanceHost.getOwnerScope(), name == null ? occurrenceScope : name.getDisplayName());
    }

    @Override
    protected void setSupplier(ProviderInternal<? extends T> supplier) {
        super.setSupplier(supplier);
        accepted(SemanticOperation.EXPLICIT_BINDING);
    }

    @Override
    protected void setConvention(ProviderInternal<? extends T> convention) {
        super.setConvention(convention);
        accepted(SemanticOperation.CONVENTION_BINDING);
    }

    @Override
    protected void discardValue() {
        super.discardValue();
        accepted(SemanticOperation.CLEAR_EXPLICIT);
    }

    @Override
    protected void discardConvention() {
        super.discardConvention();
        accepted(SemanticOperation.CLEAR_CONVENTION);
    }

    @Override
    protected SupportsConvention setToConvention() {
        super.setToConvention();
        accepted(SemanticOperation.PROMOTE_CONVENTION);
        return this;
    }

    @Override
    protected SupportsConvention setToConventionIfUnset() {
        boolean changesSelection = !isExplicit() && !isDefaultConvention();
        super.setToConventionIfUnset();
        if (changesSelection) {
            accepted(SemanticOperation.PROMOTE_CONVENTION);
        }
        return this;
    }

    private void accepted(SemanticOperation operation) {
        lastAcceptedMutation = new MutationOccurrence(occurrenceScope, nextSequence++, provenanceHost.currentAttribution(), operation);
    }
}
