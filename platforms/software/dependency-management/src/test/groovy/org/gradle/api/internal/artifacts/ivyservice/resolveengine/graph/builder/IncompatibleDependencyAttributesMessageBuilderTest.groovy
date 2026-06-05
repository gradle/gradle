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

class IncompatibleDependencyAttributesMessageBuilderTest extends Specification {

    def "buildMergeErrorMessage shows both concrete conflicting values"() {
        given:
        def attribute = Attribute.of('minified', String)
        def sel1 = Mock(ModuleComponentSelector) {
            getAttributes() >> Mock(AttributeContainer) {
                getAttribute(attribute) >> 'true'
            }
        }
        def sel2 = Mock(ModuleComponentSelector) {
            getAttributes() >> Mock(AttributeContainer) {
                getAttribute(attribute) >> 'false'
            }
        }
        def s1 = Mock(SelectorState) {
            getSelector() >> sel1
            getRequested() >> sel1
        }
        def s2 = Mock(SelectorState) {
            getSelector() >> sel2
            getRequested() >> sel2
        }
        def dm = Mock(DependencyMetadata) { isConstraint() >> false }
        def root = Mock(NodeState) {
            getIncomingEdges() >> []
            getDisplayName() >> 'root'
        }
        def e1 = Mock(EdgeState) {
            getSelector() >> s1
            getFrom() >> root
            getDependencyMetadata() >> dm
        }
        def e2 = Mock(EdgeState) {
            getSelector() >> s2
            getFrom() >> root
            getDependencyMetadata() >> dm
        }
        def edges = [e1, e2]
        def module = Mock(ModuleResolveState)
        module.visitAllIncomingEdges(_) >> { Consumer<EdgeState> visitor -> edges.each(visitor.&accept) }
        module.getId() >> DefaultModuleIdentifier.newId('com.example', 'lib')

        def ex = new AttributeMergingException(attribute, 'true', 'false', 'conflict')

        when:
        String msg = IncompatibleDependencyAttributesMessageBuilder.buildMergeErrorMessage(module, ex)

        then:
        msg.contains('Requested values:')
        msg.contains('true')
        msg.contains('false')
    }

    def "buildMergeErrorMessage shows <no value> when attribute is not requested"() {
        given:
        def attribute = Attribute.of('minified', Boolean)
        def sel1 = Mock(ModuleComponentSelector) {
            getAttributes() >> Mock(AttributeContainer) {
                getAttribute(attribute) >> { null }
            }
        }
        def sel2 = Mock(ModuleComponentSelector) {
            getAttributes() >> Mock(AttributeContainer) {
                getAttribute(attribute) >> Boolean.TRUE
            }
        }
        def s1 = Mock(SelectorState) {
            getSelector() >> sel1
            getRequested() >> sel1
        }
        def s2 = Mock(SelectorState) {
            getSelector() >> sel2
            getRequested() >> sel2
        }
        def dm = Mock(DependencyMetadata) { isConstraint() >> false }
        def root = Mock(NodeState) {
            getIncomingEdges() >> []
            getDisplayName() >> 'root'
        }
        def e1 = Mock(EdgeState) {
            getSelector() >> s1
            getFrom() >> root
            getDependencyMetadata() >> dm
        }
        def e2 = Mock(EdgeState) {
            getSelector() >> s2
            getFrom() >> root
            getDependencyMetadata() >> dm
        }
        def edges = [e1, e2]
        def module = Mock(ModuleResolveState)
        module.visitAllIncomingEdges(_) >> { Consumer<EdgeState> visitor -> edges.each(visitor.&accept) }
        module.getId() >> DefaultModuleIdentifier.newId('com.example', 'lib')

        def ex = new AttributeMergingException(attribute, null, Boolean.TRUE, 'conflict')

        when:
        String msg = IncompatibleDependencyAttributesMessageBuilder.buildMergeErrorMessage(module, ex)

        then:
        msg.contains('Requested values:')
        msg.contains('<no value>')
        msg.contains('true')
    }
}
