/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.SELECTED_BY_RULE

class DefaultDependencySubstitutionsSpec extends Specification {
    ComponentIdentifierFactory componentIdentifierFactory = Mock(ComponentIdentifierFactory)
    ImmutableModuleIdentifierFactory moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    DependencySubstitutionsInternal substitutions;

    def setup() {
        substitutions = DefaultDependencySubstitutions.forResolutionStrategy(componentIdentifierFactory, moduleIdentifierFactory)
    }

    def "provides no op resolve rule when no rules or forced modules configured"() {
        given:
        def details = Mock(DependencySubstitutionInternal)

        when:
        substitutions.ruleAction.execute(details)

        then:
        0 * details._
    }

    def "all() matches modules and projects"() {
        given:
        def action = Mock(Action)
        substitutions.all(action)

        def moduleDetails = Mock(DependencySubstitutionInternal)

        when:
        substitutions.ruleAction.execute(moduleDetails)

        then:
        _ * moduleDetails.requested >> DefaultModuleComponentSelector.newSelector("org.utils", "api", "1.5")
        1 * action.execute(moduleDetails)
        0 * _

        def projectDetails = Mock(DependencySubstitutionInternal)

        when:
        substitutions.ruleAction.execute(projectDetails)

        then:
        _ * projectDetails.requested >> TestComponentIdentifiers.newSelector(":api")
        1 * action.execute(projectDetails)
        0 * _
    }

    def "allWithDependencyResolveDetails() wraps substitution in legacy format"() {
        given:
        def action = Mock(Action)
        substitutions.allWithDependencyResolveDetails(action)

        def moduleOldRequested = DefaultModuleVersionSelector.newSelector("org.utils", "api", "1.5")
        def moduleTarget = DefaultModuleComponentSelector.newSelector(moduleOldRequested)
        def moduleDetails = Mock(DependencySubstitutionInternal)

        when:
        substitutions.ruleAction.execute(moduleDetails)

        then:
        _ * moduleDetails.target >> moduleTarget
        _ * moduleDetails.oldRequested >> moduleOldRequested
        1 * action.execute({ DependencyResolveDetailsInternal details ->
            details.requested == moduleOldRequested
        })
        0 * _

        def projectOldRequested = DefaultModuleVersionSelector.newSelector("org.utils", "api", "1.5")
        def projectTarget = TestComponentIdentifiers.newSelector(":api")
        def projectDetails = Mock(DependencySubstitutionInternal)

        when:
        substitutions.ruleAction.execute(projectDetails)

        then:
        _ * projectDetails.target >> projectTarget
        _ * projectDetails.oldRequested >> projectOldRequested
        1 * action.execute({ DependencyResolveDetailsInternal details ->
            details.requested == projectOldRequested
        })
        0 * _
    }

    @Unroll
    def "substitute module() matches only given module: #matchingModule"() {
        given:
        def matchingSubstitute = Mock(ComponentSelector)
        def nonMatchingSubstitute = Mock(ComponentSelector)
        def moduleDetails = Mock(DependencySubstitutionInternal)

        with(substitutions) {
            substitute module(matchingModule) with matchingSubstitute
            substitute module(nonMatchingModule) with nonMatchingSubstitute
        }

        when:
        substitutions.ruleAction.execute(moduleDetails)

        then:
        _ * moduleDetails.requested >> DefaultModuleComponentSelector.newSelector("org.utils", "api", "1.5")
        1 * moduleDetails.useTarget(matchingSubstitute, SELECTED_BY_RULE)
        0 * _

        when:
        substitutions.ruleAction.execute(moduleDetails)

        then:
        _ * moduleDetails.requested >> TestComponentIdentifiers.newSelector(":api")
        0 * _

        where:
        matchingModule      | nonMatchingModule
        "org.utils:api:1.5" | "org.utils:api:1.6"
        "org.utils:api"     | "org.utils:impl"
    }

    def "cannot substitute with unversioned module selector"() {
        when:
        with(substitutions) {
            substitute project("foo") with module('group:name')
        }

        then:
        def t = thrown(InvalidUserDataException)
        t.message == "Must specify version for target of dependency substitution"
    }

    @Unroll
    def "substitute project() matches only given project: #matchingProject"() {
        given:
        def matchingSubstitute = Mock(ComponentSelector)
        def nonMatchingSubstitute = Mock(ComponentSelector)

        componentIdentifierFactory.createProjectComponentSelector(":api") >> TestComponentIdentifiers.newSelector(":api")
        componentIdentifierFactory.createProjectComponentSelector(":impl") >> TestComponentIdentifiers.newSelector(":impl")

        with(substitutions) {
            substitute project(matchingProject) with matchingSubstitute
            substitute project(nonMatchingProject) with nonMatchingSubstitute
        }

        def projectDetails = Mock(DependencySubstitutionInternal)

        when:
        substitutions.ruleAction.execute(projectDetails)

        then:
        _ * projectDetails.requested >> TestComponentIdentifiers.newSelector(":api")
        1 * projectDetails.useTarget(matchingSubstitute, SELECTED_BY_RULE)
        0 * _

        when:
        substitutions.ruleAction.execute(projectDetails)

        then:
        _ * projectDetails.requested >> DefaultModuleComponentSelector.newSelector("org.utils", "api", "1.5")
        0 * _

        where:
        matchingProject | nonMatchingProject
        ":api"          | ":impl"
    }

    def "provides dependency substitution rule that orderly aggregates user specified rules"() {
        given:
        substitutions.all({ it.useTarget("1.0") } as Action)
        substitutions.all({ it.useTarget("2.0") } as Action)
        substitutions.all({ it.useTarget("3.0") } as Action)
        def details = Mock(DependencySubstitutionInternal)

        when:
        substitutions.ruleAction.execute(details)

        then:
        1 * details.useTarget("1.0")
        then:
        1 * details.useTarget("2.0")
        then:
        1 * details.useTarget("3.0")
        0 * details._
    }

    def "mutations trigger lenient validation"() {
        given:
        def validator = Mock(MutationValidator)
        substitutions.setMutationValidator(validator)

        when:
        substitutions.all(Mock(Action))
        then:
        1 * validator.validateMutation(STRATEGY)

        when:
        with(substitutions) {
            substitute module("org:foo") with project(":bar")
        }
        then:
        1 * validator.validateMutation(STRATEGY)

        when:
        with(substitutions) {
            substitute project(":bar") with module("org:foo:1.0")
        }
        then:
        1 * validator.validateMutation(STRATEGY)
    }

    def "mutating copy does not trigger original validator"() {
        given:
        def validator = Mock(MutationValidator)
        substitutions.setMutationValidator(validator)
        def copy = substitutions.copy()

        when:
        copy.all(Mock(Action))

        then:
        0 * validator.validateMutation(_)
    }
}
