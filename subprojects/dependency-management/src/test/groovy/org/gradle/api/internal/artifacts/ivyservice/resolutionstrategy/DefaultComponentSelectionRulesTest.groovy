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
import org.gradle.api.Action
import org.gradle.api.InvalidActionClosureException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.RuleAction
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.NoInputsRuleAction
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentSelectionRulesProcessor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.internal.component.model.DefaultDependencyMetaData
import spock.lang.Specification

class DefaultComponentSelectionRulesTest extends Specification {
    ComponentSelectionRulesInternal rules = new DefaultComponentSelectionRules()
    ComponentSelectionInternal componentSelection

    def setup() {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def dependencyMetaData = new DefaultDependencyMetaData(componentIdentifier)
        componentSelection = new DefaultComponentSelection(dependencyMetaData, componentIdentifier)
    }

    def "converts closure input to rule actions"() {
        when:
        rules.all { ComponentSelection cs ->  }
        rules.all { ComponentSelection cs, ComponentMetadata cm ->  }
        rules.all { ComponentSelection cs, IvyModuleDescriptor imd -> }
        rules.all { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> }
        rules.module("group:module") { ComponentSelection cs ->  }
        rules.module("group:module") { ComponentSelection cs, ComponentMetadata cm ->  }
        rules.module("group:module") { ComponentSelection cs, IvyModuleDescriptor imd -> }
        rules.module("group:module") { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm -> }

        then:
        rules.rules[0].inputTypes == []
        rules.rules[1].inputTypes == [ComponentMetadata]
        rules.rules[2].inputTypes == [IvyModuleDescriptor]
        rules.rules[3].inputTypes == [IvyModuleDescriptor, ComponentMetadata]

        rules.rules[4].ruleAction.inputTypes == []
        rules.rules[5].ruleAction.inputTypes == [ComponentMetadata]
        rules.rules[6].ruleAction.inputTypes == [IvyModuleDescriptor]
        rules.rules[7].ruleAction.inputTypes == [IvyModuleDescriptor, ComponentMetadata]
    }

    def "can add metadata rules via api"() {
        def metadataRule = new TestRuleAction()

        when:
        rules.all metadataRule
        rules.module("group:module", metadataRule)

        then:
        rules.rules[0] == metadataRule
        rules.rules[1].ruleAction == metadataRule
        rules.rules[1].spec.group == "group"
        rules.rules[1].spec.module == "module"
    }

    def "can add action rules via api"() {
        def Action<ComponentSelection> action = new TestComponentSelectionAction()

        when:
        rules.all action
        rules.module("group:module", action)

        then:
        def ruleAction = rules.rules[0]
        ruleAction.inputTypes == []
        ruleAction instanceof NoInputsRuleAction
        ruleAction.action == action

        def targetRuleAction = rules.rules[1].ruleAction
        targetRuleAction.inputTypes == []
        targetRuleAction instanceof NoInputsRuleAction
        targetRuleAction.action == action
        rules.rules[1].spec.group == "group"
        rules.rules[1].spec.module == "module"
    }

    def "produces sensible error with parameter-less closure" () {
        when:
        rules.all { }

        then:
        def e = thrown(InvalidActionClosureException)
        e.message == "The closure provided is not valid as a rule action for 'ComponentSelectionRules'."
        e.cause.message == "First parameter of rule action closure must be of type 'ComponentSelection'."
    }

    def "produces sensible error for invalid closure" () {
        when:
        rules.all closure

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

    def "produces sensible error for invalid targeted closure" () {
        when:
        rules.module("group:module", closure)

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

    def "produces sensible error when bad input type is declared for rule action" () {
        def ruleAction = Mock(RuleAction)

        when:
        ruleAction.inputTypes >> inputTypes
        rules.all ruleAction

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

    def "produces sensible error when bad input type is declared for a targeted rule action" () {
        def ruleAction = Mock(RuleAction)

        when:
        ruleAction.inputTypes >> inputTypes
        rules.module("group:module", ruleAction)

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

    def "produces sensible error when bad target module id is provided" () {
        when:
        rules.module(id, closureOrActionOrRule)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Unsupported format for module constraint: '${id}'.  This should be in the format of 'group:module'."

        where:
        id                     | closureOrActionOrRule
        null                   | new TestRuleAction()
        ""                     | new TestRuleAction()
        "module"               | new TestRuleAction()
        "group:module:version" | new TestRuleAction()
        "group:module+"        | new TestRuleAction()
        "group:module*"        | new TestRuleAction()
        "group:module["        | new TestRuleAction()
        "group:module]"        | new TestRuleAction()
        "group:module("        | new TestRuleAction()
        "group:module)"        | new TestRuleAction()
        "group:module,"        | new TestRuleAction()
        null                   | { ComponentSelection cs -> }
        ""                     | { ComponentSelection cs -> }
        "module"               | { ComponentSelection cs -> }
        "group:module:version" | { ComponentSelection cs -> }
        "group:module+"        | { ComponentSelection cs -> }
        "group:module*"        | { ComponentSelection cs -> }
        "group:module["        | { ComponentSelection cs -> }
        "group:module]"        | { ComponentSelection cs -> }
        "group:module("        | { ComponentSelection cs -> }
        "group:module)"        | { ComponentSelection cs -> }
        "group:module,"        | { ComponentSelection cs -> }
        null                   | new TestComponentSelectionAction()
        ""                     | new TestComponentSelectionAction()
        "module"               | new TestComponentSelectionAction()
        "group:module:version" | new TestComponentSelectionAction()
        "group:module+"        | new TestComponentSelectionAction()
        "group:module*"        | new TestComponentSelectionAction()
        "group:module["        | new TestComponentSelectionAction()
        "group:module]"        | new TestComponentSelectionAction()
        "group:module("        | new TestComponentSelectionAction()
        "group:module)"        | new TestComponentSelectionAction()
        "group:module,"        | new TestComponentSelectionAction()
    }

    def "ComponentSelectionSpec matches on group and name" () {
        def spec = new DefaultComponentSelectionRules.ComponentSelectionMatchingSpec(group, name)
        def candidate = Mock(ModuleComponentIdentifier) {
            1 * getGroup() >> "org.gradle"
            (0..1) * getModule() >> "api"
        }
        def selection = Stub(ComponentSelection) {
            getCandidate() >> candidate
        }

        expect:
        spec.isSatisfiedBy(selection) == matches

        where:
        group        | name  | matches
        "org.gradle" | "api" | true
        "org.gradle" | "lib" | false
        "com.gradle" | "api" | false
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

    private class TestComponentSelectionAction implements Action<ComponentSelection> {
        boolean called = false

        @Override
        void execute(ComponentSelection componentSelection) {
            called = true
        }
    }

    def process(ModuleComponentRepositoryAccess moduleAccess) {
        new ComponentSelectionRulesProcessor().apply(componentSelection, rules.rules, moduleAccess)
    }
}
