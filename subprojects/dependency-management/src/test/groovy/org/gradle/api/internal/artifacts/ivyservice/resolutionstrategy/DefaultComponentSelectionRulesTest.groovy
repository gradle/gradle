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

import org.gradle.api.*
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.internal.rules.NoInputsRuleAction
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.RuleActionAdapter
import org.gradle.internal.rules.RuleActionValidationException
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DefaultComponentSelection
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentSelectionRulesProcessor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

class DefaultComponentSelectionRulesTest extends Specification {
    ComponentSelectionRulesInternal rules = new DefaultComponentSelectionRules()
    ComponentSelectionInternal componentSelection

    def setup() {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        componentSelection = new DefaultComponentSelection(componentIdentifier)
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
        rules.rules[0].action.inputTypes == []
        rules.rules[1].action.inputTypes == [ComponentMetadata]
        rules.rules[2].action.inputTypes == [IvyModuleDescriptor]
        rules.rules[3].action.inputTypes == [IvyModuleDescriptor, ComponentMetadata]
        rules.rules[4].action.inputTypes == []
        rules.rules[5].action.inputTypes == [ComponentMetadata]
        rules.rules[6].action.inputTypes == [IvyModuleDescriptor]
        rules.rules[7].action.inputTypes == [IvyModuleDescriptor, ComponentMetadata]
    }

    def "can add metadata rules via api"() {
        def metadataRule = new TestRuleAction()

        when:
        rules.all metadataRule
        rules.module("group:module", metadataRule)

        then:
        rules.rules[0].action == metadataRule
        rules.rules[1].action == metadataRule
        rules.rules[1].spec.target == DefaultModuleIdentifier.newId("group", "module")
    }

    def "can add action rules via api"() {
        def Action<ComponentSelection> action = new TestComponentSelectionAction()
        rules.ruleActionAdapter = Mock(RuleActionAdapter) {
            2 * createFromAction(_) >> { Action providedAction ->
                new NoInputsRuleAction<ComponentSelection>(providedAction)
            }
        }

        when:
        rules.all action
        rules.module("group:module", action)

        then:
        rules.rules[0].action.action == action
        rules.rules[1].action.action == action
        rules.rules[1].spec.target == DefaultModuleIdentifier.newId("group", "module")
    }

    def "produces sensible error with parameter-less closure" () {
        when:
        rules.all { }

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "The closure provided is not valid as a rule for 'ComponentSelectionRules'."
        e.cause.message == "First parameter of rule action closure must be of type 'ComponentSelection'."
    }

    def "produces sensible error for invalid closure" () {
        when:
        rules.all closure

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "The closure provided is not valid as a rule for 'ComponentSelectionRules'."
        e.cause.message == message

        where:
        closure                                                                                       | message
        { it -> }                                                                                     | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { String something -> }                                                                       | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { IvyModuleDescriptor imd, ComponentMetadata cm -> }                                          | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { ComponentSelection cs, String something -> }                                                | "Unsupported parameter type: java.lang.String"
        { ComponentSelection cs, ComponentMetadata cm, String something -> }                          | "Unsupported parameter type: java.lang.String"
        { ComponentSelection cs, IvyModuleDescriptor imd, String something -> }                       | "Unsupported parameter type: java.lang.String"
        { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm, String something -> } | "Unsupported parameter type: java.lang.String"
    }

    def "produces sensible error for invalid targeted closure" () {
        when:
        rules.module("group:module", closure)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "The closure provided is not valid as a rule for 'ComponentSelectionRules'."
        e.cause.message == message

        where:
        closure                                                                                       | message
        { it -> }                                                                                     | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { String something -> }                                                                       | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { IvyModuleDescriptor imd, ComponentMetadata cm -> }                                          | "First parameter of rule action closure must be of type 'ComponentSelection'."
        { ComponentSelection cs, String something -> }                                                | "Unsupported parameter type: java.lang.String"
        { ComponentSelection cs, ComponentMetadata cm, String something -> }                          | "Unsupported parameter type: java.lang.String"
        { ComponentSelection cs, IvyModuleDescriptor imd, String something -> }                       | "Unsupported parameter type: java.lang.String"
        { ComponentSelection cs, IvyModuleDescriptor imd, ComponentMetadata cm, String something -> } | "Unsupported parameter type: java.lang.String"
    }

