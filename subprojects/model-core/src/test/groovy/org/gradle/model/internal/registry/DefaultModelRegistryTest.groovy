/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.Transformer
import org.gradle.internal.Actions
import org.gradle.internal.BiAction
import org.gradle.internal.BiActions
import org.gradle.model.*
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf
import static org.gradle.model.internal.core.NodePredicate.allDescendants
import static org.gradle.model.internal.core.NodePredicate.allLinks
import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

class DefaultModelRegistryTest extends Specification {

    def registry = new ModelRegistryHelper()

    def "can maybe get non existing"() {
        when:
        registry.realize("foo")

        then:
        thrown IllegalStateException

        when:
        def modelElement = registry.find("foo", ModelType.untyped())

        then:
        noExceptionThrown()

        and:
        modelElement == null
    }

    def "can get element for which a registration has been registered"() {
        given:
        registry.registerInstance("foo", "value")

        expect:
        registry.realize("foo", Object) == "value"
        registry.realize("foo", String) == "value"
    }

    def "can get root node"() {
        expect:
        registry.realizeNode(ModelPath.ROOT) != null
    }

    def "cannot get element for which registration by-path input does not exist"() {
        given:
        registry.register("foo") { it.descriptor("foo creator").unmanaged(String, "other", null, Stub(Transformer)) }

        when:
        registry.realize("foo")

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains '''
  foo creator
    inputs:
      - other Object [*]
'''
    }

    def "cannot get element for which registration by-type input does not exist"() {
        given:
        registry.register("foo") { it.descriptor("foo creator").unmanaged(String, Long, Stub(Transformer)) }

        when:
        registry.realize("foo")

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains '''
  foo creator
    inputs:
      - <no path> Long [*]
'''
    }

    def "cannot register when by-type input is ambiguous"() {
        given:
        registry.registerInstance("other-1", 11)
        registry.registerInstance("other-2", 12)

        when:
        registry.register("foo") { it.descriptor("foo creator").unmanaged(String, Number, Stub(Transformer)) }
        registry.bindAllReferences()

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(e.cause.message) == """Type-only model reference of type java.lang.Number is ambiguous as multiple model elements are available for this type:
  - other-1 (created by: other-1 creator)
  - other-2 (created by: other-2 creator)"""
    }

    def "cannot register already known element"() {
        given:
        registry.register("foo") { it.descriptor("create foo as String").unmanaged("value") }

        when:
        registry.register("foo") { it.descriptor("create foo as Integer").unmanaged(12.toInteger()) }

        then:
        DuplicateModelException e = thrown()
        e.message == /Cannot create 'foo' using creation rule 'create foo as Integer' as the rule 'create foo as String' is already registered to create this model element./
    }

    def "cannot register when element already closed"() {
        given:
        registry.register("foo") { it.descriptor("create foo as String").unmanaged("value") }

        registry.realize("foo")

        when:
        registry.register("foo") { it.descriptor("create foo as Integer").unmanaged(12.toInteger()) }

        then:
        DuplicateModelException e = thrown()
        e.message == /Cannot create 'foo' using creation rule 'create foo as Integer' as the rule 'create foo as String' has already been used to create this model element./
    }

    def "cannot register when sibling with same type used as by-type input"() {
        given:
        registry.registerInstance("other-1", 12)
        registry.register("foo") { it.descriptor("foo creator").unmanaged(String, Number, Stub(Transformer)) }
        registry.registerInstance("other-2", 11)

        when:
        registry.bindAllReferences()

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        normaliseLineSeparators(e.cause.message) == """Type-only model reference of type java.lang.Number is ambiguous as multiple model elements are available for this type:
  - other-1 (created by: other-1 creator)
  - other-2 (created by: other-2 creator)"""
    }

    def "rule cannot add link when element already known"() {
        def mutatorAction = Mock(Action)

        given:
        registry.register("foo") { it.descriptor("create foo as Integer").unmanaged(12.toInteger()) }
        registry.mutate { it.path "foo" type Integer descriptor "mutate foo as Integer" node mutatorAction }
        mutatorAction.execute(_) >> { MutableModelNode node ->
            node.addLink("foo.bar") { it.descriptor("create foo.bar as String").unmanaged("12") }
            node.addLink("foo.bar") { it.descriptor("create foo.bar as Integer").unmanaged(12) }
        }

        when:
        registry.realize("foo")

        then:
        ModelRuleExecutionException e = thrown()
        e.message == /Exception thrown while executing model rule: mutate foo as Integer/
        e.cause instanceof DuplicateModelException
        e.cause.message == /Cannot create 'foo.bar' using creation rule 'create foo.bar as Integer' as the rule 'create foo.bar as String' is already registered to create this model element./
    }

    def "inputs for creation are bound when inputs already closed"() {
        def action = Mock(Transformer)

        given:
        registry.registerInstance("foo", 12.toInteger())
        registry.realize("foo")
        registry.register("bar") { it.unmanaged String, Integer, action }
        action.transform(12) >> "[12]"

        expect:
        registry.realize("bar") == "[12]"
    }

    def "inputs for creation are bound when inputs already known"() {
        def action = Mock(Transformer)

        given:
        registry.registerInstance("foo", 12.toInteger())
        registry.register("bar") { it.unmanaged String, Integer, action }
        action.transform(12) >> "[12]"

        expect:
        registry.realize("bar") == "[12]"
    }

    def "inputs for creation are bound as inputs become known"() {
        def action = Mock(Transformer)

        given:
        registry.register("bar") { it.unmanaged String, Integer, action }
        registry.registerInstance("foo", 12.toInteger())
        action.transform(12) >> "[12]"

        expect:
        registry.realize("bar") == "[12]"
    }

