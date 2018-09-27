/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.changedetection.state.ValueSnapshotter
import org.gradle.api.specs.Specs
import org.gradle.cache.CacheRepository
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ivy.DefaultMutableIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.DefaultMutableMavenModuleResolveMetadata
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.RuleActionAdapter
import org.gradle.internal.rules.RuleActionValidationException
import org.gradle.internal.serialize.Serializer
import org.gradle.util.BuildCommencedTimeProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.SimpleMapInterner
import spock.lang.Specification

import javax.xml.namespace.QName

class DefaultComponentMetadataHandlerTest extends Specification {
    private static final String GROUP = "group"
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()

    // For testing ModuleMetadataProcessor capabilities
    private static final String MODULE = "module"

    // For testing ComponentMetadataHandler capabilities
    def executor = new ComponentMetadataRuleExecutor(Stub(CacheRepository), Stub(InMemoryCacheDecoratorFactory), Stub(ValueSnapshotter), new BuildCommencedTimeProvider(), Stub(Serializer))
    CachePolicy cachePolicy = Mock()
    def stringInterner = SimpleMapInterner.notThreadSafe()
    def handler = new DefaultComponentMetadataHandler(DirectInstantiator.INSTANCE, moduleIdentifierFactory, stringInterner, TestUtil.attributesFactory(), TestUtil.valueSnapshotter(), executor)
    RuleActionAdapter adapter = Mock(RuleActionAdapter)
    def mockedHandler = new DefaultComponentMetadataHandler(DirectInstantiator.INSTANCE, adapter, moduleIdentifierFactory, stringInterner, TestUtil.attributesFactory(), TestUtil.valueSnapshotter(), executor)
    def ruleAction = Stub(RuleAction)
    def mavenMetadataFactory = new MavenMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), TestUtil.attributesFactory(), TestUtil.objectInstantiator(), TestUtil.featurePreviews())
    def ivyMetadataFactory = new IvyMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), TestUtil.attributesFactory())
    MetadataResolutionContext context = Mock()

    def 'setup'() {
        TestComponentMetadataRule.instanceCount = 0
    }

    def "add action rule that applies to all components" () {
        def action = new Action<ComponentMetadataDetails>() {
            @Override
            void execute(ComponentMetadataDetails componentMetadataDetails) { }
        }

        when:
        mockedHandler.all action

        then:
        1 * adapter.createFromAction(action) >> ruleAction

        and:
        !mockedHandler.metadataRuleContainer.isEmpty()
        def ruleWrapper = mockedHandler.metadataRuleContainer.iterator().next()
        ruleWrapper.rule.action == (ruleAction)
        ruleWrapper.rule.spec == Specs.satisfyAll()
    }

    def "add closure rule that applies to all components" () {
        def closure = { ComponentMetadataDetails cmd -> }

        when:
        mockedHandler.all closure

        then:
        1 * adapter.createFromClosure(ComponentMetadataDetails, closure) >> ruleAction

        and:
        !mockedHandler.metadataRuleContainer.isEmpty()
        def ruleWrapper = mockedHandler.metadataRuleContainer.iterator().next()
        !ruleWrapper.classBased
        ruleWrapper.rule.action == (ruleAction)
        ruleWrapper.rule.spec == Specs.satisfyAll()
    }

    def "add rule source rule that applies to all components" () {
        def ruleSource = new Object()

        when:
        mockedHandler.all(ruleSource)

        then:
        1 * adapter.createFromRuleSource(ComponentMetadataDetails, ruleSource) >> ruleAction

        and:
        !mockedHandler.metadataRuleContainer.isEmpty()
        def ruleWrapper = mockedHandler.metadataRuleContainer.iterator().next()
        !ruleWrapper.classBased
        ruleWrapper.rule.action == (ruleAction)
        ruleWrapper.rule.spec == Specs.satisfyAll()
    }

    def "add class rule that applies to all components"() {
        when:
        handler.all(TestComponentMetadataRule)

        then:
        !handler.metadataRuleContainer.isEmpty()
        def ruleWrapper = handler.metadataRuleContainer.iterator().next()
        ruleWrapper.classBased
        def classRule = ruleWrapper.classRules.iterator().next()
        classRule.configurableRule instanceof ConfigurableRule
        classRule.spec == Specs.satisfyAll()
        TestComponentMetadataRule.instanceCount == 0
    }

    def "add class rule with parameters that applies to all components"() {
        when:
        handler.all(TestComponentMetadataRuleWithArgs, {
                it.params("foo")
                it.params(42L)
            } as Action<ActionConfiguration>)

        then:
        !handler.metadataRuleContainer.isEmpty()
        def ruleWrapper = handler.metadataRuleContainer.iterator().next()
        ruleWrapper.classBased
        def classRule = ruleWrapper.classRules.iterator().next()
        classRule.configurableRule instanceof ConfigurableRule
        classRule.spec == Specs.satisfyAll()
        TestComponentMetadataRule.instanceCount == 0
    }

    def "add action rule that applies to module" () {
        def action = new Action<ComponentMetadataDetails>() {
            @Override
            void execute(ComponentMetadataDetails componentMetadataDetails) { }
        }
        String notation = "${GROUP}:${MODULE}"

        when:
        mockedHandler.withModule(notation, action)

        then:
        1 * adapter.createFromAction(action) >> ruleAction

        and:
        !mockedHandler.metadataRuleContainer.isEmpty()
        def ruleWrapper = mockedHandler.metadataRuleContainer.iterator().next()
        !ruleWrapper.classBased
        ruleWrapper.rule.action == (ruleAction)
        ruleWrapper.rule.spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
    }

    def "add closure rule that applies to module" () {
        def closure = { ComponentMetadataDetails cmd -> }
        String notation = "${GROUP}:${MODULE}"

        when:
        mockedHandler.withModule(notation, closure)

        then:
        1 * adapter.createFromClosure(ComponentMetadataDetails, closure) >> ruleAction

        and:
        !mockedHandler.metadataRuleContainer.isEmpty()
        def ruleWrapper = mockedHandler.metadataRuleContainer.iterator().next()
        !ruleWrapper.classBased
        ruleWrapper.rule.action == (ruleAction)
        ruleWrapper.rule.spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
    }

    def "add rule source rule that applies to module" () {
        def ruleSource = new Object()
        String notation = "${GROUP}:${MODULE}"

        when:
        mockedHandler.withModule(notation, ruleSource)

        then:
        1 * adapter.createFromRuleSource(ComponentMetadataDetails, ruleSource) >> ruleAction

        and:
        !mockedHandler.metadataRuleContainer.isEmpty()
        def ruleWrapper = mockedHandler.metadataRuleContainer.iterator().next()
        !ruleWrapper.classBased
        ruleWrapper.rule.action == (ruleAction)
        ruleWrapper.rule.spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
    }

    def "add class rule that applies to module"() {
        String notation = "${GROUP}:${MODULE}"

        when:
        handler.withModule(notation, TestComponentMetadataRule)

        then:
        !handler.metadataRuleContainer.isEmpty()
        def ruleWrapper = handler.metadataRuleContainer.iterator().next()
        ruleWrapper.classBased
        def classRule = ruleWrapper.classRules.iterator().next()
        classRule.configurableRule instanceof ConfigurableRule
        classRule.spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
        TestComponentMetadataRule.instanceCount == 0
    }

    def "add class rule with params that applies to module"() {
        String notation = "${GROUP}:${MODULE}"

        when:
        handler.withModule(notation, TestComponentMetadataRuleWithArgs,  {
            it.params("foo")
            it.params(42L)
        } as Action<ActionConfiguration>)

        then:
        !handler.metadataRuleContainer.isEmpty()
        def ruleWrapper = handler.metadataRuleContainer.iterator().next()
        ruleWrapper.classBased
        def classRule = ruleWrapper.classRules.iterator().next()
        classRule.configurableRule instanceof ConfigurableRule
        classRule.spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
        TestComponentMetadataRule.instanceCount == 0
    }

    def "propagates error creating rule for closure" () {
        when:
        mockedHandler.all { }

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "bad closure"

        and:
        1 * adapter.createFromClosure(ComponentMetadataDetails, _) >> { throw new InvalidUserCodeException("bad closure") }
    }

    def "propagates error creating rule for action" () {
        def action = Mock(Action)

        when:
        mockedHandler.all action

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "bad action"

        and:
        1 * adapter.createFromAction(action) >> { throw new InvalidUserCodeException("bad action") }
    }

    def "propagates error creating rule for rule source" () {
        when:
        mockedHandler.all(new Object())

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "bad rule source"

        and:
        1 * adapter.createFromRuleSource(ComponentMetadataDetails, _) >> { throw new InvalidUserCodeException("bad rule source") }
    }

    def "produces sensible error when rule action throws an exception" () {
        def failure = new Exception("from test")
        def metadata = ivyMetadata()

        when:
        handler.all { throw failure }

        and:
        handler.createComponentMetadataProcessor(context).processMetadata(metadata.asImmutable())

        then:
        InvalidUserCodeException e = thrown()
        e.message == "There was an error while evaluating a component metadata rule for group:module:version."
        e.cause == failure
    }

    def "all rules get evaluated" () {
        def metadata = ivyMetadata()
        def closuresCalled = []

        when:
        handler.all { ComponentMetadataDetails cmd -> closuresCalled << 1 }
        handler.all { ComponentMetadataDetails cmd -> closuresCalled << 2 }
        handler.all { ComponentMetadataDetails cmd, IvyModuleDescriptor imd -> closuresCalled << 3 }

        and:
        handler.createComponentMetadataProcessor(context).processMetadata(metadata.asImmutable())

        then:
        closuresCalled.sort() == [ 1, 2, 3 ]
    }

    def "supports rule with typed ComponentMetaDataDetails parameter"() {
        def metadata = ivyMetadata()
        def capturedDetails = null
        handler.all { ComponentMetadataDetails details ->
            capturedDetails = details
        }

        when:
        handler.createComponentMetadataProcessor(context).processMetadata(metadata.asImmutable())

        then:
        noExceptionThrown()
        capturedDetails instanceof ComponentMetadataDetails
        with(capturedDetails) {
            id.group == "group"
            id.name == "module"
            id.version == "version"
            status == "integration"
            statusScheme == ["integration", "release"]
        }
    }

    def "supports rule with typed IvyModuleDescriptor parameter"() {
        def metadata = ivyMetadata()
        def id1 = new NamespaceId('namespace', 'info1')
        def id2 = new NamespaceId('namespace', 'info2')
        def extraAttrs = [:]
        extraAttrs[id1] = "info1 value"
        extraAttrs[id2] = "info2 value"
        metadata.extraAttributes = extraAttrs
        metadata.branch = "someBranch"

        def capturedDescriptor = null
        handler.all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            capturedDescriptor = descriptor
        }

        when:
        handler.createComponentMetadataProcessor(context).processMetadata(metadata.asImmutable())

        then:
        noExceptionThrown()
        capturedDescriptor instanceof IvyModuleDescriptor
        with(capturedDescriptor) {
            extraInfo.asMap() == [(new QName(id1.namespace, id1.name)): "info1 value", (new QName(id2.namespace, id2.name)): "info2 value"]
            branch == "someBranch"
            ivyStatus == "integration"
        }
    }

    def "rule with IvyModuleDescriptor parameter sees original status"() {
        def metadata = ivyMetadata()
        def id1 = new NamespaceId('namespace', 'info1')
        def id2 = new NamespaceId('namespace', 'info2')
        def extraAttrs = [:]
        extraAttrs[id1] = "info1 value"
        extraAttrs[id2] = "info2 value"
        metadata.extraAttributes = extraAttrs
        metadata.branch = "someBranch"

        def capturedDescriptor = null
        handler.all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            capturedDescriptor = descriptor
            assert descriptor.ivyStatus == "integration"
            details.status = "release"
            assert descriptor.ivyStatus == "integration"
        }

        when:
        handler.createComponentMetadataProcessor(context).processMetadata(metadata.asImmutable())

        then:
        noExceptionThrown()
        capturedDescriptor instanceof IvyModuleDescriptor
    }

    def "rule with IvyModuleDescriptor parameter does not get invoked for non-Ivy components"() {
        def metadata = mavenMetadata()

        def invoked = false
        handler.all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            invoked = true
        }

        when:
        handler.createComponentMetadataProcessor(context).processMetadata(metadata.asImmutable())

        then:
        !invoked
    }

    def "complains if first parameter type isn't assignment compatible with ComponentMetadataDetails"() {
        when:
        handler.all { String s -> }

        then:
        InvalidUserCodeException e = thrown()
        e.message == "The closure provided is not valid as a rule for 'ComponentMetadataHandler'."
        e.cause instanceof RuleActionValidationException
        e.cause.message == "First parameter of rule action closure must be of type 'ComponentMetadataDetails'."
    }

    def "complains if rule has unsupported parameter type"() {
        when:
        handler.all { ComponentMetadataDetails details, String str -> }

        then:
        InvalidUserCodeException e = thrown()
        e.message == "The closure provided is not valid as a rule for 'ComponentMetadataHandler'."
        e.cause instanceof RuleActionValidationException
        e.cause.message == "Rule may not have an input parameter of type: java.lang.String. Second parameter must be of type: org.gradle.api.artifacts.ivy.IvyModuleDescriptor."
    }

    def "supports rule with multiple inputs in arbitrary order"() {
        def metadata = ivyMetadata()
        def id1 = new NamespaceId('namespace', 'info1')
        def id2 = new NamespaceId('namespace', 'info2')
        def extraAttrs = [:]
        extraAttrs[id1] = "info1 value"
        extraAttrs[id2] = "info2 value"
        metadata.extraAttributes = extraAttrs
        metadata.branch = "someBranch"

        def capturedDetails1 = null
        def capturedDescriptor1 = null
        def capturedDescriptor2 = null

        handler.all { ComponentMetadataDetails details1, IvyModuleDescriptor descriptor1, IvyModuleDescriptor descriptor2  ->
            capturedDetails1 = details1
            capturedDescriptor1 = descriptor1
            capturedDescriptor2 = descriptor2
        }

        when:
        handler.createComponentMetadataProcessor(context).processMetadata(metadata.asImmutable())

        then:
        noExceptionThrown()
        capturedDetails1 instanceof ComponentMetadataDetails
        with(capturedDetails1) {
            id.group == "group"
            id.name == "module"
            id.version == "version"
            status == "integration"
            statusScheme == ["integration", "release"]
        }
        capturedDescriptor1 instanceof IvyModuleDescriptor
        with(capturedDescriptor1) {
            extraInfo.asMap() == [(new QName(id1.namespace, id1.name)): "info1 value", (new QName(id2.namespace, id2.name)): "info2 value"]
        }
        capturedDescriptor2 instanceof IvyModuleDescriptor
        with(capturedDescriptor2) {
            extraInfo.asMap() == [(new QName(id1.namespace, id1.name)): "info1 value", (new QName(id2.namespace, id2.name)): "info2 value"]
        }
    }

    def "ComponentMetadataDetailsSpec matches on group and name" () {
        def spec = new DefaultComponentMetadataHandler.ComponentMetadataDetailsMatchingSpec(DefaultModuleIdentifier.newId(group, name))
        def id = Mock(ModuleVersionIdentifier) {
            1 * getGroup() >> { "org.gradle" }
            (0..1) * getName() >> { "api" }
        }
        def details = Stub(ComponentMetadataDetails) {
            getId() >> id
        }

        expect:
        spec.isSatisfiedBy(details) == matches

        where:
        group        | name  | matches
        "org.gradle" | "api" | true
        "com.gradle" | "api" | false
        "org.gradle" | "lib" | false
    }

    def 'allows to mix old style and class based rules starting with class based'() {
        def closure = { ComponentMetadataDetails cmd -> }

        when:
        handler.all(TestComponentMetadataRule)
        handler.all closure
        handler.all closure
        handler.all(TestComponentMetadataRule)

        then:
        noExceptionThrown()

    }

    def 'allows to mix old style and class based rules starting with old style'() {
        def closure = { ComponentMetadataDetails cmd -> }

        when:
        handler.all closure
        handler.all(TestComponentMetadataRule)
        handler.all closure
        handler.all(TestComponentMetadataRule)

        then:
        noExceptionThrown()

    }

    private DefaultMutableIvyModuleResolveMetadata ivyMetadata() {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def metadata = ivyMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"))
        metadata.status = "integration"
        metadata.statusScheme = ["integration", "release"]
        return metadata
    }

    private DefaultMutableMavenModuleResolveMetadata mavenMetadata() {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def metadata = mavenMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"))
        metadata.status = "integration"
        metadata.statusScheme = ["integration", "release"]
        return metadata
    }
}
