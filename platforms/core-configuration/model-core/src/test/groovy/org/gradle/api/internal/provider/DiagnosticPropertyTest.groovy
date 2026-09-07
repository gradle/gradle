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
import org.gradle.api.internal.provenance.FailedOperation
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.api.provider.Provider
import org.gradle.internal.Describables
import spock.lang.Specification

class DiagnosticPropertyTest extends Specification {
    def scope = new ScopeIdentity('build', ':target')
    def attribution = new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, 'source'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'source', 'source'), scope, 'application')
    def host = Mock(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> 'property'
        currentAttribution() >> attribution
    }
    def property = new DiagnosticProperty<String>(host, String)

    def 'requested explanation never evaluates values producers or captures new attribution'() {
        given:
        def supplier = Mock(ProviderInternal)
        supplier.asSupplier(_, _, _) >> supplier
        property.set(supplier)
        property.attachOwner(null, Describables.of('extension.message'))

        when:
        def report = property.configurationTrace
        def copiedReport = property.shallowCopy().configurationTrace

        then:
        report == copiedReport
        report.contains('Configuration trace to source for extension.message')
        report.contains("explicit source [plugin 'source'")
        0 * supplier._
        0 * host.currentAttribution()
    }

    def 'explicit missing report keeps convention shadowed and original exception as its cause'() {
        given:
        property.convention('secret fallback')
        property.set(Providers.notDefined())
        def accepted = property.lastAcceptedMutation

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.cause.class == MissingValueException
        failure.message.startsWith(failure.cause.message + '\n\nFailure trace to source')
        failure.message.contains('explicit source')
        failure.message.contains('Shadowed configuration (not selected)')
        !failure.message.contains('secret fallback')
        property.lastAcceptedMutation.is(accepted)
        0 * host.currentAttribution()
    }

    def 'unconfigured missing and ordinary nullable queries do not invent sources'() {
        expect:
        property.getOrNull() == null
        property.getOrElse('default') == 'default'
        !property.present

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains('at source (unconfigured)')
        property.lastAcceptedMutation == null
        0 * host.currentAttribution()
    }

    def 'missing source and update occurrences survive #lifecycle'() {
        given:
        property.set(Providers.notDefined())
        property.replace { it.map { throw new AssertionError('missing maps must not run') } }
        def before = property.effectiveProvenance

        when:
        def queried = lifecycle(property)
        queried.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains('at update map')
        failure.message.contains('at explicit source')
        property.effectiveProvenance.updates.is(before.updates)

        where:
        lifecycle << [{ it.shallowCopy() }, { it.finalizeValue(); it }, { it.finalizeValueOnRead(); it }]
    }

    def 'rejected #operation after #closure preserves accepted facts and original cause'() {
        given:
        property.set('accepted secret')
        def before = property.effectiveProvenance
        def accepted = property.lastAcceptedMutation
        property."$closure"()

        when:
        mutation(property)

        then:
        def failure = thrown(IllegalStateException)
        failure.cause.class == IllegalStateException
        failure.message.startsWith(failure.cause.message + '\n\nFailure trace to source')
        failure.message.contains("at failed $operation")
        failure.message.count('Failure trace to source') == 1
        !failure.message.contains('accepted secret')
        !failure.message.contains('rejected secret')
        property.lastAcceptedMutation.is(accepted)
        property.effectiveProvenance.updates.is(before.updates)

        where:
        operation                 | mutation                                             | closure
        'set'                     | { it.set('rejected secret') }                         | 'disallowChanges'
        'set'                     | { it.value(Providers.of('rejected secret')) }          | 'finalizeValue'
        'convention'              | { it.convention('rejected secret') }                  | 'finalizeValue'
        'convention'              | { it.convention(Providers.of('rejected secret')) }     | 'disallowChanges'
        'unset'                   | { it.unset() }                                       | 'disallowChanges'
        'unsetConvention'         | { it.unsetConvention() }                             | 'finalizeValue'
        'setToConvention'         | { it.setToConvention() }                             | 'finalizeValue'
        'setToConventionIfUnset'  | { it.setToConventionIfUnset() }                      | 'disallowChanges'
        'replace'                 | { it.replace { it.map { 'rejected secret' } } }       | 'finalizeValue'
        'replace'                 | { it.replace { null } }                              | 'finalizeValue'
    }

    def 'rejected type checks record the failed caller separately from the accepted source'() {
        given:
        def property = new DiagnosticProperty<Boolean>(host, Boolean)
        property.set(true)
        def accepted = property.lastAcceptedMutation
        def caller = new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, 'caller'),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'caller', 'caller'), scope, 'caller-application')

        when:
        property.set(123)

        then:
        1 * host.currentAttribution() >> caller
        def failure = thrown(IllegalArgumentException)
        failure.cause.class == IllegalArgumentException
        failure.message.contains("failed set [plugin 'caller'")
        failure.message.contains("explicit source [plugin 'source'")
        property.lastAcceptedMutation.is(accepted)
        property.get()
    }

    def 'released or unavailable attribution is reported honestly without masking rejection'() {
        given:
        property.set('value')
        property.finalizeValue()

        when:
        property.set('rejected')

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('failed set [unknown caller origin]')
        failure.message.contains("explicit source [plugin 'source'")
        0 * host.currentAttribution()
    }

    def 'attribution lookup failure cannot replace the original rejection'() {
        given:
        property.set('value')
        property.disallowChanges()

        when:
        property.set('rejected')

        then:
        1 * host.currentAttribution() >> { throw new IllegalStateException('diagnostic lookup failed') }
        def failure = thrown(IllegalStateException)
        failure.cause.message == 'The value for this property cannot be changed any further.'
        failure.message.contains('unknown caller origin')
        !failure.message.contains('diagnostic lookup failed')
    }

    def 'null provider rejection does not create an accepted occurrence'() {
        when:
        property.set((Provider) null)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.cause.message == 'Cannot set the value of a property using a null provider.'
        property.lastAcceptedMutation == null
    }

    def 'shared failure adapter preserves cause identity and does not decorate twice'() {
        given:
        def cause = new RuntimeException('original cause')
        def original = new IllegalArgumentException('original problem', cause)
        def view = property.effectiveProvenance
        def attempt = new FailedOperation('set', attribution)

        when:
        def decorated = PropertyProvenanceDiagnostics.mutation(original, view, attempt)
        def again = PropertyProvenanceDiagnostics.mutation(decorated, view, attempt)

        then:
        decorated.cause.is(original)
        decorated.cause.cause.is(cause)
        again.is(decorated)
        decorated.message.count('Failure trace to source') == 1
        property.lastAcceptedMutation == null
    }

    def 'successful operations are silent and keep values and upstream liveness'() {
        given:
        def value = 'first'
        property.convention(new DefaultProvider({ value }))
        property.replace { it.map { it + '-mapped' } }
        def copy = property.shallowCopy()

        when:
        value = 'second'

        then:
        property.get() == 'second-mapped'
        copy.get() == 'second-mapped'
        0 * host.currentAttribution()
    }
}
