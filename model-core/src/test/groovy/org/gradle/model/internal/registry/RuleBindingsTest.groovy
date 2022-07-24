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
import org.gradle.model.internal.core.ModelProjection
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.type.ModelType
import org.gradle.util.internal.TextUtil

class RuleBindingsTest extends RegistrySpec {
    final RuleBindings bindings = new RuleBindings()

    def "locates the subject of a rule by-path when rule added after node type information is available"() {
        when:
        def node = node("a")
        def rule = rule("a", ModelNode.State.Mutated)
        addNode(node)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)).empty

        when:
        bindings.add(rule)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-path when rule added before node type information is available"() {
        when:
        def node = node("a")
        def rule = rule("a", ModelNode.State.Mutated)
        addUntypedNode(node)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)).empty

        when:
        bindings.add(rule)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node

        when:
        addProjections(node)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-path when subject added after rule"() {
        when:
        def node = node("a")
        def rule = rule("a", ModelNode.State.Mutated)
        bindings.add(rule)

        then:
        !rule.subjectBinding.bound

        when:
        addUntypedNode(node)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node

        when:
        addProjections(node)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-path and by-type when rule added after type information available"() {
        given:
        def node = node("a", Long)
        def rule = rule("a", Number, ModelNode.State.Mutated)
        addNode(node)
        bindings.add(rule)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-path and by-type when rule added before type information is available"() {
        when:
        def node = node("a", Long)
        def rule = rule("a", Number, ModelNode.State.Mutated)
        addUntypedNode(node)
        bindings.add(rule)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == []
        !rule.subjectBinding.bound

        when:
        addProjections(node)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
        rule.subjectBinding.boundTo == node
    }

    def "locates the subject of a rule by-path and by-type when subject added after rule"() {
        when:
        def node = node("a", Long)
        def rule = rule("a", Number, ModelNode.State.Mutated)
        bindings.add(rule)
        addUntypedNode(node)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == []
        !rule.subjectBinding.bound

        when:
        addProjections(node)

        then:
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

    def "locates the subject of a rule by-type when rule added before type information available"() {
        given:
        def node = node("a", Long)
        def rule = rule(Long, ModelNode.State.Mutated)
        addUntypedNode(node)

        when:
        bindings.add(rule)

        then:
        !rule.bound
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == []

        when:
        addProjections(node)

        then:
        rule.bound

        expect:
        rule.subjectBinding.boundTo == node
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
    }

    def "locates the subject of a rule by-type when rule is defined later"() {
        given:
        def node = node("a", Long)
        def rule = rule(Long, ModelNode.State.Mutated)

        when:
        bindings.add(rule)

        then:
        !rule.bound
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == []

        when:
        addUntypedNode(node)

        then:
        !rule.bound
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == []

        when:
        addProjections(node)

        then:
        rule.subjectBinding.boundTo == node
        rule.bound
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule]
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

    def "matches rules on path"() {
        given:
        def node1 = node("a")
        def node2 = node("b")
        def rule1 = rule("a", ModelNode.State.Mutated)
        def rule2 = rule("b", ModelNode.State.Mutated)
        bindings.add(rule1)
        addNode(node1)
        addNode(node2)
        bindings.add(rule2)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule1]
        bindings.getRulesWithSubject(nodeAtState("b", ModelNode.State.Mutated)) as List == [rule2]
    }

    def "matches rules on target state"() {
        given:
        def node = node("a", Long)
        def rule1 = rule(Long, ModelNode.State.Mutated)
        def rule2 = rule(Number, ModelNode.State.DefaultsApplied)
        def rule3 = rule(Long, ModelNode.State.Finalized)
        bindings.add(rule1)
        bindings.add(rule2)
        addNode(node)
        bindings.add(rule3)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Created)).empty
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule1]
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.DefaultsApplied)) as List == [rule2]
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule3]
    }

    def "matches rules on scope and type"() {
        given:
        def node1 = node("a", Long)
        def node2 = node("a.1", Integer)
        def node3 = node("a.2", String)
        def node4 = node("b", Long)
        def rule1 = rule(Long, ModelNode.State.Mutated, ModelPath.path("a"))
        def rule2 = rule(String, ModelNode.State.Mutated, ModelPath.path("a"))
        def rule3 = rule(Integer, ModelNode.State.Mutated, ModelPath.path("a"))
        bindings.add(rule1)
        bindings.add(rule2)
        addNode(node1)
        addNode(node2)
        addNode(node3)
        addNode(node4)
        bindings.add(rule3)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule1]
        bindings.getRulesWithSubject(nodeAtState("a.1", ModelNode.State.Mutated)) as List == [rule3]
        bindings.getRulesWithSubject(nodeAtState("a.2", ModelNode.State.Mutated)) as List == [rule2]
        bindings.getRulesWithSubject(nodeAtState("b", ModelNode.State.Mutated)).empty
    }

    def "binds multiple by-path rules to subject"() {
        given:
        def node = node("a")
        def rule1 = rule("a", ModelNode.State.Mutated)
        def rule2 = rule("a", ModelNode.State.Mutated)
        def rule3 = rule("a", ModelNode.State.Mutated)
        bindings.add(rule1)
        bindings.add(rule2)
        addNode(node)
        bindings.add(rule3)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule1, rule2, rule3]
    }

    def "binds multiple by-path and by-type rules to subject"() {
        given:
        def node = node("a", Long)
        def rule1 = rule("a", Long, ModelNode.State.Mutated)
        def rule2 = rule("a", Number, ModelNode.State.Mutated)
        def rule3 = rule("a", Object, ModelNode.State.Mutated)
        bindings.add(rule1)
        bindings.add(rule2)
        addNode(node)
        bindings.add(rule3)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule1, rule2, rule3]
    }

    def "binds multiple by-type rules to subject"() {
        given:
        def node = node("a", Long)
        def rule1 = rule(Long, ModelNode.State.Mutated)
        def rule2 = rule(Number, ModelNode.State.Mutated)
        def rule3 = rule(Long, ModelNode.State.Mutated)
        bindings.add(rule1)
        bindings.add(rule2)
        addNode(node)
        bindings.add(rule3)

        expect:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Mutated)) as List == [rule1, rule2, rule3]
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

    def "can replace by-path subject when bound"() {
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
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)).empty
        !rule.subjectBinding.bound

        when:
        addNode(node2)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.subjectBinding.boundTo == node2
    }

    def "can replace by-path subject when not bound"() {
        def node1 = node("a")
        def node2 = node("a")
        def rule = rule("a", ModelNode.State.Finalized)

        given:
        addNode(node1)
        removeNode(node1)
        bindings.add(rule)

        when:
        addNode(node2)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule]
        rule.subjectBinding.boundTo == node2
    }

    def "can replace by-type subject when bound"() {
        def node1 = node("a", Long)
        def node2 = node("a", Long)
        def rule1 = rule(Long, ModelNode.State.Finalized)
        def rule2 = rule(Long, ModelNode.State.Finalized)

        when:
        bindings.add(rule1)
        addNode(node1)
        bindings.add(rule2)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule1, rule2]
        rule1.subjectBinding.boundTo == node1
        rule2.subjectBinding.boundTo == node1

        when:
        removeNode(node1)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)).empty
        !rule1.subjectBinding.bound
        !rule2.subjectBinding.bound

        when:
        addNode(node2)

        then:
        bindings.getRulesWithSubject(nodeAtState("a", ModelNode.State.Finalized)) as List == [rule1, rule2]
        rule1.subjectBinding.boundTo == node2
        rule2.subjectBinding.boundTo == node2
    }

    def "can replace by-type subject when not bound"() {
        def node1 = node("a", Long)
        def node2 = node("a", Long)
        def rule = rule(Long, ModelNode.State.Finalized)

        given:
        addNode(node1)
        removeNode(node1)
        bindings.add(rule)

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

    def "cannot add child node that would make a by-type subject ambiguous"() {
        given:
        addNode(node("a", Long))
        bindings.add(rule(Long, ModelNode.State.Mutated, ModelPath.path("a")))

        when:
        addNode(node("a.2", Long))

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        TextUtil.normaliseLineSeparators(e.cause.message) == '''Type-only model reference of type java.lang.Long is ambiguous as multiple model elements are available for this type:
  - a (created by: test)
  - a.2 (created by: test)'''
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

    void addNode(TestNode node, ModelProjection... projections) {
        addUntypedNode(node)
        addProjections(node, projections)
    }

    private void addUntypedNode(TestNode node) {
        bindings.nodeCreated(node)
    }

    private void addProjections(TestNode node, ModelProjection... projections) {
        projections.each { node.addProjection it }
        node.setState(ModelNode.State.Discovered)
        bindings.nodeDiscovered(node)
    }

    void removeNode(TestNode node) {
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

    RuleBinder rule(Class subjectType, ModelNode.State targetState, ModelPath scope) {
        def builder = new RuleBinderTestBuilder()
        builder.subjectReference(ModelReference.of(null, ModelType.of(subjectType), targetState).inScope(scope))
        builder.descriptor("rule with subject of type $subjectType.simpleName in $scope")
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

    RuleBinder rule(String subjectPath, Class subjectType, ModelNode.State targetState) {
        def builder = new RuleBinderTestBuilder()
        builder.subjectReference(ModelReference.of(ModelPath.path(subjectPath), ModelType.of(subjectType), targetState))
        builder.descriptor("rule with subject $subjectPath of type ${subjectType.simpleName}")
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
