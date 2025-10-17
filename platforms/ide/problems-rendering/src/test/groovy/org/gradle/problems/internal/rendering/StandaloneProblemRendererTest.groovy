/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.problems.internal.rendering

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultProblemBuilder
import org.gradle.api.problems.internal.InternalProblem
import org.gradle.api.problems.internal.InternalProblemBuilder
import org.gradle.api.problems.internal.IsolatableToBytesSerializer
import org.gradle.api.problems.internal.ProblemsInfrastructure
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

class StandaloneProblemRendererTest extends Specification {

    private StringWriter output
    private StandaloneProblemRenderer renderer

    def setup() {
        output = new StringWriter()
        renderer = ProblemRendererFactory.standaloneProblemRenderer(new PrintWriter(output))
    }

    def "render problem with no group and id display name"(String displayName) {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(
                createId(
                    "sample-problems",
                    displayName,
                    "prototype-project",
                    displayName
            ))
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == 'Problem found: sample-problems:prototype-project'

        where:
        displayName << [null, '']
    }

    def "render problem with no group display name"(String displayName) {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            id(
                createId(
                    "sample-problems",
                    displayName,
                    "prototype-project",
                    "Project is a prototype"
                )
            )
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == 'Problem found: Project is a prototype (id: sample-problems:prototype-project)'

        where:
        displayName << [null, '']
    }

    def "render problem with no id display name"(String displayName) {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(
                createId(
                    "sample-problems",
                    "Sample Problems",
                    "prototype-project",
                    displayName
            )
            )
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == 'Problem found: sample-problems:prototype-project'

        where:
        displayName << [null, '']
    }

    def "render problem with id only"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == 'Problem found: Project is a prototype (id: sample-problems:prototype-project)'
    }

    def "render problem with multiline id displayNames"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(
                createId(
                    "sample-problems",
                    "Sample\nProblems",
                    "prototype-project",
                    "Project\nis a prototype"
                )
            )
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == 'Problem found: Project is a prototype (id: sample-problems:prototype-project)'
    }

    def "render problem with contextual message"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
              .contextualLabel("This is a prototype and not a guideline for modeling real-life projects")
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == '''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
        '''.strip()
    }

    def "contextual message falls back to exception message"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
                .withException(new Exception("This is a prototype and not a guideline for modeling real-life projects"))
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == '''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
        '''.strip()
    }

    def "render problem with contextual message and details"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not a guideline for modeling real-life projects")
                .details("Complex build logic like the Problems API usage should integrated into plugins")
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == '''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API usage should integrated into plugins
        '''.strip()
    }


    def "details are rendered as a fallback to contextual message"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
                .details("Complex build logic like the Problems API usage should integrated into plugins")
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == '''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  Complex build logic like the Problems API usage should integrated into plugins
        '''.strip()
    }

    def "render solution and location"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not a guideline for modeling real-life projects")
                .details("Complex build logic like the Problems API usage should integrated into plugins")
                .solution("Look up the samples index for real-life examples")
                .lineInFileLocation("/path/to/script", 20)
                .details("Complex build logic like the Problems API usage should integrated into plugins")
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == '''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API usage should integrated into plugins
    Solution: Look up the samples index for real-life examples
    Location: /path/to/script line 20
        '''.strip()
    }

    def "render multiple solution land location"() {
        given:
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not a guideline for modeling real-life projects")
                .details("Complex build logic like the Problems API usage should integrated into plugins")
                .solution("Look up the samples index for real-life examples")
                .solution("Or read the documentation on the Gradle website")
                .lineInFileLocation("/path/to/script", 20)
                .lineInFileLocation("/path/to/alternative", 30)
                .details("Complex build logic like the Problems API usage should integrated into plugins")
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == '''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API usage should integrated into plugins
    Solution: Look up the samples index for real-life examples
    Solution: Or read the documentation on the Gradle website
    Location: /path/to/script line 20
    Location: /path/to/alternative line 30
        '''.strip()
    }

    def "render multiline messages for all fields possible"() {
        def problem = createProblem { InternalProblemBuilder spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not\na guideline for modeling real-life projects")
                .details("Complex build logic like the Problems API\nusage should integrated into plugins")
                .solution("Look up the samples index for\nreal-life examples")
                .details("Complex build logic like the Problems API\nusage should integrated into plugins")
        }

        when:
        renderer.render(problem)

        then:
        renderedProblem == '''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not
  a guideline for modeling real-life projects
    Complex build logic like the Problems API
    usage should integrated into plugins
    Solution: Look up the samples index for
              real-life examples
        '''.strip()
    }

    ProblemId createId(String groupName = "sample-problems", String groupDisplayName = "Sample Problems", String idName = "prototype-project", String idDisplayName = "Project is a prototype") {
        ProblemGroup group = ProblemGroup.create(groupName, groupDisplayName)
        ProblemId.create(idName, idDisplayName, group)
    }

    InternalProblem createProblem(@DelegatesTo(value = InternalProblemBuilder) Closure spec) {
        InternalProblemBuilder builder = createProblemBuilder()
        spec.setDelegate(builder)
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec.call(builder)
        builder.build()
    }

    def createProblemBuilder = { ->
        new DefaultProblemBuilder(
            new ProblemsInfrastructure(
                new AdditionalDataBuilderFactory(),
                Mock(Instantiator),
                Mock(PayloadSerializer),
                Mock(IsolatableFactory),
                Mock(IsolatableToBytesSerializer),
                Mock(ProblemStream)
            )
        )
    }

    def getRenderedProblem() {
        return output.toString()
    }
}
