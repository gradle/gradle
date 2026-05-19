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
import org.gradle.api.problems.internal.IsolatableToBytesSerializer
import org.gradle.api.problems.internal.ProblemBuilderInternal
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemsInfrastructure
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

class SimpleProblemWriterTest extends Specification {

    private Writer writer
    private ProblemWriter problemWriter

    def setup() {
        writer = new StringWriter()
        problemWriter = ProblemWriter.simple()
    }

    def "render problem with id only"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == 'Problem found: Project is a prototype (id: sample-problems:prototype-project)'
    }

    def "render problem with multiline id displayNames"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(
                createId(
                    "sample-problems",
                    "Sample\nProblems",
                    "prototype-project",
                    "Project\n is a prototype"
                )
            )
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == 'Problem found: Project is a prototype (id: sample-problems:prototype-project)'
    }

    def "render problem with contextual message"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
              .contextualLabel("This is a prototype and not a guideline for modeling real-life projects")
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
        ''')
    }

    def "contextual message falls back to exception message"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
                .withException(new Exception("This is a prototype and not a guideline for modeling real-life projects"))
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
        ''')
    }

    def "render problem with contextual message and details"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not a guideline for modeling real-life projects")
                .details("Complex build logic like the Problems API usage should integrated into plugins")
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API usage should integrated into plugins
        ''')
    }


    def "details are rendered as a fallback to contextual message"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
                .details("Complex build logic like the Problems API usage should integrated into plugins")
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  Complex build logic like the Problems API usage should integrated into plugins
        ''')
    }

    def "render solution and location"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not a guideline for modeling real-life projects")
                .details("Complex build logic like the Problems API usage should integrated into plugins")
                .solution("Look up the samples index for real-life examples")
                .lineInFileLocation("/path/to/script", 20)
                .details("Complex build logic like the Problems API usage should integrated into plugins")
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API usage should integrated into plugins
    Location: /path/to/script line 20
    Possible solution: Look up the samples index for real-life examples.
        ''')
    }

    def "render multiple solution land location"() {
        given:
        def problem = createProblem { ProblemBuilderInternal spec ->
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
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API usage should integrated into plugins
    Location: /path/to/script line 20
    Location: /path/to/alternative line 30
    Possible solutions:
      1. Look up the samples index for real-life examples.
      2. Or read the documentation on the Gradle website.
        ''')
    }

    def "render multiline messages for all fields possible"() {
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not${System.lineSeparator()}a guideline for modeling real-life projects") // API enforces a single line for contextual label
                .details("Complex build logic like the Problems API${System.lineSeparator()}usage should integrated into plugins")
                .solution("Look up the samples index for${System.lineSeparator()}real-life examples")
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API
    usage should integrated into plugins
    Possible solution: Look up the samples index for
                       real-life examples.
        ''')
    }

    def "render multiline messages with multiple solutions"() {
        def problem = createProblem { ProblemBuilderInternal spec ->
            spec.id(createId())
                .contextualLabel("This is a prototype and not${System.lineSeparator()}a guideline for modeling real-life projects") // API enforces a single line for contextual label
                .details("Complex build logic like the Problems API${System.lineSeparator()}usage should integrated into plugins")
                .solution("Look up the samples index for${System.lineSeparator()}real-life examples")
                .solution("Another interesting${System.lineSeparator()}alternative solution")
                .solution("3rd solution")
                .solution("4th solution")
                .solution("5th solution")
                .solution("6th solution")
                .solution("7th solution")
                .solution("8th solution")
                .solution("9th solution")
                .solution("10th solution${System.lineSeparator()}with some content")
        }

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Problem found: Project is a prototype (id: sample-problems:prototype-project)
  This is a prototype and not a guideline for modeling real-life projects
    Complex build logic like the Problems API
    usage should integrated into plugins
    Possible solutions:
      1. Look up the samples index for
         real-life examples.
      2. Another interesting
         alternative solution.
      3. 3rd solution.
      4. 4th solution.
      5. 5th solution.
      6. 6th solution.
      7. 7th solution.
      8. 8th solution.
      9. 9th solution.
      10. 10th solution
          with some content.
        ''')
    }

    ProblemId createId(String groupName = "sample-problems", String groupDisplayName = "Sample Problems", String idName = "prototype-project", String idDisplayName = "Project is a prototype") {
        ProblemGroup group = ProblemGroup.create(groupName, groupDisplayName)
        ProblemId.create(idName, idDisplayName, group)
    }

    ProblemInternal createProblem(@DelegatesTo(value = ProblemBuilderInternal) Closure spec) {
        ProblemBuilderInternal builder = createProblemBuilder()
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
        return writer.toString()
    }

    private static String denormalizeAndStrip(String text) {
        // the renderers use platform-specific line endings, so we need to denormalize the expected strings before comparing
        text.denormalize().strip()
    }
}
