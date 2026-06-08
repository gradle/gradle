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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.attributes.AttributeMergingException
import org.gradle.internal.component.model.DependencyMetadata
import spock.lang.Specification

import java.util.function.Consumer

class IncompatibleDependencyAttributesExceptionTest extends Specification {

    def "message lists each conflicting constraint value and resolutions advise reconciling or defining a compatibility rule"() {
        given:
        def attribute = Attribute.of('minified', String)
        def edges = [
            mockConstraintEdge(attribute, 'true'),
            mockConstraintEdge(attribute, 'false')
        ]
        def module = mockModule(edges)
        def cause = new AttributeMergingException(attribute, 'true', 'false', 'conflict')

        when:
        def ex = new IncompatibleDependencyAttributesException(module, cause)

        then:
        ex.message.contains("Cannot select a variant of 'com.example:lib' because the dependency requirements request incompatible values for attribute 'minified'.")
        ex.message.contains('Requested values: true, false')
        ex.message.contains("Constraint path: root requires")
        ex.message.contains('with attribute minified = true')
        ex.message.contains('with attribute minified = false')

        and:
        ex.resolutions.size() == 2
        ex.resolutions[0] == "Configure all dependencies to use the same 'minified' attribute value."
        ex.resolutions[1].startsWith('For advanced cases where different values should be treated as compatible, define a compatibility rule. See: ')
        ex.resolutions[1].contains('/userguide/variant_attributes.html#sec:abm-compatibility-rules')
    }

    def "edges that do not request the conflicting attribute are omitted from the message"() {
        given:
        def attribute = Attribute.of('minified', String)
        def edges = [
            mockConstraintEdge(attribute, 'true'),
            mockDependencyEdge(attribute, null),
            mockConstraintEdge(attribute, 'false')
        ]
        def module = mockModule(edges)
        def cause = new AttributeMergingException(attribute, 'true', 'false', 'conflict')

        when:
        def ex = new IncompatibleDependencyAttributesException(module, cause)

        then:
        !ex.message.contains('<no value>')
        ex.message.contains('Requested values: true, false')
        ex.message.count('with attribute minified =') == 2
    }

    def "a dependency that declares the attribute is shown with verb 'depends on'"() {
        given:
        def attribute = Attribute.of('minified', String)
        def edges = [
            mockDependencyEdge(attribute, 'true'),
            mockConstraintEdge(attribute, 'false')
        ]
        def module = mockModule(edges)
        def cause = new AttributeMergingException(attribute, 'true', 'false', 'conflict')

        when:
        def ex = new IncompatibleDependencyAttributesException(module, cause)

        then:
        ex.message.contains("Dependency path: root depends on")
        ex.message.contains('with attribute minified = true')
        ex.message.contains("Constraint path: root requires")
        ex.message.contains('with attribute minified = false')
    }

    private EdgeState mockConstraintEdge(Attribute<?> attribute, Object value) {
        return mockEdge(attribute, value, true)
    }

    private EdgeState mockDependencyEdge(Attribute<?> attribute, Object value) {
        return mockEdge(attribute, value, false)
    }

    private EdgeState mockEdge(Attribute<?> attribute, Object value, boolean constraint) {
        def attrs = Mock(AttributeContainer)
        attrs.getAttribute(attribute) >> value
        def componentSelector = Mock(ModuleComponentSelector)
        componentSelector.getAttributes() >> attrs
        componentSelector.toString() >> 'org.example:lib:1.0'
        def selector = Mock(SelectorState)
        selector.getSelector() >> componentSelector
        selector.getRequested() >> componentSelector
        def root = Mock(NodeState)
        root.getIncomingEdges() >> []
        root.getDisplayName() >> 'root'
        def metadata = Mock(DependencyMetadata)
        metadata.isConstraint() >> constraint
        def edge = Mock(EdgeState)
        edge.getSelector() >> selector
        edge.getFrom() >> root
        edge.getDependencyMetadata() >> metadata
        return edge
    }

    private ModuleResolveState mockModule(List<EdgeState> edges) {
        def module = Mock(ModuleResolveState)
        module.visitAllIncomingEdges(_) >> { Consumer<EdgeState> visitor -> edges.each(visitor.&accept) }
        module.getId() >> DefaultModuleIdentifier.newId('com.example', 'lib')
        return module
    }
}
