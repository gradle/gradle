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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState
import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import spock.lang.Issue
import spock.lang.Specification

class DefaultCapabilitiesConflictHandlerTest extends Specification {

    Map<ModuleIdentifier, ModuleResolveState> allModules = [:]
    ResolveState resolveState = Mock(ResolveState) {
        findModule(_ as ModuleIdentifier) >> { ModuleIdentifier id ->
            allModules[id]
        }
        getModule(_ as ModuleIdentifier) >> { ModuleIdentifier id ->
            allModules.computeIfAbsent(id, {
                Mock(ModuleResolveState) {
                    getId() >> id
                }
            })
        }
    }

    DefaultCapabilitiesConflictHandler handler = new DefaultCapabilitiesConflictHandler(ImmutableList.of(), resolveState)

    private long id

    @Issue("gradle/gradle#5920")
    def "order of components should be preserved"() {
        CapabilityInternal capability = capability()
        ComponentState cs1 = component("g", "m1")
        ComponentState cs2 = component("g", "m2")

        def conflictingModules = [cs1, cs2].collect { it.module }

        when:
        boolean hasConflict = handler.registerCandidate(node(cs1, capability))

        then:
        !hasConflict

        when:
        hasConflict = handler.registerCandidate(node(cs2, capability))

        then:
        hasConflict

        when:
        // use a reasonably high number so that the test becomes at best flaky if we break the contract
        50.times {
            ComponentState cs = component("group", "m_${it}")
            conflictingModules << cs.module
            hasConflict = handler.registerCandidate(node(cs, capability))
        }

        then:
        conflictingModules.each {
            (1.._) * it.clearSelection()
        }
    }

    ComponentState component(String group="group", String name="name", String version="1.0") {
        def moduleId = DefaultModuleIdentifier.newId(group, name)
        def module = resolveState.getModule(moduleId)
        ModuleVersionIdentifier mvi = DefaultModuleVersionIdentifier.newId(moduleId, version)
        Mock(ComponentState) {
            getId() >> mvi
            getComponentId() >> DefaultModuleComponentIdentifier.newId(mvi)
            isCandidateForConflictResolution() >> true
            getModule() >> module
            isSelected() >> true
            getImplicitCapability() >> capability(group, name)
        }
    }

    CapabilityInternal capability(String group="org", String name="cap") {
        new DefaultImmutableCapability(group, name, null)
    }

    NodeState node(ComponentState cs, CapabilityInternal capability) {
        def state = Stub(VariantGraphResolveState) {
            getMetadata() >> Stub(VariantGraphResolveMetadata) {
                getCapabilities() >> ImmutableCapabilities.of(capability)
            }
        }
        def node = new NodeState(id++, cs, resolveState, state, true) {
            @Override
            boolean isSelected() {
                return true
            }
        }
        cs.addNode(node)
        return node
    }
}
