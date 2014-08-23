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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.Action
import org.gradle.api.InvalidActionClosureException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.RuleAction
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.artifacts.metadata.DefaultDependencyMetaData
import org.gradle.api.internal.artifacts.metadata.DefaultIvyModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.DefaultMavenModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import spock.lang.Specification

class DefaultComponentSelectionRulesTest extends Specification {
    ComponentSelectionInternal componentSelection

    def setup() {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def dependencyMetaData = new DefaultDependencyMetaData(componentIdentifier)
        componentSelection = new DefaultComponentSelection(dependencyMetaData, componentIdentifier)
    }


    def "all non-rejecting rules are evaluated" () {
        def ComponentSelectionRules rules = new DefaultComponentSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def closureCalled = []
        when:
        rules.all { ComponentSelection cs -> closureCalled << 0 }
        rules.all { ComponentSelection cs, ComponentMetadata cm -> closureCalled << 1 }
        rules.all { ComponentSelection cs, IvyModuleDescriptor imd -> closureCalled << 2}
        rules.all { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled << 3 }
        rules.all { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled << 4 }

        and:
        rules.apply(componentSelection, moduleAccess)

        then:
        !componentSelection.rejected
        closureCalled == [0, 1, 2, 3, 4]
    }

    def "short-circuits evaluate when rule rejects candidate" () {
        def ComponentSelectionRules rules = new DefaultComponentSelectionRules()
        def moduleAccess = Mock(ModuleComponentRepositoryAccess) {
            0 * resolveComponentMetaData(_, _, _)
        }
        def closuresCalled = []

        when:
        rules.all { ComponentSelection cs -> closuresCalled << 0 }
        rules.all { ComponentSelection cs -> cs.reject("rejecting") }
        rules.all { ComponentSelection cs -> closuresCalled << 1 }

        and:
        rules.apply(componentSelection, moduleAccess)

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
        rules.all { ComponentSelection cs -> closuresCalled << 0 }
        rules.all { ComponentSelection cs, ComponentMetadata cm -> closuresCalled << 1 }
        rules.all { ComponentSelection cs, IvyModuleDescriptor imd -> closuresCalled << 2}
        rules.all { ComponentSelection cs -> closuresCalled << 3 }
        rules.all { ComponentSelection cs -> cs.reject("rejected") }

        and:
        rules.apply(componentSelection, moduleAccess)

        then:
        componentSelection.rejected
        componentSelection.rejectionReason == "rejected"

        and:
        closuresCalled == [0, 3]
    }

    def "metadata is not requested for rules that don't require it"() {
        def ComponentSelectionRules rules = new DefaultComponentSelectionRules()
        def moduleAccess = Mock(ModuleComponentRepositoryAccess) {
            0 * resolveComponentMetaData(_, _, _)
        }
        def closureCalled = false

        when:
        rules.all { ComponentSelection cs -> closureCalled = true }
        rules.apply(componentSelection, moduleAccess)

        then:
        !componentSelection.rejected
        closureCalled
    }

    def "produces sensible error with parameter-less closure" () {
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()

        when:
        componentSelectionRules.all { }

        then:
        def e = thrown(InvalidActionClosureException)
        e.message == "The closure provided is not valid as a rule action for 'ComponentSelectionRules'."
        e.cause.message == "First parameter of rule action closure must be of type 'ComponentSelection'."
    }

    def "produces sensible error for invalid closure" () {
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()

        when:
        componentSelectionRules.all closure
        componentSelectionRules.apply(Stub(ComponentSelectionInternal), moduleAccess)

        then:
        def e = thrown(InvalidActionClosureException)
        e.message == "The closure provided is not valid as a rule action for 'ComponentSelectionRules'."
        e.cause.message == message

        where:
        closure                                                                                       | message
        { it -> }                                                                                     | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { String something -> }                                                                       | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { IvyModuleDescriptor imd, ComponentMetadata cm -> }                                          | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { ComponentSelection cs, String something -> }                                                | "Unsupported parameter type for component selection rule: java.lang.String"
        { ComponentSelection cs, ComponentMetadata cm, String something -> }                          | "Unsupported parameter type for component selection rule: java.lang.String"
        { ComponentSelection cs, IvyModuleDescriptor imd, String something -> }                       | "Unsupported parameter type for component selection rule: java.lang.String"
        { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm, String something -> } | "Unsupported parameter type for component selection rule: java.lang.String"
    }