    def "parent of input is implicitly closed when input is not known"() {
        given:
        registry.register("bar") { it.unmanaged(String, "foo.child", { input -> "[$input]" }) }
        registry.registerInstance("foo", "foo")
        registry.mutate {
            it.path "foo" type String node {
                node -> node.addLinkInstance("foo.child", 12)
            }
        }

        expect:
        registry.realize("bar") == "[12]"
    }

    def "input path can point to a reference"() {
        given:
        registry.registerInstance("target", "value")
        def target = registry.node("target")
        registry.register("ref") { parentBuilder ->
            parentBuilder.unmanagedNode(Object) { node ->
                node.addReference("direct", String, target)
            }
        }
        registry.register("foo") { it.unmanaged(String, "ref.direct") { it } }

        expect:
        registry.realize("foo", String) == "value"
    }

    def "input path can traverse a reference"() {
        given:
        registry.register("parent") { parentBuilder ->
            parentBuilder.unmanagedNode(Object) { node ->
                node.addLinkInstance("parent.child", "value")
            }
        }

        def parent = registry.node("parent")
        registry.register("ref") { parentBuilder ->
            parentBuilder.unmanagedNode(Object) { node ->
                node.addReference("indirect", String, parent)
            }
        }
        registry.register("foo") { it.unmanaged(String, "ref.indirect.child") { it } }

        expect:
        registry.realize("foo", String) == "value"
    }

    def "child reference can be null when parent is realized"() {
        given:
        registry.register("parent") { parentBuilder ->
            parentBuilder.unmanagedNode(String) { node ->
                node.addReference("child", String, null)
            }
        }

        when:
        registry.realize("parent", String)

        then:
        noExceptionThrown()
    }

    def "reference can point to ancestor of node"() {
        given:
        registry.register("parent") { parentBuilder ->
            parentBuilder.unmanagedNode(String) { node ->
                node.addReference("child", String, node)
                node.applyToSelf(ModelActionRole.Mutate) { it.path("parent").node { it.setPrivateData(String, "value") }}
            }
        }

        expect:
        registry.realize("parent.child", String) == "value"
    }

    def "child can be made known"() {
        given:
        registry.register("parent") { parentBuilder ->
            parentBuilder.unmanagedNode(String) { node ->
                node.addLinkInstance("parent.child", "child")
            }
        }

        when:
        registry.realize("parent.child", String)

        then:
        noExceptionThrown()
    }

    def "cannot change a reference after it has been discovered"() {
        given:
        registry.registerInstance("target", "value")
        def target = registry.node("target")
        registry.root.addReference("ref", String, target)
        def ref = registry.atState("ref", ModelNode.State.Discovered)

        when:
        ref.setTarget(newTarget)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot set target for model element 'ref' as this element is not mutable."

        where:
        newTarget << [null, Stub(MutableModelNode)]
    }

    def "rules are invoked in order before element is closed"() {
        def action = Mock(Action)

        given:
        registry
            .register("foo") { it.unmanaged(new Bean(), action) }
            .configure(ModelActionRole.Defaults) { it.path("foo").type(Bean).action(action) }
            .configure(ModelActionRole.Initialize) { it.path("foo").type(Bean).action(action) }
            .configure(ModelActionRole.Mutate) { it.path("foo").type(Bean).action(action) }
            .configure(ModelActionRole.Finalize) { it.path("foo").type(Bean).action(action) }
            .configure(ModelActionRole.Validate) { it.path("foo").type(Bean).action(action) }

        when:
        def value = registry.realize("foo", Bean).value

        then:
        value == "create > defaults > initialize > mutate > finalize"

        and:
        1 * action.execute(_) >> { Bean bean ->
            assert bean.value == null
            bean.value = "create"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > defaults"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > initialize"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > mutate"
        }
        1 * action.execute(_) >> { Bean bean ->
            bean.value += " > finalize"
        }
        1 * action.execute(_) >> { Bean bean ->
            assert bean.value == "create > defaults > initialize > mutate > finalize"
        }
        0 * action._

        when:
        registry.realize("foo", Bean)

        then:
        0 * action._
    }

    def "registration for linked element invoked before element is closed"() {
        def action = Mock(Action)

        given:
        registry.registerInstance("foo", new Bean())
        registry.mutate { it.path "foo" type Bean node action }

        when:
        registry.realize("foo", Bean)

        then:
        1 * action.execute(_) >> { MutableModelNode node -> node.addLink("foo.bar") { it.unmanaged("value", action) } }
        1 * action.execute(_)
        0 * action._
    }

    def "inputs for mutator are bound when inputs already closed"() {
        def action = Mock(BiAction)

        given:
        registry.registerInstance("foo", 12.toInteger())
        registry.realize("foo")
        registry.registerInstance("bar", new Bean())
        registry.mutate { it.path("bar").type(Bean).action(Integer, action) }
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.realize("bar", Bean).value == "[12]"
    }

    def "inputs for mutator are bound when inputs already known"() {
        def action = Mock(BiAction)

        given:
        registry.registerInstance("foo", 12.toInteger())
        registry.registerInstance("bar", new Bean())
        registry.mutate { it.path("bar").type(Bean).action(Integer, action) }
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.realize("bar", Bean).value == "[12]"
    }

    def "inputs for mutator are bound as inputs become known"() {
        def action = Mock(BiAction)

        given:
        registry.registerInstance("bar", new Bean())
        registry.mutate { it.path("bar").type(Bean).action(Integer, action) }
        registry.registerInstance("foo", 12.toInteger())
        action.execute(_, 12) >> { bean, value -> bean.value = "[12]" }

        expect:
        registry.realize("bar", Bean).value == "[12]"
    }

