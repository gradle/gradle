/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Problems
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import spock.lang.Shared
import spock.lang.Specification

class ProjectCoordinateCollisionReporterTest extends Specification {

    def problemSpec = Mock(ProblemSpec)
    def problemReporter = Mock(ProblemReporter)
    def problems = Stub(Problems) {
        getReporter() >> problemReporter
    }
    def reporter = new ProjectCoordinateCollisionReporter(problems)

    @Shared
    def module = DefaultModuleIdentifier.newId("org.test", "foo")
    @Shared
    def otherModule = DefaultModuleIdentifier.newId("org.test", "bar")

    @Shared
    def fooProjects = [project(":a:foo"), project(":b:foo")].toSet()
    @Shared
    def barProjects = [project(":a:bar"), project(":b:bar")].toSet()

    @Shared
    def runtimeClasspath = Describables.of("configuration ':consumer:runtimeClasspath'")
    @Shared
    def compileClasspath = Describables.of("configuration ':consumer:compileClasspath'")

    def dependencyInsight = "Run with :consumer:dependencyInsight --configuration runtimeClasspath " +
        "--dependency org.test:foo to see which project each dependency resolved to."

    def failureResolutions = Stub(ResolutionParameters.FailureResolutions) {
        forProjectCoordinateCollision(_) >> [dependencyInsight]
    }

    def "reports one problem per collision, per resolution - #condition"() {
        when:
        resolutions.each { report(collisions, it) }

        then: "no dedup of its own: the Problems API drops a problem it has already seen"
        expectedReports * problemReporter.report(_, _)

        where:
        condition                    | collisions                                          | resolutions                          | expectedReports
        'no collision was observed'  | [:]                                                 | [runtimeClasspath]                   | 0
        'the same collision twice'   | [(module): fooProjects]                             | [runtimeClasspath, runtimeClasspath] | 2
        'two configurations'         | [(module): fooProjects]                             | [runtimeClasspath, compileClasspath] | 2
        'two colliding modules'      | [(module): fooProjects, (otherModule): barProjects] | [runtimeClasspath]                   | 2
    }

    def "names the resolved configuration and the colliding projects by build tree path, sorted - #condition"() {
        given: "capture the label rather than matching on it, so a mismatch reads as a comparison"
        String reportedLabel = null

        when:
        report([(module): paths.collect { project(it) }.toSet()])

        then:
        1 * problemReporter.report(_, _) >> { ProblemId id, Action<ProblemSpec> action ->
            action.execute(problemSpec)
        }
        1 * problemSpec.contextualLabel(_) >> { String label ->
            reportedLabel = label
            problemSpec
        }
        _ * problemSpec.solution(_) >> problemSpec
        _ * problemSpec.details(_) >> problemSpec

        and:
        reportedLabel == "Configuration ':consumer:runtimeClasspath' resolves projects $expectedProjects as the same module 'org.test:foo'."

        where:
        condition            | paths                              | expectedProjects
        'two projects'       | [":b:foo", ":a:foo"]               | ":a:foo, :b:foo"
        'three projects'     | [":b:foo", ":other:foo", ":a:foo"] | ":a:foo, :b:foo, :other:foo"
        'projects of builds' | [":other:foo", ":foo"]             | ":foo, :other:foo"
    }

    def "suggests how to fix the collision, and details how to see what it did"() {
        when:
        report([(module): [":a:foo", ":b:foo"].collect { project(it) }.toSet()])

        then:
        1 * problemReporter.report(_, _) >> { ProblemId id, Action<ProblemSpec> action ->
            action.execute(problemSpec)
        }
        1 * problemSpec.contextualLabel(_) >> problemSpec
        1 * problemSpec.solution({ it.startsWith('Set a distinct `project.group`') }) >> problemSpec
        1 * problemSpec.solution({ it.startsWith('Rename one of the projects') }) >> problemSpec

        and: "the dependency insight report is context, not a fix"
        1 * problemSpec.details({ it.endsWith("\n\n" + dependencyInsight) }) >> problemSpec
    }

    def "details what a collision is when the resolution has no dependency insight report to offer"() {
        given: "a detached configuration cannot be named on the command line, and settings has no project"
        def noSuggestions = Stub(ResolutionParameters.FailureResolutions) {
            forProjectCoordinateCollision(_) >> []
        }

        when:
        reporter.report(
            [(module): [":a:foo", ":b:foo"].collect { project(it) }.toSet()],
            runtimeClasspath,
            noSuggestions
        )

        then:
        1 * problemReporter.report(_, _) >> { ProblemId id, Action<ProblemSpec> action ->
            action.execute(problemSpec)
        }
        1 * problemSpec.contextualLabel(_) >> problemSpec
        2 * problemSpec.solution(_) >> problemSpec
        1 * problemSpec.details({ it.startsWith('Multiple projects') && !it.contains(dependencyInsight) }) >> problemSpec
    }

    private void report(Map<ModuleIdentifier, Set<ProjectComponentIdentifier>> collisions, DisplayName resolution = runtimeClasspath) {
        reporter.report(collisions, resolution, failureResolutions)
    }

    private ProjectComponentIdentifier project(String buildTreePath) {
        Stub(ProjectComponentIdentifier) {
            getBuildTreePath() >> buildTreePath
        }
    }
}
