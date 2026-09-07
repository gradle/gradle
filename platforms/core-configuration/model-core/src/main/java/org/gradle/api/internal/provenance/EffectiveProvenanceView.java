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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Read-only local provenance at a mutation checkpoint, supplied by the property's plan policy.
 * Constructing and reading this view never evaluates Providers or determines selection from value presence.
 * A live convention root must be resolved by the owner again at each checkpoint, not frozen by reusing a view.
 */
public final class EffectiveProvenanceView {
    public enum SourceSelection { EXPLICIT, CONVENTION, UNCONFIGURED, UNKNOWN }
    public enum SourceKnowledge { KNOWN, UNATTRIBUTED, UNCONFIGURED, UNAVAILABLE }
    public enum RootKind { CAPTURED, LIVE_CONVENTION }

    /** Selected source metadata, independent of whether the selected Provider produces a value. */
    public static final class Source {
        private final SourceSelection selection;
        private final SourceKnowledge knowledge;
        @Nullable
        private final MutationOccurrence occurrence;
        private final String reason;

        private Source(SourceSelection selection, SourceKnowledge knowledge, @Nullable MutationOccurrence occurrence, String reason) {
            this.selection = selection;
            this.knowledge = knowledge;
            this.occurrence = occurrence;
            this.reason = reason;
        }

        public static Source known(SourceSelection selection, MutationOccurrence occurrence) {
            requireConfigured(selection);
            SemanticOperation.Kind kind = occurrence.getOperation().getKind();
            if (kind != SemanticOperation.Kind.EXPLICIT_BINDING && kind != SemanticOperation.Kind.CONVENTION_BINDING && kind != SemanticOperation.Kind.UNCLASSIFIED_BINDING) {
                throw new IllegalArgumentException("A selected source must be a binding occurrence.");
            }
            return new Source(selection, SourceKnowledge.KNOWN, occurrence, "");
        }

        public static Source unattributed(SourceSelection selection) {
            requireConfigured(selection);
            return new Source(selection, SourceKnowledge.UNATTRIBUTED, null, "");
        }

        public static Source unconfigured() {
            return new Source(SourceSelection.UNCONFIGURED, SourceKnowledge.UNCONFIGURED, null, "");
        }

        public static Source unavailable(SourceSelection selection, String reason) {
            if (selection == SourceSelection.UNCONFIGURED || reason.isEmpty()) {
                throw new IllegalArgumentException("Unavailable provenance needs a reason and must not describe an unconfigured source.");
            }
            return new Source(Objects.requireNonNull(selection), SourceKnowledge.UNAVAILABLE, null, reason);
        }

        private static void requireConfigured(SourceSelection selection) {
            if (selection != SourceSelection.EXPLICIT && selection != SourceSelection.CONVENTION) {
                throw new IllegalArgumentException("A configured source needs explicit or convention selection.");
            }
        }

        public SourceSelection getSelection() {
            return selection;
        }

        public SourceKnowledge getKnowledge() {
            return knowledge;
        }

        @Nullable
        public MutationOccurrence getOccurrence() {
            return occurrence;
        }

        public String getReason() {
            return reason;
        }
    }

    private final TargetContext target;
    private final RootKind rootKind;
    private final Source source;
    private final UpdateSequence updates;
    private final List<MutationOccurrence> shadowedConfiguration;
    private final List<String> partialReasons;

    public EffectiveProvenanceView(
        TargetContext target,
        RootKind rootKind,
        Source source,
        UpdateSequence updates,
        List<MutationOccurrence> shadowedConfiguration,
        List<String> partialReasons
    ) {
        this.target = Objects.requireNonNull(target);
        this.rootKind = Objects.requireNonNull(rootKind);
        this.source = Objects.requireNonNull(source);
        this.updates = Objects.requireNonNull(updates);
        if (rootKind == RootKind.LIVE_CONVENTION && source.selection != SourceSelection.CONVENTION && source.selection != SourceSelection.UNCONFIGURED) {
            throw new IllegalArgumentException("A live convention root cannot select an explicit source.");
        }
        List<MutationOccurrence> shadowed = new ArrayList<>(shadowedConfiguration);
        for (MutationOccurrence binding : shadowed) {
            Source.known(SourceSelection.EXPLICIT, binding);
            if (binding.equals(source.occurrence)) {
                throw new IllegalArgumentException("The selected source cannot also be shadowed.");
            }
        }
        this.shadowedConfiguration = Collections.unmodifiableList(shadowed);
        List<String> reasons = new ArrayList<>(partialReasons);
        for (String reason : reasons) {
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("Partial coverage needs nonempty reasons.");
            }
        }
        if (source.knowledge == SourceKnowledge.UNAVAILABLE && reasons.isEmpty()) {
            throw new IllegalArgumentException("Unavailable provenance must have partial coverage.");
        }
        if (source.occurrence != null && source.occurrence.getOperation().getKind() == SemanticOperation.Kind.UNCLASSIFIED_BINDING && reasons.isEmpty()) {
            throw new IllegalArgumentException("An unclassified binding must have partial coverage.");
        }
        this.partialReasons = Collections.unmodifiableList(reasons);
    }

    public static EffectiveProvenanceView captured(TargetContext target, Source source, UpdateSequence updates, @Nullable MutationOccurrence convention) {
        List<MutationOccurrence> shadowed = convention == null || convention.equals(source.getOccurrence())
            ? Collections.emptyList() : Collections.singletonList(convention);
        MutationOccurrence selected = source.getOccurrence();
        List<String> reasons = selected != null && selected.getOperation().getKind() == SemanticOperation.Kind.UNCLASSIFIED_BINDING
            ? Collections.singletonList(selected.getOperation().getReason()) : Collections.emptyList();
        return new EffectiveProvenanceView(target, RootKind.CAPTURED, source, updates, shadowed, reasons);
    }

    public TargetContext getTarget() {
        return target;
    }

    public RootKind getRootKind() {
        return rootKind;
    }

    public Source getSource() {
        return source;
    }

    public UpdateSequence getUpdates() {
        return updates;
    }

    public List<MutationOccurrence> getShadowedConfiguration() {
        return shadowedConfiguration;
    }

    /** Complete local coverage says nothing about upstream Provider dependencies or failure causality. */
    public boolean isCompleteLocal() {
        return partialReasons.isEmpty();
    }

    public List<String> getPartialReasons() {
        return partialReasons;
    }
}
