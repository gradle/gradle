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
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.ClosureBackedRuleAction
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultComponentSelectionRules
import org.gradle.api.internal.artifacts.metadata.DefaultDependencyMetaData
import org.gradle.api.internal.artifacts.metadata.DefaultIvyModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.DefaultMavenModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData
import spock.lang.Specification

class ComponentSelectionRulesProcessorTest extends Specification {
    def processor = new ComponentSelectionRulesProcessor()
    def rules = []
    ComponentSelectionInternal componentSelection

    def setup() {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def dependencyMetaData = new DefaultDependencyMetaData(componentIdentifier)
        componentSelection = new DefaultComponentSelection(dependencyMetaData, componentIdentifier)
    }

    def "all non-rejecting rules are evaluated" () {
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def closureCalled = []
        when:
        rule { ComponentSelection cs -> closureCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2}
        rule { ComponentSelection cs, ModuleVersionMetaData mvm -> closureCalled << 3}
        rule { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 4 }
        rule { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 5 }
        rule { ComponentSelection cs, ComponentMetadata cm, ModuleVersionMetaData mvm -> closureCalled << 6 }
        rule { ComponentSelection cs, ModuleVersionMetaData mvm, ComponentMetadata cm -> closureCalled << 7 }

        and:
        apply(moduleAccess)

        then:
        !componentSelection.rejected
        closureCalled == [0, 1, 2, 3, 4, 5, 6, 7]
    }

    def "short-circuits evaluate when rule rejects candidate" () {
        def ComponentSelectionRules rules = new DefaultComponentSelectionRules()
        def moduleAccess = Mock(ModuleComponentRepositoryAccess) {
            0 * resolveComponentMetaData(_, _, _)
        }
        def closuresCalled = []

        when:
        rule { ComponentSelection cs -> closuresCalled << 0 }
        rule { ComponentSelection cs -> cs.reject("rejecting") }
        rule { ComponentSelection cs -> closuresCalled << 1 }

        and:
        apply(moduleAccess)

        then:
        componentSelection.rejected
        componentSelection.rejectionReason == "rejecting"
        closuresCalled == [0]
    }

    def "prefers non-metadata rules over rules requiring metadata"() {
        def ComponentSelectionRules rules = new DefaultComponentSelectionRules()
        def moduleAccess = Mock(ModuleComponentRepositoryAccess) {
            0 * resolveComponentMetaData(_, _, _)
        }
        def closuresCalled = []

        when:
        rule { ComponentSelection cs -> closuresCalled << 0 }
        rule { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        rule { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 2}
        rule { ComponentSelection cs, ModuleVersionMetaData mvm -> closuresCalled << 3}
        rule { ComponentSelection cs -> closuresCalled << 4 }
        rule { ComponentSelection cs -> cs.reject("rejected") }

        and:
        apply(moduleAccess)

        then:
        componentSelection.rejected
        componentSelection.rejectionReason == "rejected"

        and:
        closuresCalled == [0, 4]
    }

    def "metadata is not requested for rules that don't require it"() {
        def ComponentSelectionRules rules = new DefaultComponentSelectionRules()
        def moduleAccess = Mock(ModuleComponentRepositoryAccess) {
            0 * resolveComponentMetaData(_, _, _)
        }
        def closureCalled = false

        when:
        rule { ComponentSelection cs -> closureCalled = true }

        apply(moduleAccess)

        then:
        !componentSelection.rejected
        closureCalled
    }

    def "produces sensible error when rule action throws exception" () {
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }

        when:
        rule { ComponentSelection selection -> throw new Exception("From test")}
        apply(moduleAccess)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not apply component selection rule with all()."
        e.cause.message == "From test"
    }

    def "rule expecting IvyMetadataDescriptor does not get called when not an ivy component" () {
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultMavenModuleVersionMetaData(Stub(ModuleDescriptor), "bundle", false)
                result.resolved(md, null)
            }
        }
        def closureCalled = false

        when:
        rule { ComponentSelection cs, IvyModuleDescriptor ivm, ComponentMetadata cm -> closureCalled = true }
        apply(moduleAccess)

        then:
        ! closureCalled
    }

    def rule(Closure<?> closure) {
        rules << new ClosureBackedRuleAction<ComponentSelection>(ComponentSelection, closure)
    }

    def apply(def moduleAccess) {
        processor.apply(componentSelection, rules, moduleAccess)
    }
}
