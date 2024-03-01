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


package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultComponentSelectionRules
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.rules.ClosureBackedRuleAction
import org.gradle.internal.rules.NoInputsRuleAction
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

class ComponentSelectionRulesProcessorTest extends Specification {
    def processor = new ComponentSelectionRulesProcessor()
    def rules = []
    ComponentSelectionInternal componentSelection
    def metadataProvider = Mock(MetadataProvider)

    def setup() {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        componentSelection = new DefaultComponentSelection(componentIdentifier, metadataProvider)
    }

    def "all non-rejecting rules are evaluated"() {
        metadataProvider.isUsable() >> true
        metadataProvider.getComponentMetadata() >> Mock(ComponentMetadata)
        metadataProvider.getIvyModuleDescriptor() >> Mock(IvyModuleDescriptor)

        def closureCalled = []
        when:
        rule { ComponentSelection cs -> closureCalled << 0 }
        rule { ComponentSelection cs ->
            assert cs.metadata != null
            closureCalled << 1
        }
        rule { ComponentSelection cs ->
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 2
        }
        rule { ComponentSelection cs ->
            assert cs.metadata != null
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 3
        }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules are called in order
        closureCalled == 0..3
    }

    def "all non-rejecting targeted rules are evaluated"() {
        metadataProvider.isUsable() >> true
        metadataProvider.getComponentMetadata() >> Mock(ComponentMetadata)
        metadataProvider.getIvyModuleDescriptor() >> Mock(IvyModuleDescriptor)

        def closureCalled = []
        when:
        targetedRule("group", "module") { ComponentSelection cs -> closureCalled << 0 }
        targetedRule("group", "module") { ComponentSelection cs ->
            assert cs.metadata != null
            closureCalled << 1
        }
        targetedRule("group", "module") { ComponentSelection cs ->
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 2
        }
        targetedRule("group", "module") { ComponentSelection cs ->
            assert cs.metadata != null
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 3
        }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules are called in order
        closureCalled == 0..3
    }

    def "can call both targeted and untargeted rules"() {
        metadataProvider.isUsable() >> true
        metadataProvider.getComponentMetadata() >> Mock(ComponentMetadata)
        metadataProvider.getIvyModuleDescriptor() >> Mock(IvyModuleDescriptor)

        def closureCalled = []
        when:
        rule { ComponentSelection cs -> closureCalled << 0 }
        rule { ComponentSelection cs ->
            assert cs.metadata != null
            closureCalled << 1
        }
        rule { ComponentSelection cs ->
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 2
        }
        rule { ComponentSelection cs ->
            assert cs.metadata != null
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 3
        }
        targetedRule("group", "module") { ComponentSelection cs -> closureCalled << 4 }
        targetedRule("group", "module") { ComponentSelection cs ->
            assert cs.metadata != null
            closureCalled << 5
        }
        targetedRule("group", "module") { ComponentSelection cs ->
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 6
        }
        targetedRule("group", "module") { ComponentSelection cs ->
            assert cs.metadata != null
            assert cs.getDescriptor(IvyModuleDescriptor) != null
            closureCalled << 7
        }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules are called in order
        closureCalled == 0..7
    }

    // Short circuiting tests will need to be removed once the extra param feature is removed
    def "short-circuit prefers non-metadata rules over rules requiring metadata"() {
        def metadataProvider = Mock(MetadataProvider)
        def closuresCalled = []

        when:
        rule { ComponentSelection cs -> closuresCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 2 }
        rule { ComponentSelection cs, ModuleComponentResolveMetadata mvm -> closuresCalled << 3 }
        rule { ComponentSelection cs -> closuresCalled << 4 }
        rule { ComponentSelection cs ->
            closuresCalled << 5
            cs.reject("rejected")
        }

        and:
        apply(metadataProvider)

        then:
        componentSelection.rejected
        componentSelection.rejectionReason == "rejected"

        and:
        // None of the metadata rules get fired because a non-metadata rule rejected first
        closuresCalled.intersect(1..3) == []
        closuresCalled.contains(5)

        and:
        0 * metadataProvider._
    }

    // Short circuiting tests will need to be removed once the extra param feature is removed
    def "short-circuit prefers non-metadata rules over rules requiring metadata for targeted rules"() {
        def metadataProvider = Mock(DefaultMetadataProvider)
        def closuresCalled = []

        when:
        targetedRule("group", "module") { ComponentSelection cs -> closuresCalled << 0 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 2 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetadata mvm -> closuresCalled << 3 }
        targetedRule("group", "module") { ComponentSelection cs -> closuresCalled << 4 }
        targetedRule("group", "module") { ComponentSelection cs ->
            closuresCalled << 5
            cs.reject("rejected")
        }

        and:
        apply(metadataProvider)

        then:
        componentSelection.rejected
        componentSelection.rejectionReason == "rejected"

        and:
        // None of the metadata rules get fired because a non-metadata rule rejected first
        closuresCalled.intersect(1..3) == []
        closuresCalled.contains(5)

        and:
        0 * metadataProvider._
    }

    def "metadata is not requested for rules that don't require it"() {
        def closuresCalled = []

        when:
        rule { ComponentSelection cs -> closuresCalled << 0 }
        targetedRule("group", "module") { ComponentSelection cs -> closuresCalled << 1 }

        apply(metadataProvider)

        then:
        !componentSelection.rejected
        closuresCalled.sort() == [0, 1]

        and:
        0 * metadataProvider._
    }

