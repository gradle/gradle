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
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import spock.lang.Specification
import spock.lang.Unroll

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
        def moduleDetails = Mock(ModuleDependencySubstitutionInternal)

        when:
        substitutions.dependencySubstitutionRule.execute(moduleDetails)

        then:
        _ * moduleDetails.oldRequested >> moduleOldRequested
        1 * action.execute({ DependencyResolveDetailsInternal details ->
            details.requested == moduleOldRequested
        })
        0 * _

        def projectOldRequested = DefaultModuleVersionSelector.newSelector("org.utils", "api", "1.5")
        def projectDetails = Mock(ProjectDependencySubstitutionInternal)

        when:
        substitutions.dependencySubstitutionRule.execute(projectDetails)

        then:
        _ * projectDetails.oldRequested >> projectOldRequested
        1 * action.execute({ DependencyResolveDetailsInternal details ->
            details.requested == projectOldRequested
        })
        0 * _
    }

    def "eachModule() matches only modules"() {
        given:
        def action = Mock(Action)
        substitutions.eachModule(action)

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

    def "eachProject() matches only projects"() {
        given:
        def action = Mock(Action)
        substitutions.eachProject(action)

        def projectDetails = Mock(ProjectDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(projectDetails)

        then:
        _ * projectDetails.requested >> DefaultProjectComponentSelector.newSelector(":api")
        1 * action.execute(projectDetails)
        0 * _

        def moduleDetails = Mock(ModuleDependencySubstitution)

        when:
        substitutions.dependencySubstitutionRule.execute(moduleDetails)

        then:
        _ * moduleDetails.requested >> DefaultModuleComponentSelector.newSelector("org.utils", "api", "1.5")
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
        substitutions.eachModule({ it.useVersion("1.0") } as Action)
        substitutions.eachModule({ it.useVersion("2.0") } as Action)
        substitutions.eachModule({ it.useVersion("3.0") } as Action)
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
}
