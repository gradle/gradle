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

package org.gradle.docs.asciidoctor

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
import org.asciidoctor.extension.JavaExtensionRegistry
import spock.lang.Specification

class DeprecatedLabelProcessorTest extends Specification {

    Asciidoctor asciidoctor

    def setup() {
        asciidoctor = Asciidoctor.Factory.create()
        JavaExtensionRegistry extensionRegistry = asciidoctor.javaExtensionRegistry()
        extensionRegistry.inlineMacro(DeprecatedLabelProcessor.class)
    }

    private String convertContent(String content) {
        return asciidoctor.convert(content, Options.builder().build())
    }

    def "renders deprecated-label"() {
        given:
        String asciidocContent = """
This property is deprecated-label:[]
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="deprecated-label">Deprecated</span>')
    }

    def "renders deprecated-label with surrounding text"() {
        given:
        String asciidocContent = """
This property is deprecated-label:[] and will be removed in a future version.
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="deprecated-label">Deprecated</span>')
        content.contains('This property is')
        content.contains('and will be removed in a future version.')
    }

    def "renders deprecated-label inside definition list term"() {
        given:
        String asciidocContent = """
`org.gradle.old=(true,false)` deprecated-label:[]::
This property is on its way out.

`org.gradle.new=(true,false)`::
This property replaces it.
"""

        when:
        String content = convertContent(asciidocContent)

        then:
        content.contains('<span class="deprecated-label">Deprecated</span>')
        content.contains('<code>org.gradle.old=(true,false)</code>')
        content.contains('This property is on its way out.')
    }
}
