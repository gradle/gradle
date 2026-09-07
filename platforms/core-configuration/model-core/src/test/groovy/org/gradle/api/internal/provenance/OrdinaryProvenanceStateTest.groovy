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

package org.gradle.api.internal.provenance

import spock.lang.Specification

import static org.gradle.api.internal.provenance.EffectiveProvenanceView.SourceSelection.*
import static org.gradle.api.internal.provenance.SemanticOperation.EXPLICIT_BINDING
import static org.gradle.api.internal.provenance.SemanticOperation.update

/** Exercises the ordinary policy using only descriptors: no Property, Provider, host or user-code context. */
class OrdinaryProvenanceStateTest extends Specification {
    def owner = new ScopeIdentity('build', ':project')
    def attribution = new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, 'plugin'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'plugin', 'plugin'), owner, 'application')
    def state = new OrdinaryProvenanceState(owner, 'property-1')
    def map = update(SemanticOperation.Shape.MAP)

    def 'source selection depends on accepted configuration and not value presence'() {
        expect:
        view().source.selection == UNCONFIGURED

        when:
        state.acceptedConvention(attribution, false)
        def convention = state.lastAcceptedMutation
        state.acceptedBinding(attribution, EXPLICIT_BINDING)
        def explicit = state.lastAcceptedMutation

        then:
        view().source.selection == EXPLICIT
        view().source.occurrence.is(explicit)
        view().shadowedConfiguration == [convention]

        when:
        state.acceptedClearExplicit(attribution)

        then:
        view().source.selection == CONVENTION
        view().source.occurrence.is(convention)

        when:
        state.acceptedClearConvention(attribution, false)

        then:
        view().source.selection == UNCONFIGURED
        view().shadowedConfiguration.empty
    }

    def 'an update keeps its captured root even when another binding was accepted in between'() {
        given:
        state.acceptedConvention(attribution, false)
        def captured = view()
        state.acceptedBinding(attribution, EXPLICIT_BINDING)

        when:
        state.acceptedUpdate(attribution, map, captured.source, captured.updates)
        def update = state.lastAcceptedMutation
        state.acceptedConvention(attribution, true)

        then:
        view().source.occurrence.is(captured.source.occurrence)
        view().source.selection == CONVENTION
        view().updates.inApplicationOrder() == [update]
        view().shadowedConfiguration == [state.lastAcceptedMutation]
        update.sequence == 2
        captured.updates.size() == 0
    }

    def 'source replacement cuts ordinary updates while existing checkpoints retain identity'() {
        given:
        state.acceptedBinding(attribution, EXPLICIT_BINDING)
        def source = state.source
        state.acceptedUpdate(attribution, map, source, state.updates)
        def first = state.lastAcceptedMutation
        state.acceptedUpdate(attribution, map, source, state.updates)
        def second = state.lastAcceptedMutation
        def checkpoint = view()

        when:
        state.acceptedBinding(attribution, EXPLICIT_BINDING)

        then:
        first != second
        first.attribution.is(second.attribution)
        checkpoint.updates.inApplicationOrder() == [first, second]
        view().updates.size() == 0
        view().source.occurrence.is(state.lastAcceptedMutation)
    }

    def 'promotion preserves binding identity and explicit selection'() {
        given:
        state.acceptedConvention(attribution, false)
        def convention = state.lastAcceptedMutation

        when:
        state.acceptedPromotion(attribution)
        def promotion = state.lastAcceptedMutation
        state.acceptedClearConvention(attribution, true)

        then:
        promotion.operation.kind == SemanticOperation.Kind.PROMOTE_CONVENTION
        view().source.selection == EXPLICIT
        view().source.occurrence.is(convention)
        view().shadowedConfiguration.empty
    }

    def 'partial binding coverage survives structural updates'() {
        given:
        state.acceptedBinding(attribution, SemanticOperation.unclassifiedBinding('opaque provider shape'))
        def source = state.source

        when:
        state.acceptedUpdate(attribution, map, source, state.updates)

        then:
        !view().completeLocal
        view().partialReasons == ['opaque provider shape']
        view().updates.size() == 1
    }

    def 'freezing a pre-calculation checkpoint preserves its source updates and target'() {
        given:
        state.acceptedBinding(attribution, EXPLICIT_BINDING)
        state.acceptedUpdate(attribution, map, state.source, state.updates)
        def checkpoint = view()
        state.acceptedConvention(attribution, true)

        when:
        state.freeze(checkpoint)

        then:
        state.finalized
        state.getEffectiveProvenance('later name').is(checkpoint)
        state.source.is(checkpoint.source)
        state.updates.is(checkpoint.updates)
        state.getModelPath('later name') == 'extension.message'
        state.convention == null
    }

    def 'separate owners allocate distinct occurrences even with identical descriptors'() {
        given:
        def other = new OrdinaryProvenanceState(owner, 'property-2')

        when:
        state.acceptedBinding(attribution, EXPLICIT_BINDING)
        other.acceptedBinding(attribution, EXPLICIT_BINDING)

        then:
        state.lastAcceptedMutation != other.lastAcceptedMutation
        state.lastAcceptedMutation.attribution.is(other.lastAcceptedMutation.attribution)
    }

    private EffectiveProvenanceView view() {
        state.getEffectiveProvenance('extension.message')
    }
}
