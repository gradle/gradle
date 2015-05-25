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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependencySubstitution
import org.gradle.api.artifacts.ProjectDependencySubstitution
import org.gradle.api.internal.artifacts.*
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY

class DefaultDependencySubstitutionsSpec extends Specification {
    DependencySubstitutionsInternal substitutions;

    def setup() {
        substitutions = new DefaultDependencySubstitutions()
    }

    def "provides no op resolve rule when no rules or forced modules configured"() {
        given:
        def details = Mock(DependencySubstitutionInternal)

        when:
        substitutions.dependencySubstitutionRule.execute(details)

        then:
        0 * details._
    }

    def "all() matches modules and projects"() {
        given:
        def action = Mock(Action)
        substitutions.all(action)

        def moduleDetails = Mock(ModuleDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(moduleDetails)

        then:
        _ * moduleDetails.requested >> DefaultModuleComponentSelector.newSelector("org.utils", "api", "1.5")
        1 * action.execute(moduleDetails)
        0 * _

        def projectDetails = Mock(ProjectDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(projectDetails)

        then:
        _ * projectDetails.requested >> DefaultProjectComponentSelector.newSelector(":api")
        1 * action.execute(projectDetails)
        0 * _
    }

    def "allWithDependencyResolveDetails() wraps substitution in legacy format"() {
        given:
        def action = Mock(Action)
        substitutions.allWithDependencyResolveDetails(action)

        def moduleOldRequested = DefaultModuleVersionSelector.newSelector("org.utils", "api", "1.5")
        def moduleTarget = DefaultModuleComponentSelector.newSelector(moduleOldRequested)
        def moduleDetails = Mock(ModuleDependencySubstitutionInternal)

        when:
        substitutions.dependencySubstitutionRule.execute(moduleDetails)

        then:
        _ * moduleDetails.target >> moduleTarget
        _ * moduleDetails.oldRequested >> moduleOldRequested
        1 * action.execute({ DependencyResolveDetailsInternal details ->
            details.requested == moduleOldRequested
        })
        0 * _

        def projectOldRequested = DefaultModuleVersionSelector.newSelector("org.utils", "api", "1.5")
        def projectTarget = DefaultProjectComponentSelector.newSelector(":api")
        def projectDetails = Mock(ProjectDependencySubstitutionInternal)

        when:
        substitutions.dependencySubstitutionRule.execute(projectDetails)

        then:
        _ * projectDetails.target >> projectTarget
        _ * projectDetails.oldRequested >> projectOldRequested
        1 * action.execute({ DependencyResolveDetailsInternal details ->
            details.requested == projectOldRequested
        })
        0 * _
    }

    @Unroll
    def "withModule() matches only given module: #matchingModule"() {
        given:
        def matchingModuleVersionAction = Mock(Action)
        def nonMatchingModuleVersionAction = Mock(Action)

        substitutions.withModule(matchingModule, matchingModuleVersionAction)
        substitutions.withModule(nonMatchingModule, nonMatchingModuleVersionAction)

        def moduleDetails = Mock(ModuleDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(moduleDetails)

        then:
        _ * moduleDetails.requested >> DefaultModuleComponentSelector.newSelector("org.utils", "api", "1.5")
        1 * matchingModuleVersionAction.execute(moduleDetails)
        0 * nonMatchingModuleVersionAction.execute(_)
        0 * _

        def projectDetails = Mock(ProjectDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(projectDetails)

        then:
        _ * projectDetails.requested >> DefaultProjectComponentSelector.newSelector(":api")
        0 * _

        where:
        matchingModule                    | nonMatchingModule
        "org.utils:api"                   | "org.utils:impl"
        [group: "org.utils", name: "api"] | [group: "org.utils", name: "impl"]
    }

    def "withModule() does not match projects"() {
        given:
        def action = Mock(Action)

        substitutions.withModule("org.utils:api", action)

        def projectDetails = Mock(ProjectDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(projectDetails)

        then:
        _ * projectDetails.requested >> DefaultProjectComponentSelector.newSelector(":api")
        0 * _
    }

    @Unroll
    def "withProject() matches only given project: #matchingProject"() {
        given:
        def matchingProjectAction = Mock(Action)
        def nonMatchingProjectAction = Mock(Action)

        substitutions.withProject(matchingProject, matchingProjectAction)
        substitutions.withProject(nonMatchingProject, nonMatchingProjectAction)

        def projectDetails = Mock(ProjectDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(projectDetails)

        then:
        _ * projectDetails.requested >> DefaultProjectComponentSelector.newSelector(":api")
        1 * matchingProjectAction.execute(projectDetails)
        0 * nonMatchingProjectAction.execute(_)
        0 * _

        where:
        matchingProject                                             | nonMatchingProject
        ":api"                                                      | ":impl"
        Mock(Project) { Project project -> project.path >> ":api" } | Mock(Project) { Project project -> project.path >> ":impl" }
    }

    def "withProject() does not match modules"() {
        def action = Mock(Action)
        substitutions.withProject(":api", action)
        def moduleDetails = Mock(ModuleDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(moduleDetails)

        then:
        _ * moduleDetails.requested >> DefaultModuleComponentSelector.newSelector("org.utils", "api", "1.5")
        0 * _
    }

    def "provides dependency substitution rule that orderly aggregates user specified rules"() {
        given:
        substitutions.all({ it.useVersion("1.0") } as Action)
        substitutions.all({ it.useVersion("2.0") } as Action)
        substitutions.all({ it.useVersion("3.0") } as Action)
        def details = Mock(ModuleDependencySubstitutionInternal)

        when:
        substitutions.dependencySubstitutionRule.execute(details)

        then:
        1 * details.useVersion("1.0")
        then:
        1 * details.useVersion("2.0")
        then:
        1 * details.useVersion("3.0")
        0 * details._
    }
    
    def "mutations trigger lenient validation"() {
        given:
        def validator = Mock(MutationValidator)
        substitutions.setMutationValidator(validator)
        
        when: substitutions.all(Mock(Action))
        then: 1 * validator.validateMutation(STRATEGY)
        
        when: substitutions.all(Mock(Closure))
        then: 1 * validator.validateMutation(STRATEGY)
        
        when: substitutions.withModule("org:foo", Mock(Action))
        then: 1 * validator.validateMutation(STRATEGY)
        
        when: substitutions.withModule("org:foo", Mock(Closure))
        then: 1 * validator.validateMutation(STRATEGY)
        
        when: substitutions.withProject(":foo", Mock(Action))
        then: 1 * validator.validateMutation(STRATEGY)
        
        when: substitutions.withProject(":foo", Mock(Closure))
        then: 1 * validator.validateMutation(STRATEGY)
    }

    def "mutating copy does not trigger original validator"() {
        given:
        def validator = Mock(MutationValidator)
        substitutions.setMutationValidator(validator)
        def copy = substitutions.copy()

        when:
        copy.all(Mock(Action))
        copy.all(Mock(Closure))
        copy.withModule("org:foo", Mock(Action))
        copy.withModule("org:foo", Mock(Closure))
        copy.withProject(":foo", Mock(Action))
        copy.withProject(":foo", Mock(Closure))

        then:
        0 * validator.validateMutation(_)
    }
}
