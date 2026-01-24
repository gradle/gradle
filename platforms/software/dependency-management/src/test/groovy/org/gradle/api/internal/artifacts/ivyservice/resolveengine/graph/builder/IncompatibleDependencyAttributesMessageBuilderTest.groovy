package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.attributes.AttributeMergingException
import org.gradle.internal.component.model.DependencyMetadata
import spock.lang.Specification

class IncompatibleDependencyAttributesMessageBuilderTest extends Specification {

    def setup() {
        MessageBuilderHelper.metaClass.static.formattedPathsTo = { _ -> ['Dependency path: A'] }
    }

    def cleanup() {
        MessageBuilderHelper.metaClass = null
    }

    def "buildMergeErrorMessage shows both concrete conflicting values"() {

        given:
        def attribute = Attribute.of('minified', String)
        def sel1 = Mock(org.gradle.api.artifacts.component.ModuleComponentSelector)
        def sel2 = Mock(org.gradle.api.artifacts.component.ModuleComponentSelector)

        def dm1 = Mock(DependencyMetadata)
        def dm2 = Mock(DependencyMetadata)
        dm1.getSelector() >> sel1
        dm2.getSelector() >> sel2

        def s1 = Mock(SelectorState)
        def s2 = Mock(SelectorState)
        s1.getDependencyMetadata() >> dm1
        s2.getDependencyMetadata() >> dm2

        s1.getRequested() >> sel1
        s2.getRequested() >> sel2
        def e1 = Mock(EdgeState)
        def e2 = Mock(EdgeState)

        e1.getSelector() >> s1
        e2.getSelector() >> s2
        e1.getDependencyMetadata() >> dm1
        e2.getDependencyMetadata() >> dm2

        dm1.isConstraint() >> false
        dm2.isConstraint() >> false

        e1.getFrom() >> Mock(NodeState) {
            getIncomingEdges() >> []
            getDisplayName() >> 'root'
        }

        e2.getFrom() >> Mock(NodeState) {
            getIncomingEdges() >> []
            getDisplayName() >> 'root'
        }

        def module = Mock(ModuleResolveState)
        module.getIncomingEdges() >> ([e1, e2] as Set)
        module.getUnattachedEdges() >> ([] as List)
        module.getId() >> DefaultModuleIdentifier.newId('com.example', 'lib')

        sel1.getAttributes() >> Mock(AttributeContainer) {
            getAttribute(attribute) >> 'true'
        }
        sel2.getAttributes() >> Mock(AttributeContainer) {
            getAttribute(attribute) >> 'false'
        }

        AttributeMergingException ex = new AttributeMergingException(attribute, 'true', 'false', 'conflict')

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
        def sel1 = Mock(org.gradle.api.artifacts.component.ModuleComponentSelector)
        def sel2 = Mock(org.gradle.api.artifacts.component.ModuleComponentSelector)

        def dm1 = Mock(DependencyMetadata)
        def dm2 = Mock(DependencyMetadata)
        dm1.getSelector() >> sel1
        dm2.getSelector() >> sel2

        def s1 = Mock(SelectorState)
        def s2 = Mock(SelectorState)
        s1.getDependencyMetadata() >> dm1
        s2.getDependencyMetadata() >> dm2
        s1.getRequested() >> sel1
        s2.getRequested() >> sel2

        def e1 = Mock(EdgeState)
        def e2 = Mock(EdgeState)
        e1.getSelector() >> s1
        e2.getSelector() >> s2
        e1.getDependencyMetadata() >> dm1
        e2.getDependencyMetadata() >> dm2

        dm1.isConstraint() >> false
        dm2.isConstraint() >> false

        e1.getFrom() >> Mock(NodeState) {
            getIncomingEdges() >> []
            getDisplayName() >> 'root'
        }
        e2.getFrom() >> Mock(NodeState) {
            getIncomingEdges() >> []
            getDisplayName() >> 'root'
        }

        def module = Mock(ModuleResolveState)
        module.getIncomingEdges() >> ([e1, e2] as Set)
        module.getUnattachedEdges() >> ([] as List)
        module.getId() >> DefaultModuleIdentifier.newId('com.example', 'lib')

        sel1.getAttributes() >> Mock(AttributeContainer) {
            getAttribute(attribute) >> { null }
        }
        sel2.getAttributes() >> Mock(AttributeContainer) {
            getAttribute(attribute) >> Boolean.TRUE
        }
        AttributeMergingException ex = new AttributeMergingException(attribute, null, Boolean.TRUE, 'conflict')

    when:
    String msg = IncompatibleDependencyAttributesMessageBuilder.buildMergeErrorMessage(module, ex)

    then:
    msg.contains('Requested values:')
        msg.contains('<no value>')
        msg.contains('true')
    }
}