    def "produces sensible error when bad input type is declared for rule action" () {
        def ruleAction = Mock(RuleAction)

        when:
        ruleAction.inputTypes >> inputTypes
        rules.all ruleAction

        then:
        def e = thrown(RuleActionValidationException)
        e.message == message

        where:
        inputTypes                                       | message
        [String]                                         | "Unsupported parameter type: java.lang.String"
        [ComponentMetadata, String]                      | "Unsupported parameter type: java.lang.String"
        [IvyModuleDescriptor, String]                    | "Unsupported parameter type: java.lang.String"
        [ComponentMetadata, IvyModuleDescriptor, String] | "Unsupported parameter type: java.lang.String"
        [ComponentMetadataDetails]                       | "Unsupported parameter type: ${ComponentMetadataDetails.name}"
    }

    def "produces sensible error when bad input type is declared for a targeted rule action" () {
        def ruleAction = Mock(RuleAction)

        when:
        ruleAction.inputTypes >> inputTypes
        rules.module("group:module", ruleAction)

        then:
        def e = thrown(RuleActionValidationException)
        e.message == message

        where:
        inputTypes                                       | message
        [String]                                         | "Unsupported parameter type: java.lang.String"
        [ComponentMetadata, String]                      | "Unsupported parameter type: java.lang.String"
        [IvyModuleDescriptor, String]                    | "Unsupported parameter type: java.lang.String"
        [ComponentMetadata, IvyModuleDescriptor, String] | "Unsupported parameter type: java.lang.String"
        [ComponentMetadataDetails]                       | "Unsupported parameter type: ${ComponentMetadataDetails.name}"
    }

    def "produces sensible error when null module id is provided" () {
        when:
        rules.module(id, closureOrActionOrRule)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not add a component selection rule for module 'null'."
        def cause = e.cause
        cause.message.startsWith("Cannot convert a null value to an object of type ModuleIdentifier.")

        where:
        id                     | closureOrActionOrRule
        null                   | new TestRuleAction()
        null                   | { ComponentSelection cs -> }
        null                   | new TestComponentSelectionAction()
    }

    def "produces sensible error when un-parsable module id is provided" () {
        when:
        rules.module(id, closureOrActionOrRule)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not add a component selection rule for module '${id}'."
        def cause = e.cause
        cause.message.startsWith("Cannot convert the provided notation to an object of type ModuleIdentifier: ${id}.")

        where:
        id                     | closureOrActionOrRule
        ""                     | new TestRuleAction()
        "module"               | new TestRuleAction()
        "group:module:version" | new TestRuleAction()
        ""                     | { ComponentSelection cs -> }
        "module"               | { ComponentSelection cs -> }
        "group:module:version" | { ComponentSelection cs -> }
        ""                     | new TestComponentSelectionAction()
        "module"               | new TestComponentSelectionAction()
        "group:module:version" | new TestComponentSelectionAction()
    }

    def "produces sensible error when illegal characters are provided in target module id" () {
        when:
        rules.module("group:module${character}", closureOrActionOrRule)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Could not add a component selection rule for module 'group:module${character}'."
        def cause = e.cause
        cause.message.startsWith("Cannot convert the provided notation to an object of type ModuleIdentifier: group:module${character}.")

        where:
        character  | closureOrActionOrRule
        "+"        | new TestRuleAction()
        "*"        | new TestRuleAction()
        "["        | new TestRuleAction()
        "]"        | new TestRuleAction()
        "("        | new TestRuleAction()
        ")"        | new TestRuleAction()
        ","        | new TestRuleAction()
        "+"        | { ComponentSelection cs -> }
        "*"        | { ComponentSelection cs -> }
        "["        | { ComponentSelection cs -> }
        "]"        | { ComponentSelection cs -> }
        "("        | { ComponentSelection cs -> }
        ")"        | { ComponentSelection cs -> }
        ","        | { ComponentSelection cs -> }
        "+"        | new TestComponentSelectionAction()
        "*"        | new TestComponentSelectionAction()
        "["        | new TestComponentSelectionAction()
        "]"        | new TestComponentSelectionAction()
        "("        | new TestComponentSelectionAction()
        ")"        | new TestComponentSelectionAction()
        ","        | new TestComponentSelectionAction()
    }

    def "ComponentSelectionSpec matches on group and name" () {
        def spec = new DefaultComponentSelectionRules.ComponentSelectionMatchingSpec(DefaultModuleIdentifier.newId(group, name))
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