    def "transitions elements that depend on a particular state of an element when the target element leaves target state"() {
        given:
        registry.registerInstance("a", new Bean())
        registry.registerInstance("b", new Bean())
        registry.configure(ModelActionRole.Finalize) {
            it.path("b").action(ModelReference.of(ModelPath.path("a"), ModelType.of(Bean), ModelNode.State.DefaultsApplied)) { Bean b, Bean a ->
                b.value = "$b.value $a.value"
            }
        }
        registry.configure(ModelActionRole.Mutate) {
            it.path("b").action { Bean b ->
                b.value = "b-mutate"
            }
        }
        registry.configure(ModelActionRole.Mutate) {
            it.path("a").action { Bean a ->
                a.value = "a-mutate"
            }
        }
        registry.configure(ModelActionRole.Defaults) {
            it.path("a").action { Bean a ->
                a.value = "a-defaults"
            }
        }

        expect:
        registry.realize("a", Bean).value == "a-mutate"
        registry.realize("b", Bean).value == "b-mutate a-defaults"
    }

    def "transitions input elements to target state"() {
        given:
        registry.registerInstance("a", new Bean())
        registry.registerInstance("b", new Bean())
        registry.configure(ModelActionRole.Finalize) {
            it.path("b").descriptor("b-finalize").action(ModelReference.of(ModelPath.path("a"), ModelType.of(Bean), ModelNode.State.DefaultsApplied)) { Bean b, Bean a ->
                b.value = "$b.value $a.value"
            }
        }
        registry.configure(ModelActionRole.Mutate) {
            it.path("b").descriptor("b-mutate").action { Bean b ->
                b.value = "b-mutate"
            }
        }
        registry.configure(ModelActionRole.Mutate) {
            it.path("a").descriptor("a-mutate").action { Bean a ->
                a.value = "a-mutate"
            }
        }
        registry.configure(ModelActionRole.Defaults) {
            it.path("a").descriptor("a-defaults").action { Bean a ->
                a.value = "a-defaults"
            }
        }

        expect:
        registry.realize("b", Bean).value == "b-mutate a-defaults"
        registry.realize("a", Bean).value == "a-mutate"
    }

    def "can attach a mutator with inputs to all elements linked from an element"() {
        given:
        registry.register("parent") { it.unmanagedNode Integer, { MutableModelNode node ->
            node.applyTo(allLinks(), ModelActionRole.Mutate) {
                it.type(Bean).action(String) { Bean bean, String prefix ->
                    bean.value = "$prefix: $bean.value"
                }
            }
            node.addLinkInstance("parent.foo", new Bean(value: "foo"))
            node.addLinkInstance("parent.bar", new Bean(value: "bar"))
        }
        }
        registry.registerInstance("prefix", "prefix")

        expect:
        registry.realize("parent.foo", Bean).value == "prefix: foo"
        registry.realize("parent.bar", Bean).value == "prefix: bar"
    }

    def "can attach a mutator to all elements with specific type linked from an element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(Action)

