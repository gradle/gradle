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
import org.gradle.api.problems.internal.DefaultAdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultProblemBuilder
import org.gradle.api.problems.internal.DefaultProblemGroup
import spock.lang.Specification

class ProblemRendererTest extends Specification {

    private StringWriter stringWriter
    private PrintWriter writer
    private ProblemRenderer renderer

    def setup() {
        stringWriter = new StringWriter()
        writer = new PrintWriter(stringWriter)
        renderer = new ProblemRenderer(writer)
    }

    def "individual problem header is correct when only group display name is present"() {
        given:
        def problem = new DefaultProblemBuilder(new DefaultAdditionalDataBuilderFactory())
            .id("test-id", "test-id-display-name", level1Group)
            .build()

        when:
        renderer.render(problem)

        then:
        renderedTextLines[0] == "  test-id-display-name"
    }

    def "individual problem header is correct when contextual label is present"() {
        given:
        def problem = new DefaultProblemBuilder(new DefaultAdditionalDataBuilderFactory())
            .id("test-id", "display-name", level1Group)
            .contextualLabel("contextual-label")
            .build()

        when:
        renderer.render(problem)

        then:
        renderedTextLines[0] == "  contextual-label"
    }

    def "individual problem with details are displayed"() {
        given:
        def problem = new DefaultProblemBuilder(new DefaultAdditionalDataBuilderFactory())
            .id("id", "display-name", level1Group)
            .details("details")
            .build()

        when:
        renderer.render(problem)

        then:
        renderedTextLines[1] == "    details"
    }

    def "individual problem with multiline details are displayed and indented correctly"() {
        given:
        def problem = new DefaultProblemBuilder(new DefaultAdditionalDataBuilderFactory())
            .id("id", "display-name", level1Group)
            .details("details:1\ndetails:2")
            .build()

        when:
        renderer.render(problem)

        then:
        renderedTextLines[1] == "    details:1"
        renderedTextLines[2] == "    details:2"
    }

    def getRenderedText() {
        return stringWriter.toString()
    }

    def getRenderedTextLines() {
        return renderedText.split('\r?\n')
    }

    private static ProblemGroup getLevel0Group() {
        return new DefaultProblemGroup("test-group-0", "Test group level 0", null);
    }

    private static ProblemGroup getLevel1Group() {
        return new DefaultProblemGroup("test-group-1", "Test group level 1", getLevel0Group());
    }
}