    def "can add metadata rules via api"() {
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def metadataRule = new TestRuleAction()
        metadataRule.inputTypes = inputTypes

        when:
        componentSelectionRules.all metadataRule
        componentSelectionRules.apply(componentSelection, moduleAccess)

        then:
        metadataRule.called

        where:
        inputTypes                                             | _
        [ ]                                                    | _
        [ ComponentMetadata.class ]                            | _
        [ IvyModuleDescriptor.class ]                          | _
        [ IvyModuleDescriptor.class, ComponentMetadata.class ] | _
        [ ComponentMetadata.class, IvyModuleDescriptor.class ] | _
    }

    def "can add action rules via api"() {
        def ComponentSelectionRules versionSelectionRules = new DefaultComponentSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess)
        def Action<ComponentSelection> action = new TestComponentSelectionAction()

        when:
        versionSelectionRules.all action
        versionSelectionRules.apply(componentSelection, moduleAccess)

        then:
        action.called
    }

    def "produces sensible error when bad input type is declared for rule action" () {
        def ruleAction = Mock(RuleAction)
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()

        when:
        ruleAction.inputTypes >> inputTypes
        componentSelectionRules.all ruleAction

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == message

        where:
        inputTypes                                       | message
        [String]                                         | "Unsupported parameter type for component selection rule: java.lang.String"
        [ComponentMetadata, String]                      | "Unsupported parameter type for component selection rule: java.lang.String"
        [IvyModuleDescriptor, String]                    | "Unsupported parameter type for component selection rule: java.lang.String"
        [ComponentMetadata, IvyModuleDescriptor, String] | "Unsupported parameter type for component selection rule: java.lang.String"
        [ComponentMetadataDetails]                       | "Unsupported parameter type for component selection rule: ${ComponentMetadataDetails.name}"
    }

    def "produces sensible error when closure or rule or action throws exception" () {
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }

        when:
        componentSelectionRules.all closureOrRuleOrAction
        componentSelectionRules.apply(componentSelection, moduleAccess)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not apply component selection rule with all()."
        e.cause.message == "From test"

        where:
        closureOrRuleOrAction                                                                                        | _
        new TestExceptionAction()                                                                                    | _
        new TestExceptionRuleAction()                                                                                | _
        { ComponentSelection cs -> throw new Exception("From test") }                                                | _
        { ComponentSelection cs, ComponentMetadata cm -> throw new Exception("From test") }                          | _
        { ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> throw new Exception("From test") } | _
    }

    def "produces sensible error when bad input types are provided with rule"() {
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def metadataRule = new TestRuleAction()
        metadataRule.inputTypes = inputTypes

        when:
        componentSelectionRules.all metadataRule
        componentSelectionRules.apply(componentSelection, moduleAccess)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Unsupported parameter type for component selection rule: java.lang.String"

        where:
        inputTypes                                                           | _
        [ String.class ]                                                     | _
        [ ComponentMetadata.class, String.class ]                            | _
        [ IvyModuleDescriptor.class, ComponentMetadata.class, String.class ] | _
    }

    def "rule expecting IvyMetadataDescriptor does not get called when not an ivy component" () {
        def ComponentSelectionRules componentSelectionRules = new DefaultComponentSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultMavenModuleVersionMetaData(Stub(ModuleDescriptor), "bundle", false)
                result.resolved(md, null)
            }
        }
        def closureCalled = false

        when:
        componentSelectionRules.all { ComponentSelection cs, IvyModuleDescriptor ivm, ComponentMetadata cm -> closureCalled = true }
        componentSelectionRules.apply(componentSelection, moduleAccess)

        then:
        ! closureCalled
    }


    private class TestRuleAction implements RuleAction<ComponentSelection> {
        boolean called = false
        List<Class> inputTypes = []


        @Override
        List<Class<?>> getInputTypes() {
            return inputTypes
        }

        @Override
        void execute(ComponentSelection subject, List inputs) {
            called = true
        }
    }

    private class TestExceptionRuleAction extends TestRuleAction {
        @Override
        void execute(ComponentSelection subject, List inputs) {
            throw new Exception("From test")
        }
    }

    private class TestComponentSelectionAction implements Action<ComponentSelection> {
        boolean called = false

        @Override
        void execute(ComponentSelection componentSelection) {
            called = true
        }
    }

    private class TestExceptionAction implements Action<ComponentSelection> {
        @Override
        void execute(ComponentSelection componentSelection) {
            throw new Exception("From test")
        }
    }
}
