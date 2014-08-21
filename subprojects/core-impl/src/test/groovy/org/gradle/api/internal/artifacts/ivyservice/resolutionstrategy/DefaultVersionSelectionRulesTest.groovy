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
import org.gradle.api.artifacts.VersionSelection
import org.gradle.api.artifacts.VersionSelectionRules
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.VersionSelectionInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.artifacts.metadata.DefaultIvyModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.DefaultMavenModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import spock.lang.Specification

class DefaultVersionSelectionRulesTest extends Specification {
    def "all closure rules added get applied" () {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def closureCalled = [ false, false, false, false, false ]
        def closure0 = { VersionSelection vs -> closureCalled[0] = true }
        def closure1 = { VersionSelection vs, ComponentMetadata cm -> closureCalled[1] = true }
        def closure2 = { VersionSelection vs, IvyModuleDescriptor imd -> closureCalled[2] = true }
        def closure3 = { VersionSelection vs, IvyModuleDescriptor imd, ComponentMetadata cm -> closureCalled[3] = true }
        def closure4 = { VersionSelection vs, ComponentMetadata cm, IvyModuleDescriptor imd -> closureCalled[4] = true }

        when:
        versionSelectionRules.all closure0
        versionSelectionRules.all closure1
        versionSelectionRules.all closure2
        versionSelectionRules.all closure3
        versionSelectionRules.all closure4
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

        then:
        closureCalled[0]
        closureCalled[1]
        closureCalled[2]
        closureCalled[3]
        closureCalled[4]
    }

    def "metadata is not requested for rules that don't require it"() {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def moduleAccess = Mock(ModuleComponentRepositoryAccess) {
            0 * resolveComponentMetaData(_, _, _)
        }
        def closureCalled = false
        def closure = { VersionSelection vs -> closureCalled = true }

        when:
        versionSelectionRules.all closure
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

        then:
        closureCalled
    }

    def "can add metadata rules via api"() {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def metadataRule = new TestRuleAction()
        metadataRule.inputTypes = inputTypes

        when:
        versionSelectionRules.all metadataRule
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

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
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess)
        def Action<VersionSelection> action = new TestVersionSelectionAction()

        when:
        versionSelectionRules.all action
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

