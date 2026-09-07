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

import org.gradle.api.internal.provider.provenance.Attribution;
import org.gradle.api.internal.provider.provenance.ContributorKey;
import org.gradle.api.internal.provider.provenance.DiagnosticOrigin;
import org.gradle.api.internal.provider.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provider.provenance.MutationOccurrence;
import org.gradle.api.internal.provider.provenance.ScopeIdentity;
import org.gradle.api.internal.provider.provenance.SemanticOperation;
import org.gradle.api.internal.provider.provenance.TargetContext;
import org.gradle.api.internal.provider.provenance.UpdateSequence;

import java.util.Collections;

/** Metadata-only scaling controls: descriptors are shared, accepted occurrences are distinct. */
public class SharedMetadataAllocationProbe {
    public static void main(String[] args) {
        ContributorKey contributor = new ContributorKey("domain", ContributorKey.Kind.PLUGIN_ID, "example");
        DiagnosticOrigin origin = new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, "example", "plugin 'example'");
        ScopeIdentity scope = new ScopeIdentity("build", ":app");
        Attribution attribution = new Attribution(contributor, origin, scope, null);
        SemanticOperation operation = SemanticOperation.update(SemanticOperation.Shape.MAP);
        MutationOccurrence source = new MutationOccurrence("target", 0, attribution, SemanticOperation.CONVENTION_BINDING);
        MutationOccurrence update = new MutationOccurrence("target", 1, attribution, operation);
        ProvenanceAllocationProbe.layout("ContributorKey", contributor);
        ProvenanceAllocationProbe.layout("DiagnosticOrigin", origin);
        ProvenanceAllocationProbe.layout("ScopeIdentity", scope);
        ProvenanceAllocationProbe.layout("Attribution", attribution);
        ProvenanceAllocationProbe.layout("SemanticOperation", operation);
        ProvenanceAllocationProbe.layout("MutationOccurrence", update);
        ProvenanceAllocationProbe.layout("UpdateSequence node", UpdateSequence.empty().append(update));
        TargetContext target = new TargetContext(scope, "extension.message");
        EffectiveProvenanceView.Source selected = EffectiveProvenanceView.Source.known(EffectiveProvenanceView.SourceSelection.CONVENTION, source);
        for (int count : new int[] {0, 1, 8, 128, 4096}) {
            int repetitions = Math.max(128, 65536 / Math.max(1, count));
            ProvenanceAllocationProbe.measure("append " + count, repetitions, () -> {
                UpdateSequence sequence = UpdateSequence.empty();
                for (int i = 0; i < count; i++) {
                    sequence = sequence.append(new MutationOccurrence("target", i + 1, attribution, operation));
                }
                ProvenanceAllocationProbe.consume(sequence);
            });
            UpdateSequence sequence = UpdateSequence.empty();
            for (int i = 0; i < count; i++) {
                sequence = sequence.append(new MutationOccurrence("target", i + 1, attribution, operation));
            }
            UpdateSequence checkpoint = sequence;
            ProvenanceAllocationProbe.measure("checkpoint " + count, repetitions, () -> ProvenanceAllocationProbe.consume(
                new EffectiveProvenanceView(target, EffectiveProvenanceView.RootKind.CAPTURED, selected, checkpoint, Collections.emptyList(), Collections.emptyList())
            ));
        }
    }
}
