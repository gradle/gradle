/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.docs.asciidoctor

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
import org.asciidoctor.extension.JavaExtensionRegistry
import spock.lang.Specification

class IncubatingLabelProcessorTest extends Specification {

    Asciidoctor asciidoctor

    def setup() {
        asciidoctor = Asciidoctor.Factory.create()
        JavaExtensionRegistry extensionRegistry = asciidoctor.javaExtensionRegistry()
        extensionRegistry.inlineMacro(IncubatingLabelProcessor.class)
    }

    private String convertContent(String content) {
        return asciidoctor.convert(content, Options.builder().build())
    }

    def "renders incubating-label"() {
        given:
        String asciidocContent = """
This feature is incubating-label:[]
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="incubating-label">Incubating</span>')
    }

    def "renders incubating-label with surrounding text"() {
        given:
        String asciidocContent = """
This feature is incubating-label:[] and may change in future versions.
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="incubating-label">Incubating</span>')
        content.contains('This feature is')
        content.contains('and may change in future versions.')
    }

    def "renders multiple incubating-labels"() {
        given:
        String asciidocContent = """
Feature A is incubating-label:[]
Feature B is also incubating-label:[]
Feature C is stable
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.count('<span class="incubating-label">Incubating</span>') == 2
        content.contains('Feature A is')
        content.contains('Feature B is also')
        content.contains('Feature C is stable')
    }

    def "renders incubating-label inside definition list term"() {
        given:
        String asciidocContent = """
`publish` incubating-label:[]::
This task publishes the artifact to the repository.

`build`::
This task builds the project.
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="incubating-label">Incubating</span>')
        content.contains('<code>publish</code>')
        content.contains('This task publishes the artifact to the repository.')
    }

    def "does not render incubating-label in code blocks"() {
        given:
        String asciidocContent = """
[source,gradle]
----
// This feature is incubating-label:[]
configurations {
    customConfig
}
----
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        !content.contains('<span class="incubating-label">Incubating</span>')
        content.contains('incubating-label:[]')
    }

    def "renders incubating-label in table cells"() {
        given:
        String asciidocContent = """
|===
|Feature |Status

|Feature A
|incubating-label:[]

|Feature B
|Stable
|===
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="incubating-label">Incubating</span>')
        content.contains('Feature A')
        content.contains('Feature B')
        content.contains('Stable')
    }
}