        then:
        action.called
    }

    def "produces sensible error with parameter-less closure" () {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()

        when:
        versionSelectionRules.all { }

        then:
        def e = thrown(InvalidActionClosureException)
        e.message == "The closure provided is not valid as a rule action for 'VersionSelectionRules'."
        e.cause.message == "First parameter of rule action closure must be of type 'VersionSelection'."
    }

    def "produces sensible error for invalid closure" () {
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()

        when:
        versionSelectionRules.all closure
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

        then:
        def e = thrown(InvalidActionClosureException)
        e.message == "The closure provided is not valid as a rule action for 'VersionSelectionRules'."
        e.cause.message == message

        where:
        closure                                                                                     | message
        { it -> }                                                                                   | "First parameter of rule action closure must be of type 'VersionSelection'."
        { String something -> }                                                                     | "First parameter of rule action closure must be of type 'VersionSelection'."
        { IvyModuleDescriptor imd, ComponentMetadata cm -> }                                        | "First parameter of rule action closure must be of type 'VersionSelection'."
        { VersionSelection vs, String something -> }                                                | "Unsupported parameter type for version selection rule: java.lang.String"
        { VersionSelection vs, ComponentMetadata cm, String something -> }                          | "Unsupported parameter type for version selection rule: java.lang.String"
        { VersionSelection vs, IvyModuleDescriptor imd, String something -> }                       | "Unsupported parameter type for version selection rule: java.lang.String"
        { VersionSelection vs, IvyModuleDescriptor imd, ComponentMetadata cm, String something -> } | "Unsupported parameter type for version selection rule: java.lang.String"
    }


    def "produces sensible error when bad input type is declared for rule action" () {
        def ruleAction = Mock(RuleAction)
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()

        when:
        ruleAction.inputTypes >> inputTypes
        versionSelectionRules.all ruleAction

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == message

        where:
        inputTypes                                       | message
        [String]                                         | "Unsupported parameter type for version selection rule: java.lang.String"
        [ComponentMetadata, String]                      | "Unsupported parameter type for version selection rule: java.lang.String"
        [IvyModuleDescriptor, String]                    | "Unsupported parameter type for version selection rule: java.lang.String"
        [ComponentMetadata, IvyModuleDescriptor, String] | "Unsupported parameter type for version selection rule: java.lang.String"
        [ComponentMetadataDetails]                       | "Unsupported parameter type for version selection rule: ${ComponentMetadataDetails.name}"
    }

    def "produces sensible error when closure or rule or action throws exception" () {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }

        when:
        versionSelectionRules.all closureOrRuleOrAction
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not apply version selection rule with all()."
        e.cause.message == "From test"

        where:
        closureOrRuleOrAction                                                                                      | _
        new TestExceptionAction()                                                                                  | _
        new TestExceptionRuleAction()                                                                            | _
        { VersionSelection vs -> throw new Exception("From test") }                                                | _
        { VersionSelection vs, ComponentMetadata cm -> throw new Exception("From test") }                          | _
        { VersionSelection vs, ComponentMetadata cm, IvyModuleDescriptor imd -> throw new Exception("From test") } | _
    }

    def "produces sensible error when bad input types are provided with rule"() {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultIvyModuleVersionMetaData(Stub(ModuleDescriptor))
                result.resolved(md, null)
            }
        }
        def metadataRule = new TestRuleAction()
        metadataRule.inputTypes = inputTypes

        when:
        versionSelectionRules.all metadataRule
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Unsupported parameter type for version selection rule: java.lang.String"

        where:
        inputTypes                                                           | _
        [ String.class ]                                                     | _
        [ ComponentMetadata.class, String.class ]                            | _
        [ IvyModuleDescriptor.class, ComponentMetadata.class, String.class ] | _
    }

    def "rule expecting IvyMetadataDescriptor does not get called when not an ivy component" () {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def moduleAccess = Stub(ModuleComponentRepositoryAccess) {
            resolveComponentMetaData(_, _, _) >> { DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result ->
                def md = new DefaultMavenModuleVersionMetaData(Stub(ModuleDescriptor), "bundle", false)
                result.resolved(md, null)
            }
        }
        def closureCalled = false

        when:
        versionSelectionRules.all { VersionSelection vs, IvyModuleDescriptor ivm, ComponentMetadata cm -> closureCalled = true }
        versionSelectionRules.apply(Stub(VersionSelectionInternal), moduleAccess)

        then:
        ! closureCalled
    }

    def "accurately returns whether or not any rules are configured" () {
        when:
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()

        then:
        ! versionSelectionRules.hasRules()

        when:
        versionSelectionRules.all closureOrActionOrRule

        then:
        versionSelectionRules.hasRules()

        where:
        closureOrActionOrRule            | _
        { VersionSelection vs -> }       | _
        new TestRuleAction()           | _
        new TestVersionSelectionAction() | _
    }

    private class TestRuleAction implements RuleAction<VersionSelection> {
        boolean called = false
        List<Class> inputTypes = []


        @Override
        List<Class<?>> getInputTypes() {
            return inputTypes
        }

        @Override
        void execute(VersionSelection subject, List inputs) {
            called = true
        }
    }

    private class TestExceptionRuleAction extends TestRuleAction {
        @Override
        void execute(VersionSelection subject, List inputs) {
            throw new Exception("From test")
        }
    }

    private class TestVersionSelectionAction implements Action<VersionSelection> {
        boolean called = false

        @Override
        void execute(VersionSelection versionSelection) {
            called = true
        }
    }

    private class TestExceptionAction implements Action<VersionSelection> {
        @Override
        void execute(VersionSelection versionSelection) {
            throw new Exception("From test")
        }
    }
}
