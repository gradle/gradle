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
import spock.lang.Specification

class SinceGradleLabelProcessorTest extends Specification {

    Asciidoctor asciidoctor

    def setup() {
        asciidoctor = Asciidoctor.Factory.create()
    }

    private String convertContent(String content) {
        return asciidoctor.convert(content, Options.builder().build())
    }

    def "renders since-gradle-label with version"() {
        given:
        String asciidocContent = """
This feature is available since-gradle-label:9.1.0[]
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="since-gradle-label">Since 9.1.0</span>')
    }

    def "handles empty version by not rendering span"() {
        given:
        String asciidocContent = """
This feature is available since-gradle-label:[]
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        !content.contains('<span class="since-gradle-label">')
        content.contains('This feature is available')
    }

    def "handles null version by not rendering span"() {
        given:
        String asciidocContent = """
This feature is available since-gradle-label:[]
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        !content.contains('<span class="since-gradle-label">')
    }

    def "renders since-gradle-label with different version formats"() {
        given:
        String asciidocContent = """
Feature A is available since-gradle-label:8.5[]
Feature B is available since-gradle-label:9.0.0[]
Feature C is available since-gradle-label:10.1.2-rc-1[]
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="since-gradle-label">Since 8.5</span>')
        content.contains('<span class="since-gradle-label">Since 9.0.0</span>')
        content.contains('<span class="since-gradle-label">Since 10.1.2-rc-1</span>')
    }

    def "renders since-gradle-label inside definition list term"() {
        given:
        String asciidocContent = """
`publish` since-gradle-label:9.1.0[]::
This task publishes the artifact to the repository.

`build`::
This task builds the project.
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="since-gradle-label">Since 9.1.0</span>')
        content.contains('<code>publish</code>')
        content.contains('This task publishes the artifact to the repository.')
    }
}
