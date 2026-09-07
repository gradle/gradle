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

package org.gradle.api.internal.provider

import org.gradle.api.internal.provenance.Attribution
import org.gradle.api.internal.provenance.ContributorKey
import org.gradle.api.internal.provenance.DiagnosticOrigin
import org.gradle.api.internal.provenance.EffectiveProvenanceView
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.api.internal.provenance.SemanticOperation
import org.gradle.api.provider.Provider
import spock.lang.Specification

import static org.gradle.api.internal.provenance.EffectiveProvenanceView.SourceSelection.*
import static org.gradle.api.internal.provenance.SemanticOperation.Kind.*

class EffectivePropertyProvenanceTest extends Specification {
    def scope = new ScopeIdentity('build', ':project')
    def origin = new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'plugin', 'plugin')
    def attribution = new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, 'plugin'), origin, scope, 'application')
    def host = Stub(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> 'property'
        currentAttribution() >> attribution
    }
    def property = new AttributedProperty<String>(host, String)

    def 'selection follows bindings and clears rather than provider presence'() {
        expect:
        property.effectiveProvenance.source.selection == UNCONFIGURED

        when:
        property.convention('fallback')
        def convention = property.lastAcceptedMutation
        property.set(Providers.notDefined())
        def explicit = property.lastAcceptedMutation
        def view = property.effectiveProvenance

        then:
        view.source.selection == EXPLICIT
        view.source.occurrence.is(explicit)
        view.shadowedConfiguration == [convention]
        !property.present

        when:
        property.unset()

        then:
        property.effectiveProvenance.source.occurrence.is(convention)
        property.get() == 'fallback'
        view.source.occurrence.is(explicit)

        when:
        property.unsetConvention()

        then:
        property.effectiveProvenance.source.selection == UNCONFIGURED
    }

    def 'compound maps are one occurrence and repeated replace calls remain distinct'() {
        given:
        property.set('root')
        def root = property.lastAcceptedMutation
        def calls = 0

        when:
        property.replace { it.map { calls++; it + '-a' }.map { calls++; it + '-b' } }
        def first = property.lastAcceptedMutation
        property.replace { it.map { calls++; it + '-c' } }
        def second = property.lastAcceptedMutation
        def view = property.effectiveProvenance

        then:
        calls == 0
        view.source.occurrence.is(root)
        view.updates.inApplicationOrder() == [first, second]
        first.operation.kind == UPDATE
        first.operation.shapes == [SemanticOperation.Shape.MAP, SemanticOperation.Shape.MAP]
        second.operation.kind == UPDATE
        first != second
        view.completeLocal
        property.get() == 'root-a-b-c'
        calls == 3

        when:
        property.set('replacement')

        then:
        property.effectiveProvenance.updates.size() == 0
        property.effectiveProvenance.source.occurrence.is(property.lastAcceptedMutation)
        view.updates.size() == 2
    }

    def 'upstream maps belong to dependencies rather than target contributions'() {
        given:
        def upstream = new AttributedProperty<String>(host, String)
        upstream.set('upstream')
        upstream.replace { it.map { it + '-mapped' } }

        when:
        property.set(upstream.map { it + '-bound' })

        then:
        property.effectiveProvenance.updates.size() == 0
        property.effectiveProvenance.source.occurrence.is(property.lastAcceptedMutation)
        property.get() == 'upstream-mapped-bound'
    }

    def 'convention replacement captures its root while upstream inputs remain live'() {
        given:
        def input = 'first'
        property.convention(new DefaultProvider({ input }))
        def root = property.lastAcceptedMutation
        property.replace { it.map { it + '-update' } }
        def update = property.lastAcceptedMutation
        def copy = property.shallowCopy()

        when:
        property.convention('later convention')
        input = 'second'

        then:
        property.get() == 'second-update'
        copy.get() == 'second-update'
        property.effectiveProvenance.rootKind == EffectiveProvenanceView.RootKind.CAPTURED
        property.effectiveProvenance.source.occurrence.is(root)
        copy.effectiveProvenance.source.occurrence.is(root)
        copy.effectiveProvenance.updates.inApplicationOrder()[0].is(update)

        when:
        property.unset()

        then:
        property.get() == 'later convention'
        property.effectiveProvenance.updates.size() == 0
        copy.get() == 'second-update'
    }

    def 'unsupported replace is explicitly partial and cuts the local update projection'() {
        given:
        property.set('root')
        property.replace { it.map { it + '-map' } }

        when:
        property.replace { it.flatMap { Providers.of(it + '-flat') } }

        then:
        property.lastAcceptedMutation.operation.kind == UNCLASSIFIED_BINDING
        !property.effectiveProvenance.completeLocal
        property.effectiveProvenance.updates.size() == 0
        property.get() == 'root-map-flat'

        when:
        property.replace { it.map { it + '-map' } }

        then:
        !property.effectiveProvenance.completeLocal
        property.effectiveProvenance.updates.size() == 1

        when:
        property.set('new')

        then:
        property.effectiveProvenance.completeLocal
        property.effectiveProvenance.updates.size() == 0
    }

    def 'classification is bounded and does not execute transforms'() {
        given:
        property.set('root')
        def calls = 0

        when:
        property.replace {
            def result = it
            count.times { result = result.map { calls++; it } }
            result
        }

        then:
        calls == 0
        property.lastAcceptedMutation.operation.kind == kind
        property.effectiveProvenance.completeLocal == complete

        where:
        count | kind                 | complete
        1     | UPDATE               | true
        128   | UPDATE               | true
        129   | UNCLASSIFIED_BINDING | false
    }

    def 'failed finalization preserves mutable metadata and successful finalization freezes descriptors'() {
        given:
        def fail = true
        def input = 'first'
        property.set(new DefaultProvider({
            if (fail) { throw new IllegalStateException('original failure') }
            input
        }))
        property.replace { it.map { it + '-updated' } }
        def before = property.effectiveProvenance

        when:
        property.finalizeValue()

        then:
        def failure = thrown(IllegalStateException)
        failure.message == 'original failure'
        !property.@provenance.finalized
        property.@provenanceHost.is(host)
        property.effectiveProvenance.updates.is(before.updates)

        when:
        fail = false
        property.finalizeValue()
        input = 'later'

        then:
        property.get() == 'first-updated'
        property.@provenanceHost == null
        property.effectiveProvenance.source.occurrence.is(before.source.occurrence)
        property.effectiveProvenance.updates.is(before.updates)
        property.shallowCopy().effectiveProvenance.updates.is(property.effectiveProvenance.updates)
    }

    def 'finalization on read uses the same descriptor checkpoint'() {
        given:
        property.set('root')
        property.replace { it.map { it + '-updated' } }
        def before = property.effectiveProvenance
        property.finalizeValueOnRead()

        expect:
        !property.@provenance.finalized
        property.get() == 'root-updated'
        property.@provenanceHost == null
        property.effectiveProvenance.updates.is(before.updates)
    }

    def 'nested replace callback mutations do not change the captured root'() {
        given:
        property.set('captured')
        def root = property.lastAcceptedMutation

        when:
        property.replace { previous ->
            property.set('intermediate')
            previous.map { it + '-updated' }
        }

        then:
        property.get() == 'captured-updated'
        property.effectiveProvenance.source.occurrence.is(root)
        property.lastAcceptedMutation.sequence == 2
        property.effectiveProvenance.updates.size() == 1
    }

    def 'replace returning null retains ordinary clear behavior'() {
        given:
        property.convention('fallback')
        def convention = property.lastAcceptedMutation
        property.set('explicit')
        property.replace { it.map { it + '-updated' } }

        when:
        property.replace { null }

        then:
        property.get() == 'fallback'
        property.lastAcceptedMutation.operation.kind == CLEAR_EXPLICIT
        property.effectiveProvenance.source.occurrence.is(convention)
        property.effectiveProvenance.updates.size() == 0
    }

    def 'direct self assignment remains cyclic and is not a recognized update'() {
        given:
        property.set('root')

        when:
        property.set(property.map { it + '-updated' })

        then:
        property.lastAcceptedMutation.operation.kind == EXPLICIT_BINDING
        property.effectiveProvenance.updates.size() == 0

        when:
        property.get()

        then:
        thrown(org.gradle.internal.evaluation.CircularEvaluationException)
    }

    def 'metadata and snapshot do not retain the property or unrelated suppliers'() {
        given:
        property.set('captured')
        def copy = property.shallowCopy()
        def discarded = new DefaultProvider({ 'discarded' })
        property.set(discarded)
        def discardedView = property.effectiveProvenance
        property.set('replacement')

        expect:
        copy.class.declaredFields.every { field ->
            field.accessible = true
            !field.get(copy).is(property) && !field.get(copy).is(discarded)
        }
        descriptorsOnly(discardedView)
        descriptorsOnly(copy.effectiveProvenance)
        descriptorsOnly(property.effectiveProvenance)
    }

    def 'promotion preserves the convention occurrence and later conventions are shadowed'() {
        given:
        property.convention('first')
        def first = property.lastAcceptedMutation

        when:
        property.setToConvention()
        def promotion = property.lastAcceptedMutation
        property.convention('second')

        then:
        property.get() == 'first'
        promotion.operation.kind == PROMOTE_CONVENTION
        property.effectiveProvenance.source.selection == EXPLICIT
        property.effectiveProvenance.source.occurrence.is(first)
        property.effectiveProvenance.shadowedConfiguration == [property.lastAcceptedMutation]
    }

    def 'rejected replace retains the existing occurrences'() {
        given:
        property.set('root')
        property.replace { it.map { it + '-accepted' } }
        def accepted = property.lastAcceptedMutation
        def before = property.effectiveProvenance
        property.disallowChanges()

        when:
        property.replace { it.map { it + '-rejected' } }

        then:
        thrown(IllegalStateException)
        property.lastAcceptedMutation.is(accepted)
        property.effectiveProvenance.updates.is(before.updates)
        property.get() == 'root-accepted'
    }

    def 'long runtime update chains share immutable prefixes and are discarded by rebinding'() {
        given:
        property.set('root')
        def root = property.lastAcceptedMutation
        property.replace { it.map { it } }
        def prefix = property.effectiveProvenance

        when:
        4095.times { property.replace { it.map { it } } }
        def view = property.effectiveProvenance

        then:
        view.source.occurrence.is(root)
        view.updates.size() == 4096
        prefix.updates.size() == 1
        view.updates.inApplicationOrder()[0].is(prefix.updates.inApplicationOrder()[0])
        view.updates.reverseIterator().next().is(property.lastAcceptedMutation)

        when:
        property.set('replacement')

        then:
        property.effectiveProvenance.updates.size() == 0
        property.get() == 'replacement'
    }

    def 'creating a copy during evaluation does not evaluate or reenter the property'() {
        given:
        def copy
        property.set(new DefaultProvider({
            copy = property.shallowCopy()
            'value'
        }))

        expect:
        property.get() == 'value'
        copy.effectiveProvenance.source.occurrence.is(property.lastAcceptedMutation)
    }

    private static boolean descriptorsOnly(Object object) {
        if (object == null || object instanceof String || object instanceof Number || object instanceof Enum) {
            return true
        }
        if (object instanceof Collection) {
            return object.every { descriptorsOnly(it) }
        }
        assert !(object instanceof Provider)
        assert object.class.package.name == 'org.gradle.api.internal.provenance'
        object.class.declaredFields.findAll { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.every {
            it.accessible = true
            descriptorsOnly(it.get(object))
        }
    }
}
