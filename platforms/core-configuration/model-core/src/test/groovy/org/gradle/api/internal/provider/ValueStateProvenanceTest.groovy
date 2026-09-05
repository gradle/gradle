/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.internal.provider.provenance.PropertyProvenanceKind
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRecord
import org.gradle.api.internal.provider.provenance.PropertyProvenanceTrace
import org.gradle.internal.Describables
import spock.lang.Specification

import java.lang.reflect.Modifier

class ValueStateProvenanceTest extends Specification {
    def host = Stub(PropertyHost) {
        tracksPropertyProvenance() >> true
    }

    def "property and disabled state retain their pre-provenance fields"() {
        def state = ValueState.newPropertyState(PropertyHost.NO_OP)

        expect:
        instanceFields(AbstractProperty)*.name.toSet() == ["producer", "displayName", "state", "value"].toSet()
        instanceFields(ValueState).isEmpty()
        instanceFields(state.class)*.name.toSet() == ["host", "flags", "convention"].toSet()
        instanceFields(state.finalState().class).isEmpty()
        state.provenanceHost == null
        state.provenance == null
    }

    def "disabled properties use the shared finalized singleton across hosts"() {
        def otherHost = Stub(PropertyHost)
        def first = ValueState.newPropertyState(PropertyHost.NO_OP).finalState()
        def second = ValueState.newPropertyState(otherHost).finalState()

        expect:
        first.is(second)
        first.finalized
        first.finalState().is(first)
        first.provenanceHost == null
    }

    def "only the property factory consults the provenance switch and only once"() {
        def propertyHost = Mock(PropertyHost)

        when:
        def state = ValueState.newPropertyState(propertyHost)
        def finalized = state.finalState()
        state.provenanceHost
        finalized.provenanceHost

        then:
        1 * propertyHost.tracksPropertyProvenance() >> enabled
        0 * propertyHost._
        (finalized.provenanceHost != null) == enabled

        where:
        enabled << [false, true]
    }

    def "general purpose and copier states do not opt configurable file collections into provenance"() {
        def propertyHost = Mock(PropertyHost)

        when:
        def ordinary = ValueState.newState(propertyHost)
        def copying = ValueState.newState(propertyHost, { new ArrayList(it) })
        def convention = ["entry"]
        copying.setConvention(convention)
        def value = copying.implicitValue()

        then:
        0 * propertyHost._
        ordinary.provenanceHost == null
        copying.provenanceHost == null
        value == convention
        !value.is(convention)
        ordinary.finalState().is(copying.finalState())
    }

    def "finalized reads bypass host checks and finalization callbacks with provenance enabled = #enabled"() {
        def propertyHost = Mock(PropertyHost)
        def callbacks = 0

        when:
        def finalized = ValueState.newPropertyState(propertyHost).finalState()
        finalized.finalizeOnReadIfNeeded(Describables.of("value"), null, ValueSupplier.ValueConsumer.DisallowUnsafeRead, { callbacks++ })

        then:
        1 * propertyHost.tracksPropertyProvenance() >> enabled
        0 * propertyHost._
        callbacks == 0

        where:
        enabled << [false, true]
    }

    def "mutable reads still enforce unsafe-read checks with provenance enabled = #enabled"() {
        def propertyHost = Mock(PropertyHost)
        def callbacks = 0

        when:
        def state = ValueState.newPropertyState(propertyHost)
        state.disallowUnsafeRead()
        state.finalizeOnReadIfNeeded(Describables.of("value"), null, ValueSupplier.ValueConsumer.IgnoreUnsafeRead, { callbacks++ })

        then:
        1 * propertyHost.tracksPropertyProvenance() >> enabled
        1 * propertyHost.beforeRead(null) >> "still configuring"
        0 * propertyHost._
        def failure = thrown(IllegalStateException)
        failure.message == "Cannot query the value of value because still configuring."
        callbacks == 0

        where:
        enabled << [false, true]
    }

    def "enabled mutable state is its own metadata view and copies detach only descriptors"() {
        def state = ValueState.newPropertyState(host)
        def convention = origin("default", PropertyProvenanceKind.CONVENTION)
        def explicit = origin("explicit", PropertyProvenanceKind.EXPLICIT_SOURCE)

        expect:
        state.provenanceHost.is(host)
        state.provenance == null

        when:
        state.recordConventionProvenance(convention)
        state.recordExplicitProvenance(explicit)
        def copy = state.provenance.copy()
        state.selectConventionProvenance()
        state.discardConventionProvenance()

        then:
        state.provenance.is(state)
        copy.explicitSource.is(explicit)
        copy.convention.is(convention)
        copy.hasShadowedConvention()
        !(copy instanceof ValueState)
        instanceFields(copy.class).every { Modifier.isFinal(it.modifiers) }
        references(copy).every { !it.is(host) && !it.is(state) }
        state.provenance.explicitSource == null
        state.provenance.convention == null
    }

    def "finalized metadata is immutable and does not retain the mutable state or convention supplier"() {
        def state = ValueState.newPropertyState(host)
        def conventionSupplier = new DefaultProvider<String>({ "unused" })
        state.setConvention(conventionSupplier)
        state.recordConventionProvenance(origin("default", PropertyProvenanceKind.CONVENTION))
        state.promoteConventionProvenance()
        def snapshot = new PropertyProvenanceTrace().snapshot()
        if (withSnapshot) {
            state.finalizeProvenance(snapshot)
        }

        when:
        def finalized = state.finalState()
        state.recordConventionProvenance(origin("later", PropertyProvenanceKind.CONVENTION))

        then:
        finalized.finalized
        finalized.finalizing
        finalized.disallowChanges
        finalized.explicit
        !finalized.shouldFinalize(Describables.of("value"), null)
        finalized.finalState().is(finalized)
        finalized.provenanceHost.is(host)
        finalized.provenance.is(finalized)
        finalized.convention() == null
        !finalized.provenance.hasShadowedConvention()
        withSnapshot ? finalized.provenance.finalizedSnapshot.is(snapshot) : finalized.provenance.explicitSource.originDisplayName == "default"
        references(finalized).every { !it.is(state) && !it.is(conventionSupplier) }
        instanceFields(finalized.class).every { Modifier.isFinal(it.modifiers) }

        where:
        withSnapshot << [false, true]
    }

    def "an enabled finalized property without a recorded binding still reports the failure origin"() {
        def propertyHost = Stub(PropertyHost) {
            tracksPropertyProvenance() >> true
            currentPropertyFailure(_) >> origin("task ':consumer' action", PropertyProvenanceKind.SET)
        }
        def property = new DefaultProperty<String>(propertyHost, String)
        property.set("fixed")
        property.finalizeValue()

        when:
        property.set("rejected")

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains("at task ':consumer' action [set()]")
        property.finalized
        property.get() == "fixed"
    }

    private static PropertyProvenanceRecord origin(String displayName, PropertyProvenanceKind kind) {
        new PropertyProvenanceRecord(displayName, kind, null)
    }

    private static List instanceFields(Class<?> type) {
        type.declaredFields.findAll { !Modifier.isStatic(it.modifiers) }
    }

    private static List references(Object value) {
        instanceFields(value.class).findAll { !it.type.primitive }.collect {
            it.accessible = true
            it.get(value)
        }
    }
}
