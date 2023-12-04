/*
 * Copyright 2023 the original author or authors.
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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ExampleSelfLinkProcessorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    Asciidoctor asciidoctor

    def setup() {
        tmpDir.newFolder("src", "samples")
        asciidoctor = Asciidoctor.Factory.create()
    }

    def "renders example with title adding link to self"() {
        given:
        String asciidocContent = """
.Example Title `with code`
====
some text
====
"""

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains("""<div id="ex-example-title-with-code" class="exampleblock">
<div class="title">Example 1. <a href="#ex-example-title-with-code">Example Title <code>with code</code></a></div>
<div class="content">
<div class="paragraph">
<p>some text</p>
</div>
</div>
</div>""")
    }

    def "renders example without title"() {
        given:
        String asciidocContent = """
====
some text
====
"""

        when:
        String content = asciidoctor.convert(asciidocContent, [:])

        then:
        content.contains("""<div class="exampleblock">
<div class="content">
<div class="paragraph">
<p>some text</p>
</div>
</div>
</div>""")
    }
}
