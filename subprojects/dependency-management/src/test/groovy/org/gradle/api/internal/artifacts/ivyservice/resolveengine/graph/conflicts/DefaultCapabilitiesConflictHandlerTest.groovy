/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState
import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class DefaultCapabilitiesConflictHandlerTest extends Specification {

    @Subject
    DefaultCapabilitiesConflictHandler handler = new DefaultCapabilitiesConflictHandler()

    private long id

    @Issue("gradle/gradle#5920")
    def "order of components should be preserved"() {
        PotentialConflict conflict
        CapabilityInternal capability = capability()
        ComponentState cs1 = component("g", "m1")
        ComponentState cs2 = component("g", "m2")

        def expectedIds = [cs1, cs2].collect { it.id.module }

        when:
        conflict = handler.registerCandidate(
            candidate(capability, cs1)
        )

        then:
        !conflict.conflictExists()

        when:
        conflict = handler.registerCandidate(
            candidate(capability, cs2)
        )

        then:
        conflict.conflictExists()

        when:
        // use a reasonably high number so that the test becomes at best flaky if we break the contract
        50.times {
            ComponentState cs = component("group", "m_${it}")
            expectedIds << cs.id.module
            conflict = handler.registerCandidate(
                candidate(capability, cs)
            )
        }

        then:
        def actualIds = []
        conflict.withParticipatingModules {
            actualIds << it
        }

        actualIds == expectedIds
    }

    CapabilitiesConflictHandler.Candidate candidate(CapabilityInternal cap, ComponentState co) {
        Mock(CapabilitiesConflictHandler.Candidate) {
            getNode() >> node(co)
            getCapability() >> cap
            getImplicitCapabilityProviders() >> []
        }
    }

    ComponentState component(String group="group", String name="name", String version="1.0") {
        ModuleIdentifier module = DefaultModuleIdentifier.newId(group, name)
        ModuleVersionIdentifier mvi = DefaultModuleVersionIdentifier.newId(module, version)
        Mock(ComponentState) {
            getId() >> mvi
            getComponentId() >> DefaultModuleComponentIdentifier.newId(mvi)
            isCandidateForConflictResolution() >> true
        }
    }

    CapabilityInternal capability(String group="org", String name="cap") {
        Mock(CapabilityInternal) {
            getGroup() >> group
            getName() >> name
        }
    }

    NodeState node(ComponentState cs) {
        return new NodeState(id++, Mock(ResolvedConfigurationIdentifier) { getId() >> Mock(ModuleVersionIdentifier) }, cs, Mock(VariantGraphResolveMetadata) {
            getDependencies() >> []
            getCapabilities() >> ImmutableCapabilities.of([])
        }, true) {
            @Override
            boolean isSelected() {

                return true;
            }
        }
    }
}
