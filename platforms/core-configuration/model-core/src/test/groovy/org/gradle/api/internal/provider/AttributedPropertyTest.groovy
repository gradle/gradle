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
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.api.provider.Provider
import org.gradle.internal.Describables
import spock.lang.Specification

import static org.gradle.api.internal.provenance.SemanticOperation.Kind.*

class AttributedPropertyTest extends Specification {
    def attribution = new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, 'binder'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'binder', 'binder'), new ScopeIdentity('build', ':source'), 'application')
    def host = Mock(PropertyProvenanceHost) {
        newOccurrenceScope() >> 'property-1'
        getOwnerScope() >> new ScopeIdentity('build', ':target')
    }
    def property = new AttributedProperty<String>(host, String)

    def 'only the enabled scalar factory creates attributed properties'() {
        expect:
        property instanceof AttributedProperty
        new DefaultPropertyFactory(host).property(String) instanceof DiagnosticProperty
        new DefaultPropertyFactory(PropertyHost.NO_OP).property(String).class == DefaultProperty
        !(new DefaultPropertyFactory(host).listProperty(String) instanceof AttributedProperty)
        !(new DefaultPropertyFactory(host).setProperty(String) instanceof AttributedProperty)
        !(new DefaultPropertyFactory(host).mapProperty(String, String) instanceof AttributedProperty)
        new DefaultPropertyFactory(host).propertyOfAnyType(String).class == DefaultProperty
        new DefaultPropertyFactory(host).propertyWithNoType().class == DefaultProperty
        property.lastAcceptedMutation == null
    }

    def 'successful #label records exactly one semantic occurrence'() {
        when:
        mutation(property)

        then:
        1 * host.currentAttribution() >> attribution
        property.lastAcceptedMutation.sequence == 0
        property.lastAcceptedMutation.operation.kind == kind
        property.lastAcceptedMutation.attribution.is(attribution)

        where:
        label             | mutation                                  | kind
        'set value'       | { it.set('value') }                        | EXPLICIT_BINDING
        'set provider'    | { it.set(new DefaultProvider({ null })) }  | EXPLICIT_BINDING
        'value alias'     | { it.value('value') }                      | EXPLICIT_BINDING
        'convention'      | { it.convention('value') }                 | CONVENTION_BINDING
        'null convention' | { it.convention((String) null) }           | CONVENTION_BINDING
        'unset'           | { it.unset() }                            | CLEAR_EXPLICIT
        'null set'        | { it.set((String) null) }                  | CLEAR_EXPLICIT
        'unsetConvention' | { it.unsetConvention() }                  | CLEAR_CONVENTION
    }

    def 'repeated mutations share attribution and remain distinct without retaining history'() {
        when:
        property.set('first')
        def first = property.lastAcceptedMutation
        property.set('second')
        def second = property.lastAcceptedMutation

        then:
        2 * host.currentAttribution() >> attribution
        first != second
        first.scope == second.scope
        first.sequence == 0
        second.sequence == 1
        first.attribution.is(second.attribution)
        property.get() == 'second'
    }

    def 'type rejection preserves the accepted fact and original exception'() {
        given:
        def property = new AttributedProperty<Boolean>(host, Boolean)
        host.currentAttribution() >> attribution
        property.set(true)
        def accepted = property.lastAcceptedMutation

        when:
        property.set(123)

        then:
        thrown(IllegalArgumentException)
        0 * host.currentAttribution()
        property.lastAcceptedMutation.is(accepted)
        property.get()
    }

    def 'rejected #mutation after #closure preserves accepted attribution and exact message'() {
        given:
        host.currentAttribution() >> attribution
        def ordinary = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        [property, ordinary].each { it.set('accepted'); it."$closure"() }
        def accepted = property.lastAcceptedMutation
        def failures = []

        when:
        [property, ordinary].each {
            try {
                it."$mutation"('rejected')
            } catch (IllegalStateException failure) {
                failures.add(failure)
            }
        }

        then:
        0 * host.currentAttribution()
        failures.size() == 2
        failures[0].message == failures[1].message
        property.lastAcceptedMutation.is(accepted)

        where:
        mutation     | closure
        'set'        | 'finalizeValue'
        'convention' | 'finalizeValue'
        'set'        | 'disallowChanges'
        'convention' | 'disallowChanges'
    }

    def 'null provider rejection and throwing replace do not capture attribution'() {
        when:
        property.set((Provider) null)

        then:
        thrown(IllegalArgumentException)
        0 * host.currentAttribution()
        property.lastAcceptedMutation == null

        when:
        property.replace { throw new IllegalStateException('original failure') }

        then:
        def failure = thrown(IllegalStateException)
        failure.message == 'original failure'
        0 * host.currentAttribution()
        property.lastAcceptedMutation == null
    }

    def 'binding is lazy and lazy type failures do not erase accepted binding facts'() {
        given:
        def property = new AttributedProperty<Boolean>(host, Boolean)
        def calls = 0

        when:
        property.set(new DefaultProvider({ calls++; 42 }))
        def accepted = property.lastAcceptedMutation

        then:
        1 * host.currentAttribution() >> attribution
        calls == 0
        accepted.operation.kind == EXPLICIT_BINDING

        when:
        property.get()

        then:
        thrown(IllegalArgumentException)
        calls == 1
        property.lastAcceptedMutation.is(accepted)
        0 * host.currentAttribution()
    }

    def 'queries and target descriptions do not capture or evaluate attribution'() {
        given:
        host.currentAttribution() >> attribution
        property.set('value')
        property.attachOwner(null, Describables.of('extension.message'))

        when:
        def target = property.provenanceTarget
        def value = property.get()
        property.isPresent()
        property.getOrNull()
        property.finalizeValue()
        property.get()

        then:
        target.owner == new ScopeIdentity('build', ':target')
        target.modelPath == 'extension.message'
        value == 'value'
        0 * host.currentAttribution()
    }

    def 'conditional promotion records only an accepted selection change'() {
        given:
        host.currentAttribution() >> attribution
        property.convention('default')
        def convention = property.lastAcceptedMutation

        when:
        property.setToConventionIfUnset()
        def promoted = property.lastAcceptedMutation
        property.setToConventionIfUnset()

        then:
        1 * host.currentAttribution() >> attribution
        promoted.sequence == convention.sequence + 1
        promoted.operation.kind == PROMOTE_CONVENTION
        property.lastAcceptedMutation.is(promoted)
    }
}