        given:
        registry.register("parent") { it.unmanagedNode Integer, creatorAction }
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyTo(allLinks(), ModelActionRole.Mutate) { it.type(Bean).action(mutatorAction) }
            node.addLinkInstance("parent.foo", "ignore me")
            node.addLinkInstance("parent.bar", new Bean(value: "bar"))
        }
        registry.registerInstance("other", new Bean(value: "ignore me"))
        mutatorAction.execute(_) >> { Bean bean -> bean.value = "prefix: $bean.value" }

        registry.realize("parent") // TODO - should not need this: parent mutations should be applied before mutating element

        expect:
        registry.realize("parent.bar", Bean).value == "prefix: bar"
        registry.realize("parent.foo", String) == "ignore me"

        and:
        registry.realize("other", Bean).value == "ignore me"
    }

    def "can attach a mutator to all elements with specific type transitively linked from an element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(Action)

        given:
        registry.register("parent") { it.unmanagedNode Integer, creatorAction }
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyTo(allDescendants(), ModelActionRole.Mutate) { it.type(Bean).action(mutatorAction) }
            node.addLinkInstance("parent.foo", "ignore me")
            node.addLinkInstance("parent.bar", new Bean(value: "bar"))
            node.applyToLink(ModelActionRole.Mutate) { it.path("parent.bar").node { MutableModelNode bar ->
                bar.addLinkInstance("parent.bar.child1", new Bean(value: "baz"))
                bar.addLinkInstance("parent.bar.child2", "ignore me too")
            }}
        }
        registry.registerInstance("other", new Bean(value: "ignore me"))
        mutatorAction.execute(_) >> { Bean bean -> bean.value = "prefix: $bean.value" }

        registry.realize("parent") // TODO - should not need this: parent mutations should be applied before mutating element

        expect:
        registry.realize("parent.bar", Bean).value == "prefix: bar"
        registry.realize("parent.foo", String) == "ignore me"
        registry.realize("parent.bar.child1", Bean).value == "prefix: baz"
        registry.realize("parent.bar.child2", String) == "ignore me too"

        and:
        registry.realize("other", Bean).value == "ignore me"
    }

    def "can attach a mutator with inputs to element linked from another element"() {
        def creatorAction = Mock(Action)
        def mutatorAction = Mock(BiAction)

        given:
        registry.register("parent") { it.unmanagedNode Integer, creatorAction }
        creatorAction.execute(_) >> { MutableModelNode node ->
            node.applyToLink(ModelActionRole.Mutate) { it.path("parent.foo").type(Bean).action(String, mutatorAction) }
            node.addLinkInstance("parent.foo", new Bean(value: "foo"))
            node.addLinkInstance("parent.bar", new Bean(value: "bar"))
        }
        mutatorAction.execute(_, _) >> { Bean bean, String prefix ->
            bean.value = "$prefix: $bean.value"
        }
        registry.registerInstance("prefix", "prefix")

        registry.realize("parent") // TODO - should not need this: parent mutations should be applied before mutating element

        expect:
        registry.realize("parent.foo", Bean).value == "prefix: foo"
        registry.realize("parent.bar", Bean).value == "bar"
    }

    def "cannot attach link when element is not mutable"() {
        def action = Stub(Action)

        given:
        registry.registerInstance("thing", "value")
        registry.configure(ModelActionRole.Validate) { it.path "thing" type Object node action }
        action.execute(_) >> { MutableModelNode node -> node.addLink("thing.child") { it.descriptor("create thing.child as String").unmanaged("value") } }

        when:
        registry.realize("thing")

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot create 'thing.child' using creation rule 'create thing.child as String' as model element 'thing' is no longer mutable."
    }

    def "cannot set value when element is not mutable"() {
        def action = Stub(Action)

        given:
        registry.registerInstance("thing", "value")
        registry.configure(ModelActionRole.Validate) { it.path("thing").type(Object).node(action) }
        action.execute(_) >> { MutableModelNode node -> node.setPrivateData(ModelType.of(String), "value 2") }

        when:
        registry.realize("thing")

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot set value for model element 'thing' as this element is not mutable."
    }

    def "can replace an element that has not been used as input by a rule"() {
        given:
        registry.registerInstance("thing", new Bean(value: "old"))
        registry.configure(ModelActionRole.Mutate) { it.path("thing").action { it.value = "${it.value} path" } }
        registry.configure(ModelActionRole.Mutate) { it.type(Bean).action { it.value = "${it.value} type" } }
        registry.realize("thing")
        registry.remove(ModelPath.path("thing"))
        registry.registerInstance("thing", new Bean(value: "new"))

        expect:
        registry.realize("thing", Bean).value == "new path type"
    }

    def "cannot remove an element that has already been used as input by a rule"() {
        given:
        def action = Mock(BiAction)
        registry.registerInstance("foo", 12.toInteger())
        registry.registerInstance("bar", new Bean())
        registry.mutate { it.path("bar").type(Bean).action(Integer, action) }
        registry.realize("bar", Bean).value == "[12]"

        when:
        registry.remove(ModelPath.path("foo"))

        then:
        def ex = thrown IllegalStateException
        ex.message == "Tried to remove model 'foo' but it is depended on by: 'bar'"
    }

    def "can remove an element with children that has not been used as input by a rule"() {
        given:
        registry.register("parent") { it.unmanagedNode (Integer) { MutableModelNode node ->
            node.addLinkInstance("parent.foo", 12.toInteger())
        }}

        registry.realize("parent")

        expect:
        registry.atStateOrLater("parent", ModelNode.State.Registered).path == ModelPath.path("parent")
        registry.atStateOrLater("parent.foo", ModelNode.State.Registered).path == ModelPath.path("parent.foo")

        registry.remove(ModelPath.path("parent"))

        when:
        registry.atStateOrLater("parent", ModelNode.State.Registered) == null
        then:
        def exParent = thrown IllegalStateException
        exParent.message == "No model node at 'parent'"

        when:
        registry.atStateOrLater("parent.foo", ModelNode.State.Registered) == null
        then:
        def exFoo = thrown IllegalStateException
        exFoo.message == "No model node at 'parent.foo'"
    }

    def "cannot remove an element whose child has already been used as input by a rule"() {
        given:
        registry.register("parent") { it.unmanagedNode (Integer) { MutableModelNode node ->
            node.addLinkInstance("parent.foo", 12.toInteger())
        }}
        registry.registerInstance("bar", new Bean())
        registry.mutate { it.path("bar").action("parent.foo", Integer, BiActions.doNothing()) }
        registry.realize("bar", Bean).value == "[12]"

        when:
        registry.remove(ModelPath.path("parent"))

        then:
        def ex = thrown IllegalStateException
        ex.message == "Tried to remove model 'parent.foo' but it is depended on by: 'bar'"
    }

    def "cannot bind action targeting type for role #targetRole where type is not available"() {
        when:
        registry.configure(targetRole, ModelReference.of("thing", Bean), Actions.doNothing())

        then:
        def ex = thrown IllegalStateException
        ex.message == "Cannot bind subject 'ModelReference{path=thing, scope=null, type=${fullyQualifiedNameOf(Bean)}, state=GraphClosed}' to role '${targetRole}' because it is targeting a type and subject types are not yet available in that role"

        where:
        targetRole << ModelActionRole.values().findAll { !it.subjectViewAvailable }
    }

    def "cannot execute action with role #targetRole where view is not available"() {
        registry.configure(targetRole, new AbstractModelActionWithView<Bean>(ModelReference.of("thing"), new SimpleModelRuleDescriptor(targetRole.name()), []) {
            @Override
            protected void execute(MutableModelNode modelNode, Bean view, List<ModelView<?>> inputs) {
            }
        })

        when:
        registry.registerInstance("thing", new Bean(value: "thing"))
        registry.atStateOrLater(ModelPath.path("thing"), targetRole.targetState)

        then:
        def ex = thrown ModelRuleExecutionException
        ex.cause instanceof IllegalStateException
        ex.cause.message == "Cannot get view for node thing in state ${targetRole.targetState.previous()}"

        where:
        targetRole << ModelActionRole.values().findAll { !it.subjectViewAvailable }
    }

    def "cannot add action for #targetRole mutation when in later #fromRole mutation"() {
        def action = Stub(Action)

        given:
        registry.registerInstance("thing", "value")
            .configure(fromRole) { it.path("thing").node(action) }
        action.execute(_) >> { MutableModelNode node -> registry
            .configure(targetRole) { it.path("thing").type(String).descriptor("X").action {} }
        }

        when:
        registry.realize("thing")

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot add rule X for model element 'thing' at state ${targetRole.targetState.previous()} as this element is already at state ${fromRole.targetState.previous()}."

        where:
        [fromRole, targetRole] << ModelActionRole.values().collectMany { fromRole ->
            return ModelActionRole.values().findAll { it.ordinal() < fromRole.ordinal() && it.subjectViewAvailable }.collect { targetRole ->
                [ fromRole, targetRole ]
            }
        }
    }

    def "cannot add action for #targetRole mutation when in later #fromState state"() {
        def action = Stub(Action)

        given:
        registry.registerInstance("thing", "value")
            .registerInstance("another", "value")
            .configure(ModelActionRole.Mutate) {
                it.path("another").node(action)
            }
        action.execute(_) >> {
            MutableModelNode node -> registry.configure(targetRole) { it.path("thing").descriptor("X").action {} }
        }

        when:
        registry.atState(ModelPath.path("thing"), fromState)
        registry.realize("another")

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == "Cannot add rule X for model element 'thing' at state ${targetRole.targetState.previous()} as this element is already at state ${fromState}."

        where:
        [fromState, targetRole] << ModelNode.State.values().collectMany { fromState ->
            return ModelActionRole.values().findAll { it.targetState.ordinal() <= fromState.ordinal() }.collect { targetRole ->
                [ fromState, targetRole ]
            }
        }
    }

    def "can add action for #targetRole when in #fromRole action"() {
        given:
        registry.configure(fromRole) {
            it.path("thing").node { MutableModelNode node ->
                registry.configure(targetRole) {
                    it.path("thing").type(Bean).action {
                        it.value = "mutated"
                    }
                }
            }
        }
        registry.registerInstance("thing", new Bean(value: "initial"))

        when:
        def thing = registry.realize("thing", Bean)

        then:
        thing.value == "mutated"

        where:
        [fromRole, targetRole] << ModelActionRole.values().collectMany { fromRole ->
            return ModelActionRole.values().findAll { it.subjectViewAvailable && it.ordinal() >= fromRole.ordinal() }.collect { targetRole ->
                return [ fromRole, targetRole ]
            }
        }
    }

    def "closes inputs for mutation discovered after running action with role #targetRole"() {
        given:
        registry.registerInstance("thing", new Bean(value: "initial"))
            .configure(targetRole) {
            it.path("thing").descriptor("outside").node { MutableModelNode node ->
                registry.configure(targetRole) {
                    it.path("thing").type(Bean).descriptor("inside").action("other", ModelType.of(Bean), action)
                }
            }
        }
        // Include a dependency
        registry.registerInstance("other", new Bean())
            .mutate { it.path("other").type(Bean).action { it.value = "input value" } }

        when:
        def thing = registry.realize("thing", Bean)

        then:
        thing.value == expected

        where:
        [targetRole, action, expected] << ModelActionRole.values().findAll { it.subjectViewAvailable }.collect { role ->
            return [role, { subject, dep -> subject.value = dep.value }, "input value"]
        }
    }

    def "can add action for #targetRole mutation when in earlier #fromState state"() {
        def action = Stub(Action)

        given:
        registry.registerInstance("thing", "value")
            .registerInstance("another", "value")
            .configure(ModelActionRole.Mutate) {
                it.path("another").node(action)
            }
        action.execute(_) >> {
            MutableModelNode node -> registry.configure(targetRole) {
                it.path("thing").descriptor("X").action {}
            }
        }

        when:
        registry.atState(ModelPath.path("thing"), fromState)
        registry.realize("another")

        then:
        noExceptionThrown()

        where:
        [fromState, targetRole] << (ModelNode.State.values() - ModelNode.State.Registered).collectMany { fromState ->
            return ModelActionRole.values().findAll { it.targetState.ordinal() > fromState.ordinal() }.collect { targetRole ->
                [ fromState, targetRole ]
            }
        }
    }

    def "can get node at state #state"() {
        given:
        ModelActionRole.values().each { role ->
            registry.configure(role, { builder ->
                builder.path "thing"
                if (role.subjectViewAvailable) {
                    builder.type Bean
                    return builder.action({
                        it.value = role.name()
                    })
                } else {
                    return builder.node(Actions.doNothing())
                }
            })
        }
        registry.registerInstance("thing", new Bean(value: "created"))

        expect:
        registry.atState(ModelPath.path("thing"), state).getPrivateData(ModelType.of(Bean))?.value == expected

        where:
        state                           | expected
        ModelNode.State.Registered      | null
        ModelNode.State.Discovered      | null
        ModelNode.State.Created         | "created"
        ModelNode.State.DefaultsApplied | ModelActionRole.Defaults.name()
        ModelNode.State.Initialized     | ModelActionRole.Initialize.name()
        ModelNode.State.Mutated         | ModelActionRole.Mutate.name()
        ModelNode.State.Finalized       | ModelActionRole.Finalize.name()
        ModelNode.State.SelfClosed      | ModelActionRole.Validate.name()
        ModelNode.State.GraphClosed     | ModelActionRole.Validate.name()
    }


    def "can get node at state Known"() {
        registry.registerInstance("thing", new Bean(value: "created"))

        expect:
        registry.atState(ModelPath.path("thing"), ModelNode.State.Registered).path.toString() == "thing"
    }

    def "asking for element at state #state does not create node"() {
        given:
        def events = []
        registry.register("thing") { it.unmanaged(new Bean()) { events << "created" } }

        when:
        registry.atState(ModelPath.path("thing"), state)

        then:
        events == []

        when:
        registry.atState(ModelPath.path("thing"), ModelNode.State.Created)

        then:
        events == ["created"]

        where:
        state << [ModelNode.State.Registered, ModelNode.State.Discovered]
    }

    def "asking for unknown element at state #state fails"() {
        when:
        registry.atState(ModelPath.path("thing"), state)

        then:
        IllegalStateException e = thrown()
        e.message == "No model node at 'thing'"

        where:
        state << ModelNode.State.values()
    }

    def "getting self closed collection defines all links but does not realise them until graph closed"() {
        given:
        def events = []
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerModelMap("things", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
            it.path "things" type mmType action { c ->
                events << "collection mutated"
                c.create("c1") { events << "$it.name created" }
            }
        }

        when:
        def cbNode = registry.atState(ModelPath.path("things"), ModelNode.State.SelfClosed)

        then:
        events == ["collection mutated"]
        cbNode.getLinkNames(ModelType.of(Bean)).toList() == ["c1"]

        when:
        registry.atState(ModelPath.path("things"), ModelNode.State.GraphClosed)

        then:
        events == ["collection mutated", "c1 created"]
    }

    def "cannot request model node at earlier state #targetState when at #fromState"() {
        given:
        registry.registerInstance("thing", new Bean())
        registry.atState(ModelPath.path("thing"), fromState)

        when:
        registry.atState(ModelPath.path("thing"), targetState)

        then:
        def e = thrown IllegalStateException
        e.message == "Cannot lifecycle model node 'thing' to state ${targetState.name()} as it is already at ${fromState.name()}"

        where:
        [fromState, targetState] << ModelNode.State.values().collectMany { fromState ->
            return ModelNode.State.values().findAll { it.ordinal() < fromState.ordinal() }.collect { targetState ->
                [ fromState, targetState ]
            }
        }
    }

    def "is benign to request element at current state #state"() {
        given:
        registry.registerInstance("thing", new Bean())

        when:
        // not in loop to get different stacktrace line numbers
        registry.atState(ModelPath.path("thing"), state)
        registry.atState(ModelPath.path("thing"), state)
        registry.atState(ModelPath.path("thing"), state)

        then:
        noExceptionThrown()

        where:
        state << ModelNode.State.values() - ModelNode.State.Registered
    }

    def "is benign to request element at prior state #state"() {
        given:
        registry.registerInstance("thing", new Bean())

        when:
        registry.atState(ModelPath.path("thing"), state)
        ModelNode.State.values().findAll { it.ordinal() <= state.ordinal() }.each {
            registry.atStateOrLater(ModelPath.path("thing"), state)
        }

        then:
        noExceptionThrown()

        where:
        state << ModelNode.State.values() - ModelNode.State.Registered
    }

    def "requesting at state #state does not reinvoke actions"() {
        given:
        def events = []
        def uptoRole = ModelActionRole.values().findAll { it.ordinal() <= role.ordinal() }
        uptoRole.each { r -> configureAction(r, "thing", Bean, { events << r.name() }) }

        registry.registerInstance("thing", new Bean())

        when:
        registry.atState(ModelPath.path("thing"), state)

        then:
        events == uptoRole*.name()

        when:
        registry.atState(ModelPath.path("thing"), state)

        then:
        events == uptoRole*.name()

        where:
        [state, role] << ModelActionRole.values().collect { role -> [role.targetState, role]}
    }

    private <T> void configureAction(ModelActionRole role, String path, def type, Action<? super MutableModelNode> nodeAction, Action<? super T> viewAction = null) {
        registry.configure(role) {
            it.path path
            if (role.subjectViewAvailable) {
                it.type type
                if (viewAction != null) {
                    it.action viewAction
                } else {
                    it.node nodeAction
                }
            } else {
                it.node nodeAction
            }
        }
    }

    def "reports unbound subjects"() {
        given:
        registry.mutate { it.path("a.b").descriptor("by-path").action() {} }
        registry.mutate { it.type(Long).descriptor("by-type").action() {} }
        registry.mutate { it.path("missing").type(String).descriptor("by-path-and-type").action() {} }

        when:
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains '''
  by-path
    subject:
      - a.b Object [*]

  by-path-and-type
    subject:
      - missing String [*]

  by-type
    subject:
      - <no path> Long [*]
'''
    }

    def "reports unbound inputs"() {
        given:
        registry.register("foo") { it.descriptor("creator").unmanaged(Long, "a.b") {} }
        registry.mutate { it.path("foo").descriptor("by-path").action(ModelPath.path("other.thing"), ModelType.of(String)) {} }
        registry.mutate { it.type(Runnable).descriptor("by-type").action(String) {} }

        when:
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains '''
  by-path
    subject:
      - foo Object
    inputs:
      - other.thing String (java.lang.String) [*]

  by-type
    subject:
      - <no path> Runnable [*]
    inputs:
      - <no path> String (java.lang.String) [*]

  creator
    inputs:
      - a.b Object (a.b) [*]
'''
    }

    def "closes elements as required to bind all subjects and inputs"() {
        given:
        registry.mutate { it.path("a.1.2").action(ModelPath.path("b.1.2"), ModelType.of(String)) {} }
        registry.register("a") { it.unmanaged("a") }
        registry.mutate {
            it.path("a").node {
                it.addLinkInstance("a.1", "a.1")
                it.applyToLink(ModelActionRole.Finalize) { it.path("a.1").node {
                    it.addLinkInstance("a.1.2", "a.1.2")
                }}
            }
        }
        registry.register("b") { it.unmanaged("b") }
        registry.mutate {
            it.path("b").node {
                it.addLinkInstance("b.1", "b.1")
                it.applyToLink(ModelActionRole.Finalize) { it.path("b.1").node {
                    it.addLinkInstance("b.1.2", "b.1.2")
                }}
            }
        }

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    def "only rules that actually have unbound inputs are reported as unbound"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerInstance("foo", new Bean())
            .mutate {
            it.descriptor("non-bindable").path("foo").type(Bean).action("emptyBeans.element", ModelType.of(Bean), null, {})
        }
        .mutate {
            it.descriptor("bindable").path("foo").type(Bean).action("beans.element", ModelType.of(Bean)) {
            }
        }
        .registerModelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
            it.path "beans" type mmType action { c ->
                c.create("element")
            }
        }
        .registerModelMap("emptyBeans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }

        when:
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains '''
  non-bindable
    subject:
      - foo DefaultModelRegistryTest.Bean
    inputs:
      - emptyBeans.element DefaultModelRegistryTest.Bean [*]
'''
    }

    def "does not report unbound actions applied at registration as unbound after the nodes is removed"() {
        given:
        def registration = ModelRegistrations.of(ModelPath.path("unused")).descriptor("unused")
        ModelActionRole.values().each { role ->
            registration.action(role, [ModelReference.of("unknown")], BiActions.doNothing())
        }
        registry.register(registration.build())
        registry.remove(ModelPath.path("unused"))

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    def "two element mutation rule based configuration cycles are detected"() {
        given:
        registry.registerInstance("foo", "foo")
            .registerInstance("bar", "bar")
            .mutate { it.path("foo").descriptor("foo mutator").type(String).action("bar", ModelType.of(String), "parameter 1", {}) }
            .mutate { it.path("bar").descriptor("bar mutator").type(String).action("foo", ModelType.of(String), null, {}) }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo mutator
   \\- bar
      \\- bar mutator
         \\- foo""")
    }

    def "multiple element configuration cycles are detected"() {
        registry.register("foo") { it.unmanaged(String, "bar") { "foo" } }
            .register("bar") { it.unmanaged(String, "fizz") { "bar" } }
            .registerInstance("fizz", "fizz")
            .mutate { it.path("fizz").descriptor("fizz mutator").type(String).action("buzz", ModelType.of(String), {}) }
            .registerInstance("buzz", "buzz")
            .mutate { it.path("buzz").descriptor("buzz mutator").type(String).action("foo", ModelType.of(String), {}) }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo creator
   \\- bar
      \\- bar creator
         \\- fizz
            \\- fizz mutator
               \\- buzz
                  \\- buzz mutator
                     \\- foo""")
    }

    def "one element configuration cycles are detected"() {
        given:
        registry.registerInstance("foo", "foo")
            .mutate { it.path("foo").descriptor("foo mutator").type(String).action(String) {} }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo mutator
   \\- foo""")
    }

    def "only the elements actually forming the cycle are reported when configuration cycles are detected"() {
        given:
        registry.register("foo") { it.unmanaged(Long, "bar") { 12 } }
            .register("bar") { it.unmanaged(String, "fizz") { "bar" } }
            .mutate { it.path("foo").action(String) {} }
            .register("fizz") { it.unmanaged(Boolean, "buzz") { "buzz" } }
            .mutate { it.path("fizz").descriptor("fizz mutator").action("bar", ModelType.of(String), {}) }
            .registerInstance("buzz", Long)

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
bar
\\- bar creator
   \\- fizz
      \\- fizz mutator
         \\- bar""")
    }

    def "implicit cycle when node depends on parent is detected"() {
        given:
        registry.registerInstance("foo", "foo")
            .mutate { it.path("foo").descriptor("foo mutator").node { it.addLink("foo.bar") { it.unmanaged(Number, 12) } } }
            .mutate { it.path("foo.bar").descriptor("bar mutator").action(String) {} }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo.bar
   \\- bar mutator
      \\- foo""")
    }

    def "implicit cycle when node depends on ancestor is detected"() {
        given:
        registry.registerInstance("foo", "foo")
            .mutate { it.path("foo").descriptor("foo mutator").node { it.addLink("foo.bar") { it.unmanaged(Number, 12) } } }
            .mutate { it.path("foo.bar").descriptor("bar mutator").node { it.addLink("foo.bar.baz") { it.unmanaged(Number, 107) } } }
            .mutate { it.path("foo.bar.baz").descriptor("baz mutator").action(String) {} }

        when:
        registry.get("foo")

        then:
        ConfigurationCycleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""A cycle has been detected in model rule dependencies. References forming the cycle:
foo
\\- foo.bar
   \\- foo.bar.baz
      \\- baz mutator
         \\- foo""")
    }

    def "node can be viewed via projection registered via projector"() {
        registry.configure(ModelActionRole.Discover) { it.path "foo" descriptor "project" node { node ->
            node.addProjection UnmanagedModelProjection.of(BeanInternal)
        } }
        registry
            .register("foo") { it.unmanaged(Bean, new AdvancedBean(name: "foo")) }
            .mutate (BeanInternal) { bean ->
                bean.internal = "internal"
            }

        expect:
        def bean = registry.realize("foo")
        assert bean instanceof AdvancedBean
        bean.internal == "internal"
    }

    def "can register projection after node is registered"() {
        registry
            .register("foo") { it.unmanaged(Bean, new AdvancedBean(name: "foo")) }
            .mutate (BeanInternal) { bean ->
            bean.internal = "internal"
        }
        registry.configure(ModelActionRole.Discover) { it.path "foo" descriptor "project" node { node ->
            node.addProjection UnmanagedModelProjection.of(BeanInternal)
        } }

        expect:
        def bean = registry.realize("foo")
        assert bean instanceof AdvancedBean
        bean.internal == "internal"
    }

    def "cannot register projection after node has been discovered"() {
        given:
        registry.register("foo") { it.unmanaged(Bean, new AdvancedBean(name: "foo")) }
        registry.atState("foo", ModelNode.State.Discovered)

        when:
        registry.configure(ModelActionRole.Discover) { it.path "foo" descriptor "project" node {} }

        then:
        def ex = thrown IllegalStateException
        ex.message == "Cannot add rule project for model element 'foo' at state ${ModelNode.State.Registered} as this element is already at state ${ModelNode.State.Discovered}."
    }

    def "discover children of scope when defining scope when node matching input type is not already discovered"() {
        registry.register("dep") { it.unmanaged(Bean, new Bean()) }
        registry.register("target") { it.unmanaged(String) }
        registry.register("childA") { it.unmanaged(String) }
        registry.register("childB") { it.unmanaged(String) }
        registry.configure(ModelActionRole.Mutate) { it.path("target").action(Bean, BiActions.doNothing())}

        when:
        registry.realize("target")

        then:
        registry.state("dep") == ModelNode.State.GraphClosed
        registry.state("target") == ModelNode.State.GraphClosed
        registry.state("childA") == ModelNode.State.Discovered
        registry.state("childB") == ModelNode.State.Discovered
    }

    def "does not discover children of scope when node matching input type is already in discovered"() {
        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of("dep", Bean), new Bean()).descriptor("dep").build())
        registry.atState("dep", ModelNode.State.Discovered)
        registry.register("target") { it.unmanaged(String) }
        registry.register("childA") { it.unmanaged(String) }
        registry.register("childB") { it.unmanaged(String) }
        registry.configure(ModelActionRole.Mutate) { it.path("target").action(Bean, BiActions.doNothing()) }

        when:
        registry.realize("target")

        then:
        registry.state("dep") == ModelNode.State.GraphClosed
        registry.state("target") == ModelNode.State.GraphClosed
        registry.state("childA") == ModelNode.State.Registered
        registry.state("childB") == ModelNode.State.Registered
    }

    def "fails when another child in scope with matching bound rule's target type is discovered"() {
        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of("dep", Bean), new Bean()).descriptor("dep creator").build())
        registry.atState("dep", ModelNode.State.Discovered)
        registry.register("target") { it.unmanaged(String) }
        registry.register("childA") { it.unmanaged(String) }
        registry.register("childB") { it.unmanaged(String) }
        registry.configure(ModelActionRole.Mutate) { it.path("target").action(Bean, BiActions.doNothing()) }

        when:
        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of("dep2", Bean), new Bean()).descriptor("dep2 creator").build())

        then:
        noExceptionThrown()

        when:
        registry.bindAllReferences()

        then:
        def ex = thrown InvalidModelRuleException
        ex.cause instanceof ModelRuleBindingException
        ex.cause.message == TextUtil.toPlatformLineSeparators("""Type-only model reference of type ${fullyQualifiedNameOf(Bean)} (${fullyQualifiedNameOf(Bean)}) is ambiguous as multiple model elements are available for this type:
  - dep (created by: dep creator)
  - dep2 (created by: dep2 creator)""")
    }

    def "can apply RuleSource to node"() {
        when:
        registry.registerInstance("bean", new Bean())
        registry.configure(ModelActionRole.Create) { it.path("bean").node { node -> node.applyToSelf(BeanRules) } }

        then:
        def bean = registry.realize("bean", Bean)
        bean.name == 'bean'
        bean.value == '12'
    }

    def "can apply RuleSource to node via reference"() {
        when:
        registry.registerInstance("target", new Bean())
        registry.root.addReference("bean", Bean, registry.root.getLink("target"))
        registry.configure(ModelActionRole.Defaults) { it.path("bean").node { node -> node.applyToSelf(BeanRules) } }

        // TODO - should not need this: target of the reference should be transitioned
        registry.realize("target", Bean)

        then:
        def bean = registry.realize("bean", Bean)
        bean.name == 'bean'
        bean.value == '12'
    }

    def "can apply #description to each element matching type in root scope"() {
        def mmType = ModelTypes.modelMap(Bean)

        registry.registerInstance("foo", "foo")
        registry.registerInstance("bean1", new Bean(name: "bean1 unmodified"))
        registry.registerModelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it + " unmodified") } }
        registry.mutate {
            it.path "beans" type mmType action { beans ->
                beans.create("bean2")
            }
        }
        registry.root.applyToSelf(rules)

        expect:
        registry.realize("bean1", Bean).name == "bean"
        registry.realize("beans.bean2", Bean).name == "bean"

        where:
        rules                 | description
        EachBeanViaDirectRule | "direct rule"
        EachBeanViaRuleSource | "rule source"
    }

    def "#description is not applied to descendants accessible only via references"() {
        def mmType = ModelTypes.modelMap(Bean)

        registry.registerInstance("bean1", new Bean(name: "bean1 unmodified"))
        registry.registerInstance("beans", new Bean(name: "beans"))
        registry.registerModelMap("otherBeans", Bean) { it.registerFactory(Bean) { new Bean(name: it + " unmodified") } }
        registry.mutate {
            it.path "otherBeans" type mmType action { beans ->
                beans.create("bean3")
            }
        }

        registry.mutate {
            it.path "beans" node {
                it.addLinkInstance("beans.bean2", new Bean(name: "bean2 unmodified"))
                it.addReference("beanRef", Bean, registry.root.getLink("bean1"))
                it.addReference("otherBeansRef", Bean, registry.root.getLink("otherBeans"))
            }
        }

        def scope = registry.root.getLink("beans")
        scope.applyToSelf(rules)

        expect:
        // Rule gets applied to node in scope
        registry.realize("beans.bean2", Bean).name == "bean"

        // Rule doesn't get applied to node outside scope
        registry.realize("bean1", Bean).name == "bean1 unmodified"

        // Rule is not applied to referenced node
        registry.realize("beans.beanRef", Bean).name == "bean1 unmodified"

        // Rule is not applied to descendant node of referenced node (via "beans.otherBeans")
        registry.realize("otherBeans.bean3", Bean).name == "bean3 unmodified"

        where:
        rules                 | description
        EachBeanViaDirectRule | "direct rule"
        EachBeanViaRuleSource | "rule source"
    }

    def "#description is not applied to scope element"() {
        registry.registerInstance("bean1", new Bean(name: "bean1 unmodified"))
        registry.mutate {
            it.path "bean1" node {
                it.addLinkInstance("bean1.bean2", new Bean(name: "bean2 unmodified"))
            }
        }
        registry.root.getLink("bean1").applyToSelf(rules)

        expect:
        // Rule is not applied to scope node
        registry.realize("bean1", Bean).name == "bean1 unmodified"
        // Rule is applied to child of scope node
        registry.realize("bean1.bean2", Bean).name == "bean"

        where:
        rules                 | description
        EachBeanViaDirectRule | "direct rule"
        EachBeanViaRuleSource | "rule source"
    }

    static class Bean {
        String name
        String value
    }

    static interface BeanInternal {
        String getInternal()
        void setInternal(String internal)
    }

    static class AdvancedBean extends Bean implements BeanInternal {
        String internal
    }
}

