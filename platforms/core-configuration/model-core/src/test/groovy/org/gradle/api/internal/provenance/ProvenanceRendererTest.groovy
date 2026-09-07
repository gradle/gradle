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
import static org.gradle.api.internal.provenance.SemanticOperation.*

class ProvenanceRendererTest extends Specification {
    def owner = new ScopeIdentity('build', ':target')
    def state = new OrdinaryProvenanceState(owner, 'property')

    def 'configuration report renders reverse updates source and shadowed convention separately'() {
        given:
        state.acceptedConvention(author('defaults'), false)
        state.acceptedBinding(author('source'), EXPLICIT_BINDING)
        state.acceptedUpdate(author('first'), update(Shape.MAP, Shape.MAP), state.source, state.updates)
        state.acceptedUpdate(author('second'), update(Shape.MAP), state.source, state.updates)

        expect:
        ProvenanceRenderer.configuration(view()) == """Configuration trace to source for extension.message (build 'build', project ':target'):
    at update map [plugin 'second'; source build 'build', scope ':source']
    at update map -> map [plugin 'first'; source build 'build', scope ':source']
    at explicit source [plugin 'source'; source build 'build', scope ':source']
Shadowed configuration (not selected):
    at convention [plugin 'defaults'; source build 'build', scope ':source']
Local configuration only; provider dependencies and failure causality are not inferred."""
    }

    def 'failure attempts are report-local and repeated authors are not collapsed'() {
        given:
        state.acceptedBinding(author('source'), EXPLICIT_BINDING)
        2.times { state.acceptedUpdate(author('same'), update(Shape.MAP), state.source, state.updates) }
        def accepted = state.lastAcceptedMutation

        when:
        def report = ProvenanceRenderer.failure(view(), new FailedOperation('set', author('caller')))

        then:
        report.startsWith('Failure trace to source')
        report.indexOf('failed set') < report.indexOf('update map')
        report.count("plugin 'same'") == 2
        report.contains("failed set [plugin 'caller'")
        state.lastAcceptedMutation.is(accepted)
        state.updates.size() == 2
    }

    def 'unconfigured unattributed unavailable and known unknown-author sources stay distinct'() {
        given:
        def view = new EffectiveProvenanceView(new TargetContext(owner, 'value'), EffectiveProvenanceView.RootKind.CAPTURED,
            source, UpdateSequence.empty(), [], reasons)

        expect:
        ProvenanceRenderer.failure(view, null).contains(expected)

        where:
        source                                                                                                                        | reasons  | expected
        EffectiveProvenanceView.Source.unconfigured()                                                                                  | []       | 'source (unconfigured)'
        EffectiveProvenanceView.Source.unattributed(EXPLICIT)                                                                           | []       | 'explicit source (unattributed)'
        EffectiveProvenanceView.Source.unavailable(EXPLICIT, 'lost')                                                                    | ['lost'] | 'provenance unavailable: lost'
        EffectiveProvenanceView.Source.known(EXPLICIT, new MutationOccurrence('p', 0, unknown(), EXPLICIT_BINDING))                       | []       | 'explicit source [unknown origin;'
    }

    def 'captured convention root is not repeated as shadowed'() {
        given:
        state.acceptedConvention(author('defaults'), false)
        state.acceptedUpdate(author('update'), update(Shape.MAP), state.source, state.updates)

        expect:
        ProvenanceRenderer.configuration(view()).contains('convention source (captured)')
        !ProvenanceRenderer.configuration(view()).contains('Shadowed configuration')
    }

    def 'truncation keeps the selected root and partial coverage visible'() {
        given:
        state.acceptedBinding(author('source'), unclassifiedBinding('unsupported local shape'))
        4096.times { state.acceptedUpdate(author('update'), update(Shape.MAP), state.source, state.updates) }

        when:
        def report = ProvenanceRenderer.configuration(view())

        then:
        report.count('at update map') == 64
        report.contains('4032 earlier updates omitted')
        report.contains("explicit source (unclassified binding) [plugin 'source'")
        report.contains('Partial local provenance:\n    unsupported local shape')
        report.length() < 10000
        state.updates.size() == 4096
    }

    def 'labels cannot inject extra frames or unbounded output'() {
        given:
        state.acceptedBinding(author('plugin\n    at forged' + 'x' * 2000), EXPLICIT_BINDING)

        when:
        def report = ProvenanceRenderer.configuration(view())

        then:
        !report.contains('\n    at forged')
        report.contains('plugin\\n    at forged')
        report.length() < 1200
    }

    private EffectiveProvenanceView view() {
        state.getEffectiveProvenance('extension.message')
    }

    private static Attribution author(String id) {
        new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, id),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, id, id), new ScopeIdentity('build', ':source'), 'application')
    }

    private static Attribution unknown() {
        new Attribution(new ContributorKey('domain', ContributorKey.Kind.UNKNOWN, 'unknown'),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, '', 'unknown'), new ScopeIdentity('build', ':source'), null)
    }
}
