/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.api.Action
import org.gradle.model.InvalidModelRuleException
import org.gradle.model.ModelRuleBindingException
import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.type.ModelType
import org.gradle.util.TextUtil

class RuleBindingsTest extends RegistrySpec {
    final ModelGraph graph = new ModelGraph(node(""))
    final RuleBindings bindings = new RuleBindings(graph)

    def "locates the subject of a rule by-path"() {
        given:
        def node = node("a")
        def rule = rule("a", ModelNode.State.Mutated)
        addNode(node)
        bindings.add(rule)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-path when subject added after rule"() {
        given:
        def node = node("a")
        def rule = rule("a", ModelNode.State.Mutated)
        bindings.add(rule)
        addNode(node)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-type"() {
        given:
        def node = node("a", Long)
        def rule = rule(Long, ModelNode.State.Mutated)
        addNode(node)
        bindings.add(rule)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-type when subject added after rule"() {
        given:
        def node = node("a", Long)
        def rule = rule(Long, ModelNode.State.Mutated)
        bindings.add(rule)
        addNode(node)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates dependents of a node by-path"() {
        given:
        def node = node("a")
        def rule = rule("other") { it.inputReference("a", ModelNode.State.Created) }
        bindings.add(rule)
        addNode(node)

        expect:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Created)) as List == [rule]
        rule.inputBindings[0].boundTo == node
    }

    def "locates dependents of a node by-type"() {
        given:
        def node = node("a", Long)
        def rule = rule("other") { it.inputReference(Long, ModelNode.State.Created) }
        bindings.add(rule)
        addNode(node)

        expect:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Created)) as List == [rule]
        rule.inputBindings[0].boundTo == node
    }

    def "returns empty list when no rules with matching subject"() {
        given:
        bindings.add(rule("other", ModelNode.State.Created))
        bindings.add(rule(Long, ModelNode.State.Created))
        bindings.add(rule("path", ModelNode.State.Mutated))
        bindings.add(rule(String, ModelNode.State.Mutated))
        addNode(node("path", String))

        expect:
        bindings.getRulesWithSubject(nodeAtState("path", ModelNode.State.Created)) as List == []
    }

    def "returns empty list when no rules with matching input"() {
        given:
        bindings.add(rule("other") { it.inputReference("other", ModelNode.State.Created) })
        bindings.add(rule("other") { it.inputReference(Long, ModelNode.State.Created) })
        bindings.add(rule("other") { it.inputReference(String, ModelNode.State.GraphClosed) })
        bindings.add(rule("other") { it.inputReference("path", ModelNode.State.GraphClosed) })
        addNode(node("path", String))

        expect:
        bindings.getRulesWithInput(nodeAtState("path", ModelNode.State.Created)) as List == []
    }

    def "returns rules with subject in fixed order - by-path in order added followed by by-type in order added"() {
        def rule1 = rule("path", ModelNode.State.Finalized)
        def rule2 = rule(Long, ModelNode.State.Finalized)
        def rule3 = rule("path", ModelNode.State.Finalized)
        def rule4 = rule(Long, ModelNode.State.Finalized)
        def rule5 = rule(Long, ModelNode.State.Finalized)
        def rule6 = rule("path", ModelNode.State.Finalized)

        given:
        bindings.add(rule1)
        bindings.add(rule2)
        bindings.add(rule3)
        bindings.add(rule4)
        bindings.add(rule5)
        bindings.add(rule6)
        addNode(node("path", Long))

        expect:
        bindings.getRulesWithSubject(nodeAtState("path", ModelNode.State.Finalized)) as List == [rule1, rule3, rule6, rule2, rule4, rule5]
    }

    def "returns rules with input in fixed order"() {
        def rule1 = rule("other") { it.inputReference("path", ModelNode.State.Finalized) }
        def rule2 = rule("other") { it.inputReference(Long, ModelNode.State.Finalized) }
        def rule3 = rule("other") { it.inputReference("path", ModelNode.State.Finalized) }
        def rule4 = rule("other") { it.inputReference(Long, ModelNode.State.Finalized) }
        def rule5 = rule("other") { it.inputReference(Long, ModelNode.State.Finalized) }
        def rule6 = rule("other") { it.inputReference("path", ModelNode.State.Finalized) }

        given:
        bindings.add(rule1)
        bindings.add(rule2)
        bindings.add(rule3)
        bindings.add(rule4)
        bindings.add(rule5)
        bindings.add(rule6)
        addNode(node("path", Long))

        expect:
        bindings.getRulesWithInput(nodeAtState("path", ModelNode.State.Finalized)) as List == [rule1, rule3, rule6, rule2, rule4, rule5]
    }

    def "includes rule once only when multiple inputs with same matching node"() {
        def rule1 = rule("other") {
            it.inputReference("path", ModelNode.State.Finalized)
            it.inputReference(Long, ModelNode.State.Finalized)
            it.inputReference("path", ModelNode.State.Finalized)
        }
        def rule2 = rule("other") {
            it.inputReference(Long, ModelNode.State.Finalized)
            it.inputReference("path", ModelNode.State.Finalized)
        }

        given:
        bindings.add(rule1)
        addNode(node("path", Long))
        bindings.add(rule2)

        expect:
        bindings.getRulesWithInput(nodeAtState("path", ModelNode.State.Finalized)) as List == [rule1, rule2]
        rule1.inputBindings.every { it.bound }
        rule2.inputBindings.every { it.bound }
    }

    def "can replace by-path subject"() {
        def node1 = node("a")
        def node2 = node("a")
        def rule = rule("a", ModelNode.State.Finalized)

        given:
        bindings.add(rule)

        when:
        addNode(node1)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.subjectBinding.boundTo == node1

        when:
        removeNode(node1)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == []
        !rule.subjectBinding.bound

        when:
        addNode(node2)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.subjectBinding.boundTo == node2
    }

    def "can replace by-type subject"() {
        def node1 = node("a", Long)
        def node2 = node("a", Long)
        def rule = rule(Long, ModelNode.State.Finalized)

        given:
        bindings.add(rule)

        when:
        addNode(node1)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.subjectBinding.boundTo == node1

        when:
        removeNode(node1)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == []
        !rule.subjectBinding.bound

        when:
        addNode(node2)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.subjectBinding.boundTo == node2
    }

    def "can replace by-path input"() {
        def node1 = node("a")
        def node2 = node("a")
        def rule = rule("other") { it.inputReference("a", ModelNode.State.Finalized) }

        given:
        bindings.add(rule)

        when:
        addNode(node1)

        then:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.inputBindings[0].boundTo == node1

        when:
        removeNode(node1)

        then:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Finalized)) as List == []
        !rule.subjectBinding.bound

        when:
        addNode(node2)

        then:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.inputBindings[0].boundTo == node2
    }

    def "can replace by-type input"() {
        def node1 = node("a", Long)
        def node2 = node("a", Long)
        def rule = rule("other") { it.inputReference(Long, ModelNode.State.Finalized) }

        given:
        bindings.add(rule)

        when:
        addNode(node1)

        then:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.inputBindings[0].boundTo == node1

        when:
        removeNode(node1)

        then:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Finalized)) as List == []
        !rule.subjectBinding.bound

        when:
        addNode(node2)

        then:
        bindings.getRulesWithInput(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.inputBindings[0].boundTo == node2
    }

    def "cannot add node that would make a by-type subject ambiguous"() {
        given:
        bindings.add(rule(Long))
        addNode(node("a", Long))

        when:
        addNode(node("b", Long))

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        TextUtil.normaliseLineSeparators(e.cause.message) == '''Type-only model reference of type java.lang.Long is ambiguous as multiple model elements are available for this type:
  - a (created by: test)
  - b (created by: test)'''
    }

    def "cannot add node that would make a by-type input ambiguous"() {
        given:
        bindings.add(rule("other") { it.inputReference(Long) })
        addNode(node("a", Long))

        when:
        addNode(node("b", Long))

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        TextUtil.normaliseLineSeparators(e.cause.message) == '''Type-only model reference of type java.lang.Long is ambiguous as multiple model elements are available for this type:
  - a (created by: test)
  - b (created by: test)'''
    }

    def "cannot add rule with ambiguous by-type subject"() {
        given:
        addNode(node("a", Long))
        addNode(node("b", Long))

        when:
        bindings.add(rule(Long))

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        TextUtil.normaliseLineSeparators(e.cause.message) == '''Type-only model reference of type java.lang.Long is ambiguous as multiple model elements are available for this type:
  - a (created by: test)
  - b (created by: test)'''
    }

    def "cannot add rule with ambiguous by-type input"() {
        given:
        addNode(node("a", Long))
        addNode(node("b", Long))

        when:
        bindings.add(rule("other") { it.inputReference(Long) })

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        TextUtil.normaliseLineSeparators(e.cause.message) == '''Type-only model reference of type java.lang.Long is ambiguous as multiple model elements are available for this type:
  - a (created by: test)
  - b (created by: test)'''
    }

    def "cannot add rule when subject state is not earlier than rule target state"() {
        given:
        def node = node("a", Long)
        def rule = rule(Long, requiredState) { it.descriptor("<rule>") }
        addNode(node)
        node.state = currentState

        when:
        bindings.add(rule)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot add rule <rule> for model element 'a' at state ${requiredState.previous()} as this element is already at state $currentState."

        where:
        currentState                | requiredState
        ModelNode.State.Initialized | ModelNode.State.Initialized
        ModelNode.State.GraphClosed | ModelNode.State.Initialized
    }

    def "cannot add rule when input state is not at or earlier than input source state"() {
        given:
        def node = node("a", Long)
        def rule = rule("other") {
            it.descriptor("<rule>")
            it.inputReference(Long, requiredState)
        }

        addNode(node)
        node.state = currentState

        when:
        bindings.add(rule)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot add rule <rule> with input model element 'a' at state $requiredState as this element is already at state $currentState."

        where:
        currentState                | requiredState
        ModelNode.State.Initialized | ModelNode.State.Initialized.previous()
        ModelNode.State.Initialized | ModelNode.State.Created
    }

    NodeAtState nodeAtState(String path, ModelNode.State state) {
        return new NodeAtState(ModelPath.path(path), state)
    }

    void addNode(TestNode node) {
        graph.get(node.path.parent).addLink(node)
        graph.add(node)
        bindings.add(node)
    }

    void removeNode(TestNode node) {
        graph.remove(node)
        bindings.remove(node)
    }

    TestNode node(String path, Class type = Object) {
        return new TestNode(path, type)
    }

    RuleBinder rule(Class subjectType, ModelNode.State targetState) {
        def builder = new RuleBinderTestBuilder()
        builder.subjectReference(ModelReference.of(null, ModelType.of(subjectType), targetState).inScope(ModelPath.ROOT))
        builder.descriptor("rule with subject of type $subjectType.simpleName")
        return builder.build()
    }

    RuleBinder rule(Class subjectType, ModelNode.State targetState, Action<? super RuleBinderTestBuilder> action) {
        def builder = new RuleBinderTestBuilder()
        builder.subjectReference(ModelReference.of(null, ModelType.of(subjectType), targetState).inScope(ModelPath.ROOT))
        builder.descriptor("rule with subject of type $subjectType.simpleName")
        action.execute(builder)
        return builder.build()
    }

    RuleBinder rule(Class subjectType) {
        def builder = new RuleBinderTestBuilder()
        builder.subjectReference(ModelReference.of(null, ModelType.of(subjectType), ModelNode.State.Mutated).inScope(ModelPath.ROOT))
        builder.descriptor("rule with subject of type $subjectType.simpleName")
        return builder.build()
    }

    RuleBinder rule(String subjectPath) {
        return rule(subjectPath, ModelNode.State.Mutated)
    }

    RuleBinder rule(String subjectPath, ModelNode.State targetState) {
        def builder = new RuleBinderTestBuilder()
        builder.subjectReference(ModelReference.of(ModelPath.path(subjectPath), ModelType.untyped(), targetState))
        builder.descriptor("rule with subject $subjectPath")
        return builder.build()
    }

    RuleBinder rule(String subjectPath, Action<? super RuleBinderTestBuilder> action) {
        def builder = new RuleBinderTestBuilder()
        builder.subjectReference(ModelReference.of(ModelPath.path(subjectPath), ModelType.untyped(), ModelNode.State.Mutated))
        builder.descriptor("rule with subject $subjectPath")
        action.execute(builder)
        return builder.build()
    }
}
