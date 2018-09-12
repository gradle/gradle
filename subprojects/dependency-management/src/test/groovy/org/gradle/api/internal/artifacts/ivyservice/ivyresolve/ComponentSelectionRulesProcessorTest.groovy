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
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

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
        rule { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 3 }
        rule { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 4 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules without metadata should be executed first
        closureCalled[0] == 0
        // metadata rules get called second in indeterminate order
        closureCalled[1..-1].sort() == 1..4
    }

    def "all non-rejecting targeted rules are evaluated"() {
        metadataProvider.isUsable() >> true
        metadataProvider.getComponentMetadata() >> Mock(ComponentMetadata)
        metadataProvider.getIvyModuleDescriptor() >> Mock(IvyModuleDescriptor)

        def closureCalled = []
        when:
        targetedRule("group", "module") { ComponentSelection cs -> closureCalled << 0 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 3 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 4 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules without metadata should be executed first
        closureCalled[0] == 0
        // metadata rules get called second in indeterminate order
        closureCalled[1..-1].sort() == 1..4
    }

    def "can call both targeted and untargeted rules"() {
        metadataProvider.isUsable() >> true
        metadataProvider.getComponentMetadata() >> Mock(ComponentMetadata)
        metadataProvider.getIvyModuleDescriptor() >> Mock(IvyModuleDescriptor)

        def closureCalled = []
        when:
        rule { ComponentSelection cs -> closureCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 3 }
        rule { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 4 }
        targetedRule("group", "module") { ComponentSelection cs -> closureCalled << 5 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 6 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 7 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 8 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 9 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules without metadata should be executed first in indeterminate order
        closureCalled[0..1].sort() == [0, 5]
        // metadata rules get called second in indeterminate order
        closureCalled[2..-1].sort() == [*1..4, *6..9]
    }

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
        targetedRule("group", "module1") { ComponentSelection cs, IvyModuleDescriptor ivm -> closuresCalled << 0 }
        targetedRule("group1", "module") { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        targetedRule("group1", "module") { ComponentSelection cs, IvyModuleDescriptor ivm, ComponentMetadata cm -> closuresCalled << 2 }

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

    def "rule expecting IvyMetadataDescriptor does not get called when not an ivy component"() {
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
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 2 }
        targetedRule("group", "module1") { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 3 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 4 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closuresCalled << 5 }
        targetedRule("group1", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closuresCalled << 6 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closuresCalled << 7 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        closuresCalled.sort() == [0, 2, 4, 5, 7]
    }

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

    @Unroll
    def "rule can have access to component metadata attributes"() {
        def id = Mock(ModuleVersionIdentifier)
        def testAttr = Attribute.of('test', String)
        def metadataProvider = Mock(MetadataProvider) {
            isUsable() >> true
            getComponentMetadata() >> Mock(ComponentMetadata) {
                getId() >> id
                getAttributes() >> {
                    TestUtil.attributesFactory().mutable().attribute(testAttr, attributeValue)
                }
            }
        }

        def matches = []
        when:
        rule { ComponentSelection cs, ComponentMetadata cm ->
            if (cm.attributes.getAttribute(testAttr) == 'ok') {
                matches << cm.id
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
