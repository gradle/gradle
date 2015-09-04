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

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.internal.rules.ClosureBackedRuleAction
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultComponentSelectionRules
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData
import spock.lang.Specification

class ComponentSelectionRulesProcessorTest extends Specification {
    def processor = new ComponentSelectionRulesProcessor()
    def rules = []
    ComponentSelectionInternal componentSelection

    def setup() {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        componentSelection = new DefaultComponentSelection(componentIdentifier)
    }

    def "all non-rejecting rules are evaluated" () {
        def metadataProvider = Stub(MetadataProvider) {
            resolve() >> true
        }
        def closureCalled = []
        when:
        rule { ComponentSelection cs -> closureCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2 }
        rule { ComponentSelection cs, ModuleComponentResolveMetaData mvm -> closureCalled << 3 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 4 }
        rule { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 5 }
        rule { ComponentSelection cs, ComponentMetadata cm, ModuleComponentResolveMetaData mvm -> closureCalled << 6 }
        rule { ComponentSelection cs, ModuleComponentResolveMetaData mvm, ComponentMetadata cm -> closureCalled << 7 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules without metadata should be executed first
        closureCalled[0] == 0
        // metadata rules get called second in indeterminate order
        closureCalled[1..-1].sort() == 1..7
    }

    def "all non-rejecting targeted rules are evaluated" () {
        def metadataProvider = Stub(MetadataProvider) {
            resolve() >> true
        }
        def closureCalled = []
        when:
        targetedRule("group", "module") { ComponentSelection cs -> closureCalled << 0 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetaData mvm -> closureCalled << 3 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 4 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 5 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, ModuleComponentResolveMetaData mvm -> closureCalled << 6 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetaData mvm, ComponentMetadata cm -> closureCalled << 7 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules without metadata should be executed first
        closureCalled[0] == 0
        // metadata rules get called second in in-determinate order
        closureCalled[1..-1].sort() == 1..7
    }

    def "can call both targeted and untargeted rules" () {
        def metadataProvider = Stub(MetadataProvider) {
            resolve() >> true
        }
        def closureCalled = []
        when:
        rule { ComponentSelection cs -> closureCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2 }
        rule { ComponentSelection cs, ModuleComponentResolveMetaData mvm -> closureCalled << 3 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 4 }
        rule { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 5 }
        rule { ComponentSelection cs, ComponentMetadata cm, ModuleComponentResolveMetaData mvm -> closureCalled << 6 }
        rule { ComponentSelection cs, ModuleComponentResolveMetaData mvm, ComponentMetadata cm -> closureCalled << 7 }
        targetedRule("group", "module") { ComponentSelection cs -> closureCalled << 8 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 9 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 10 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetaData mvm -> closureCalled << 11 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 12 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 13 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, ModuleComponentResolveMetaData mvm -> closureCalled << 14 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetaData mvm, ComponentMetadata cm -> closureCalled << 15 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        // rules without metadata should be executed first in in-determinate order
        closureCalled[0..1].sort() == [0, 8]
        // metadata rules get called second in in-determinate order
        closureCalled[2..-1].sort() == [*1..7, *9..15]
    }

    def "short-circuit prefers non-metadata rules over rules requiring metadata"() {
        def metadataProvider = Mock(MetadataProvider)
        def closuresCalled = []

        when:
        rule { ComponentSelection cs -> closuresCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 2 }
        rule { ComponentSelection cs, ModuleComponentResolveMetaData mvm -> closuresCalled << 3 }
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
        def metadataProvider = Mock(MetadataProvider)
        def closuresCalled = []

        when:
        targetedRule("group", "module") { ComponentSelection cs -> closuresCalled << 0 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 2 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetaData mvm -> closuresCalled << 3 }
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
        def metadataProvider = Mock(MetadataProvider)
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
        def metadataProvider = Mock(MetadataProvider)
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

    def "produces sensible error when rule action throws exception" () {
        def metadataProvider = Mock(MetadataProvider)
        def failure = new Exception("From test")

        when:
        rule { ComponentSelection selection -> throw failure }
        apply(metadataProvider)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "There was an error while evaluating a component selection rule for group:module:version."
        e.cause == failure
    }

    def "rule expecting IvyMetadataDescriptor does not get called when not an ivy component" () {
        def metadataProvider = Stub(MetadataProvider) {
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

    def "only matching targeted rules get called" () {
        def metadataProvider = Stub(MetadataProvider) {
            resolve() >> true
        }
        def closuresCalled = []
        when:
        targetedRule("group", "module") { ComponentSelection cs -> closuresCalled << 0 }
        targetedRule("group1", "module") { ComponentSelection cs -> closuresCalled << 1 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 2 }
        targetedRule("group", "module1") { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 3 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 4 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetaData mvm -> closuresCalled << 5 }
        targetedRule("group", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closuresCalled << 6 }
        targetedRule("group1", "module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closuresCalled << 7 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closuresCalled << 8 }
        targetedRule("group", "module") { ComponentSelection cs, ComponentMetadata cm, ModuleComponentResolveMetaData mvm -> closuresCalled << 9 }
        targetedRule("group", "module") { ComponentSelection cs, ModuleComponentResolveMetaData mvm, ComponentMetadata cm -> closuresCalled << 10 }
        targetedRule("group", "module1") { ComponentSelection cs, ModuleComponentResolveMetaData mvm, ComponentMetadata cm -> closuresCalled << 11 }

        and:
        apply(metadataProvider)

        then:
        !componentSelection.rejected
        closuresCalled.sort() == [0, 2, 4, 5, 6, 8, 9, 10]
    }

    def "does not invoke rules that require meta-data when it cannot be resolved" () {
        def metadataProvider = Stub(MetadataProvider) {
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

    def rule(Closure<?> closure) {
        rules << new SpecRuleAction<ComponentSelection>(
                new ClosureBackedRuleAction<ComponentSelection>(ComponentSelection, closure),
                Specs.<ComponentSelection>satisfyAll()
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
