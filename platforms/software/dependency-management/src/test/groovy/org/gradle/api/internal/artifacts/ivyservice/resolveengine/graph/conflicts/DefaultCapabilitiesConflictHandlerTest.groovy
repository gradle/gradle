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

    @Issue("gradle/gradle#5920")
    def "order of components should be preserved"() {
        CapabilityInternal capability = capability()
        ComponentState cs1 = component("g", "m1")
        ComponentState cs2 = component("g", "m2")
        def node1 = node(cs1, capability)
        def node2 = node(cs2, capability)
        // use a reasonably high number so that the test becomes at best flaky if we break the contract
        def extraNodes = (1..50).collect {
            ComponentState cs = component("group", "m_${it}")
            this.node(cs, capability)
        }
        def conflictingNodes = [node1, node2] + extraNodes

        when:
        boolean hasConflict = handler.registerCandidate(node1)

        then:
        !hasConflict

        when:
        hasConflict = handler.registerCandidate(node2)

        then:
        hasConflict
        1 * node1.markInCapabilityConflict()
        1 * node2.markInCapabilityConflict()

        when:
        extraNodes.each {
            handler.registerCandidate(it)
        }

        then:
        extraNodes.each {
            1 * it.markInCapabilityConflict()
        }
    }

    ComponentState component(String group="group", String name="name", String version="1.0") {
        def moduleId = DefaultModuleIdentifier.newId(group, name)
        def module = resolveState.getModule(moduleId)
        ModuleVersionIdentifier mvi = DefaultModuleVersionIdentifier.newId(moduleId, version)
        Mock(ComponentState) {
            getId() >> mvi
            getComponentId() >> DefaultModuleComponentIdentifier.newId(mvi)
            isNotEvicted() >> true
            getModule() >> module
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
        def node = Mock(NodeState) {
            isSelected() >> true
            getComponent() >> cs
            getResolveState() >> state
            getMetadata() >> state.metadata
        }
        cs.addNode(node)
        return node
    }
}
