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
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultProblemBuilder
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.IsolatableToBytesSerializer
import org.gradle.api.problems.internal.ProblemsInfrastructure
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Issue
import spock.lang.Specification

class GroupingProblemWriterTest extends Specification {

    private Writer writer
    private ProblemWriter problemWriter

    def setup() {
        writer = new StringWriter()
        problemWriter = ProblemWriter.grouping()
    }

    def "individual problem header is correct when only group display name is present"() {
        given:
        def problem = createProblemBuilder()
            .id("test-id", "test-id-display-name", level1Group)
            .build()

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == 'test-id-display-name'
    }

    DefaultProblemBuilder createProblemBuilder() {
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

    def "individual problem header is correct when contextual label is present"() {
        given:
        def problem = createProblemBuilder()
            .id("test-id", "display-name", level1Group)
            .contextualLabel("contextual-label")
            .build()

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
display-name
  contextual-label
        ''')
    }

    def "individual problem with details are displayed"() {
        given:
        def problem = createProblemBuilder()
            .id("id", "display-name", level1Group)
            .details("details")
            .build()

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
display-name
  details
        ''')
    }

    def "individual problem with multiline details are displayed and indented correctly"() {
        given:
        def problem = createProblemBuilder()
            .id("id", "display-name", level1Group)
            .details("details:1${System.lineSeparator()}details:2")
            .build()

        when:
        problemWriter.write(problem, writer)

        then:
        renderedProblem == denormalizeAndStrip('''
display-name
  details:1
  details:2
        ''')
    }

    @Issue("https://github.com/gradle/gradle/issues/32016")
    def "reports are properly separated"() {
        given:
        def problem1 = createProblemBuilder()
            .id("id", "display-name", level1Group)
            .details("details:1${System.lineSeparator()}details:2")
            .build()
        def problem2 = createProblemBuilder()
            .id("id", "display-name", level1Group)
            .contextualLabel("Some context for one problem")
            .details("details:1${System.lineSeparator()}details:2")
            .build()

        when:
        problemWriter.write([problem1, problem2], writer)

        then:
        renderedProblem == denormalizeAndStrip('''
display-name
  details:1
  details:2
display-name
  Some context for one problem
    details:1
    details:2
        ''')
    }

    @Issue("https://github.com/gradle/gradle/issues/32016")
    def "java compilation reports are properly separated"() {
        given:
        def problem1 = createProblemBuilder()
            .id("id", "display-name", GradleCoreProblemGroup.compilation().java())
            .details("Unused variable a in line 10")
            .build()
        def problem2 = createProblemBuilder()
            .id("id", "display-name", GradleCoreProblemGroup.compilation().java())
            .details("Unused variable a in line 20")
            .build()

        when:
        problemWriter.write([problem1, problem2], writer)

        then:
        renderedProblem == denormalizeAndStrip('''
Unused variable a in line 10
Unused variable a in line 20
        ''')
    }

    def getRenderedProblem() {
        return writer.toString()
    }

    private static ProblemGroup getLevel0Group() {
        return ProblemGroup.create("test-group-0", "Test group level 0", null);
    }

    private static ProblemGroup getLevel1Group() {
        return ProblemGroup.create("test-group-1", "Test group level 1", getLevel0Group());
    }

    private static String denormalizeAndStrip(String text) {
        // the renderers use platform-specific line endings, so we need to denormalize the expected strings before comparing
        text.denormalize().strip()
    }
}