    def "metadata is not requested for non-targeted components"() {
        def closuresCalled = []

        when:
        targetedRule("group", "module1") { ComponentSelection cs ->
            cs.getDescriptor(IvyModuleDescriptor)
            closuresCalled << 0
        }
        targetedRule("group1", "module") { ComponentSelection cs ->
            cs.metadata
            closuresCalled << 1
        }
        targetedRule("group1", "module") { ComponentSelection cs ->
            cs.metadata
            cs.getDescriptor(IvyModuleDescriptor)
            closuresCalled << 2 }

        apply(metadataProvider)

        then:
        !componentSelection.rejected
        closuresCalled == []

        and:
        0 * metadataProvider._
    }

    def "produces sensible error when rule action throws exception"() {
        def failure = new Exception("From test")

        when:
        rule { ComponentSelection selection -> throw failure }
        apply(metadataProvider)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "There was an error while evaluating a component selection rule for group:module:version."
        e.cause == failure
    }

    // Conditional rules execution tests will need to be removed once feature is removed
    def "rule expecting IvyModuleDescriptor does not get called when not an ivy component"() {
        def metadataProvider = Stub(DefaultMetadataProvider) {
            resolve() >> true
            getIvyModuleDescriptor() >> null
        }
        def closuresCalled = []

        when:
        rule { ComponentSelection cs, IvyModuleDescriptor ivm, ComponentMetadata cm -> closuresCalled << 0 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor ivm, ComponentMetadata cm -> closuresCalled << 1 }
        apply(metadataProvider)

        then:
        closuresCalled == []
    }

    def "rule accessing IvyModuleDescriptor receives null when called on a component not ivy "() {
        def metadataProvider = Stub(DefaultMetadataProvider) {
            resolve() >> true
            getIvyModuleDescriptor() >> null
        }
        def closuresCalled = []

        when:
        rule { ComponentSelection cs ->
            if (cs.getDescriptor(IvyModuleDescriptor) != null) {
                closuresCalled << 0
            }
        }
        targetedRule("group", "module") { ComponentSelection cs ->
            if (cs.getDescriptor(IvyModuleDescriptor) != null) {
                closuresCalled << 1
            }
        }
        apply(metadataProvider)

        then:
        closuresCalled == []
    }

    def "only matching targeted rules get called"() {
        def metadataProvider = Mock(MetadataProvider) {
            isUsable() >> true
            getComponentMetadata() >> Mock(ComponentMetadata)
            getIvyModuleDescriptor() >> Mock(IvyModuleDescriptor)

        }
        def closuresCalled = []
        when:
        targetedRule("group", "module") { ComponentSelection cs -> closuresCalled << 0 }
        targetedRule("group1", "module") { ComponentSelection cs -> closuresCalled << 1 }
        targetedRule("group", "module1") { ComponentSelection cs -> closuresCalled << 2 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        closuresCalled == [0]
    }

    // Conditional rules execution tests will need to be removed once feature is removed
    def "does not invoke rules that require meta-data when it cannot be resolved"() {
        def metadataProvider = Stub(DefaultMetadataProvider) {
            resolve() >> false
        }
        def closuresCalled = []
        when:
        rule { ComponentSelection cs -> closuresCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 2 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        closuresCalled == [0]
    }

    def "rules that access meta-data when it cannot be resolved receive a null value"() {
        def metadataProvider = Stub(DefaultMetadataProvider) {
            resolve() >> false
        }
        def closuresCalled = []
        when:
        rule { ComponentSelection cs -> closuresCalled << 0 }
        rule { ComponentSelection cs ->
            if (cs.metadata != null) {
                closuresCalled << 1
            }
        }
        rule { ComponentSelection cs ->
            if (cs.getDescriptor(IvyModuleDescriptor) != null) {
                closuresCalled << 2
            }
        }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        closuresCalled == [0]
    }

    def "does invoke Action based rules even when metadata cannot be resolved"() {
        given:
        metadataProvider.usable >> false
        def closuresCalled = []
        rule(new Action<ComponentSelection>() {
            @Override
            void execute(ComponentSelection componentSelection) {
                closuresCalled << 1
            }
        })

        when:
        apply(metadataProvider)

        then:
        closuresCalled == [1]
    }

    def "rule can have access to component metadata attributes"() {
        def id = Mock(ModuleVersionIdentifier)
        def testAttr = Attribute.of('test', String)
        metadataProvider.isUsable() >> true
        metadataProvider.getComponentMetadata() >> Mock(ComponentMetadata) {
            getId() >> id
            getAttributes() >> {
                AttributeTestUtil.attributesFactory().mutable().attribute(testAttr, attributeValue)
            }
        }

        def matches = []
        when:
        rule { ComponentSelection cs ->
            if (cs.metadata.attributes.getAttribute(testAttr) == 'ok') {
                matches << cs.metadata.id
            }
        }

        and:
        apply(metadataProvider)

        then:
        matches == (match ? [id] : [])

        where:
        attributeValue << ['ok', 'ko']
        match << [true, false]
    }

    def rule(Action<ComponentSelection> action) {
        rules << new SpecRuleAction<ComponentSelection>(
            new NoInputsRuleAction<ComponentSelection>(action),
            Specs.<ComponentSelection> satisfyAll()
        )
    }

    def rule(Closure<?> closure) {
        rules << new SpecRuleAction<ComponentSelection>(
            new ClosureBackedRuleAction<ComponentSelection>(ComponentSelection, closure),
            Specs.<ComponentSelection> satisfyAll()
        )
    }

    def targetedRule(String group, String module, Closure<?> closure) {
        rules << new SpecRuleAction<ComponentSelection>(
            new ClosureBackedRuleAction<ComponentSelection>(ComponentSelection, closure),
            new DefaultComponentSelectionRules.ComponentSelectionMatchingSpec(DefaultModuleIdentifier.newId(group, module))
        )
    }

    def apply(def metadataProvider) {
        processor.apply(componentSelection, rules, metadataProvider)
    }
}
