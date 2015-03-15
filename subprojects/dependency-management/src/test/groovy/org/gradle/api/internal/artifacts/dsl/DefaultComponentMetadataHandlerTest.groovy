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
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.RuleActionAdapter
import org.gradle.internal.rules.RuleActionValidationException
import org.gradle.internal.typeconversion.NotationParser
import spock.lang.Specification

import javax.xml.namespace.QName

class DefaultComponentMetadataHandlerTest extends Specification {
    private static final String GROUP = "group"
    private static final String MODULE = "module"

    // For testing ModuleMetadataProcessor capabilities
    def handler = new DefaultComponentMetadataHandler(DirectInstantiator.INSTANCE)

    // For testing ComponentMetadataHandler capabilities
    RuleActionAdapter<ComponentMetadataDetails> adapter = Mock(RuleActionAdapter)
    NotationParser<Object, String> notationParser = Mock(NotationParser)
    def mockedHandler = new DefaultComponentMetadataHandler(DirectInstantiator.INSTANCE, adapter, notationParser)
    def ruleAction = Stub(RuleAction)

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
        mockedHandler.rules.size() == 1
        mockedHandler.rules[0].action == (ruleAction)
        mockedHandler.rules[0].spec == Specs.satisfyAll()
    }

    def "add closure rule that applies to all components" () {
        def closure = { ComponentMetadataDetails cmd -> }

        when:
        mockedHandler.all closure

        then:
        1 * adapter.createFromClosure(ComponentMetadataDetails, closure) >> ruleAction

        and:
        mockedHandler.rules.size() == 1
        mockedHandler.rules[0].action == (ruleAction)
        mockedHandler.rules[0].spec == Specs.satisfyAll()
    }

    def "add rule source rule that applies to all components" () {
        def ruleSource = new Object()

        when:
        mockedHandler.all(ruleSource)

        then:
        1 * adapter.createFromRuleSource(ComponentMetadataDetails, ruleSource) >> ruleAction

        and:
        mockedHandler.rules.size() == 1
        mockedHandler.rules[0].action == (ruleAction)
        mockedHandler.rules[0].spec == Specs.satisfyAll()
    }

    def "add action rule that applies to module" () {
        def action = new Action<ComponentMetadataDetails>() {
            @Override
            void execute(ComponentMetadataDetails componentMetadataDetails) { }
        }
        def notation = "${GROUP}:${MODULE}"

        when:
        mockedHandler.withModule(notation, action)

        then:
        1 * adapter.createFromAction(action) >> ruleAction
        1 * notationParser.parseNotation(notation) >> DefaultModuleIdentifier.newId(GROUP, MODULE)

        and:
        mockedHandler.rules.size() == 1
        mockedHandler.rules[0].action == (ruleAction)
        mockedHandler.rules[0].spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
    }

    def "add closure rule that applies to module" () {
        def closure = { ComponentMetadataDetails cmd -> }
        def notation = "${GROUP}:${MODULE}"

        when:
        mockedHandler.withModule(notation, closure)

        then:
        1 * adapter.createFromClosure(ComponentMetadataDetails, closure) >> ruleAction
        1 * notationParser.parseNotation(notation) >> DefaultModuleIdentifier.newId(GROUP, MODULE)

        and:
        mockedHandler.rules.size() == 1
        mockedHandler.rules[0].action == (ruleAction)
        mockedHandler.rules[0].spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
    }

    def "add rule source rule that applies to module" () {
        def ruleSource = new Object()
        def notation = "${GROUP}:${MODULE}"

        when:
        mockedHandler.withModule(notation, ruleSource)

        then:
        1 * adapter.createFromRuleSource(ComponentMetadataDetails, ruleSource) >> ruleAction
        1 * notationParser.parseNotation(notation) >> DefaultModuleIdentifier.newId(GROUP, MODULE)

        and:
        mockedHandler.rules.size() == 1
        mockedHandler.rules[0].action == (ruleAction)
        mockedHandler.rules[0].spec.target == DefaultModuleIdentifier.newId(GROUP, MODULE)
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

    def "processing fails when status is not present in status scheme"() {
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getComponentId() >> DefaultModuleComponentIdentifier.newId("group", "module", "version")
            getStatus() >> "green"
            getStatusScheme() >> ["alpha", "beta"]
        }

        when:
        handler.processMetadata(metadata)

        then:
        ModuleVersionResolveException e = thrown()
        e.message == /Unexpected status 'green' specified for group:module:version. Expected one of: [alpha, beta]/
    }

    def "produces sensible error when rule action throws an exception" () {
        def failure = new Exception("from test")
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
        }

        when:
        handler.all { throw failure }

        and:
        handler.processMetadata(metadata)

        then:
        InvalidUserCodeException e = thrown()
        e.message == "There was an error while evaluating a component metadata rule for group:module:version."
        e.cause == failure
    }

    def "all rules get evaluated" () {
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }
        def closuresCalled = []

        when:
        handler.all { ComponentMetadataDetails cmd -> closuresCalled << 1 }
        handler.all { ComponentMetadataDetails cmd -> closuresCalled << 2 }
        handler.all { ComponentMetadataDetails cmd, IvyModuleDescriptor imd -> closuresCalled << 3 }

        and:
        handler.processMetadata(metadata)

        then:
        closuresCalled.sort() == [ 1, 2, 3 ]
    }

    def "supports rule with typed ComponentMetaDataDetails parameter"() {
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }
        def capturedDetails = null
        handler.all { ComponentMetadataDetails details ->
            capturedDetails = details
        }

        when:
        handler.processMetadata(metadata)

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
        def id1 = new NamespaceId('namespace', 'info1')
        def id2 = new NamespaceId('namespace', 'info2')
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
            getExtraInfo() >> [(id1): "info1 value", (id2): "info2 value"]
            getBranch() >> "someBranch"
        }
        def capturedDescriptor = null
        handler.all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            capturedDescriptor = descriptor
        }

        when:
        handler.processMetadata(metadata)

        then:
        noExceptionThrown()
        capturedDescriptor instanceof IvyModuleDescriptor
        with(capturedDescriptor) {
            extraInfo.asMap() == [(new QName(id1.namespace, id1.name)): "info1 value", (new QName(id2.namespace, id2.name)): "info2 value"]
            branch == "someBranch"
            ivyStatus == "integration"
        }
    }

    def "rule with IvyModuleDescriptor parameter does not get invoked for non-Ivy components"() {
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }

        def invoked = false
        handler.all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            invoked = true
        }

        when:
        handler.processMetadata(metadata)

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
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }

        when:
        handler.all { ComponentMetadataDetails details, String str -> }

        then:
        InvalidUserCodeException e = thrown()
        e.message == "The closure provided is not valid as a rule for 'ComponentMetadataHandler'."
        e.cause instanceof RuleActionValidationException
        e.cause.message == "Rule may not have an input parameter of type: java.lang.String. Second parameter must be of type: org.gradle.api.artifacts.ivy.IvyModuleDescriptor."
    }

    def "supports rule with multiple inputs in arbitrary order"() {
        def id1 = new NamespaceId('namespace', 'info1')
        def id2 = new NamespaceId('namespace', 'info2')
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
            getExtraInfo() >> [(id1): "info1 value", (id2): "info2 value"]
        }

        def capturedDetails1 = null
        def capturedDescriptor1 = null
        def capturedDescriptor2 = null

        handler.all { ComponentMetadataDetails details1, IvyModuleDescriptor descriptor1, IvyModuleDescriptor descriptor2  ->
            capturedDetails1 = details1
            capturedDescriptor1 = descriptor1
            capturedDescriptor2 = descriptor2
        }

        when:
        handler.processMetadata(metadata)

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

    interface TestIvyMetaData extends IvyModuleResolveMetaData, MutableModuleComponentResolveMetaData {}
}
