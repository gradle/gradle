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
import org.asciidoctor.Options
import spock.lang.Specification
import spock.lang.TempDir

class JavadocLinkInlineMacroTest extends Specification {
    @TempDir
    File tempDir

    def javadocFile

    def setup() {
        javadocFile = new File(tempDir, "org/gradle/api/artifacts/dsl/ComponentMetadataHandler.html")
        javadocFile.parentFile.mkdirs()
        javadocFile.text = """
<section class="detail" id="all(java.lang.Class,org.gradle.api.Action)">
<h3>all <a href="#all(java.lang.Class,org.gradle.api.Action)" class="anchor-link" aria-label="Link to this section"><img src="../../../../../link.svg" alt="Link icon" tabindex="0" width="16" height="16"></a></h3>
<div class="member-signature"><span class="return-type"><a href="ComponentMetadataHandler.html" title="interface in org.gradle.api.artifacts.dsl">ComponentMetadataHandler</a></span>&nbsp;<span class="element-name">all</span><wbr><span class="parameters">(<a href="https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html" title="class or interface in java.lang" class="external-link">Class</a>&lt;? extends <a href="../ComponentMetadataRule.html" title="interface in org.gradle.api.artifacts">ComponentMetadataRule</a>&gt;&nbsp;rule,
 <a href="../../Action.html" title="interface in org.gradle.api">Action</a>&lt;? super <a href="../../ActionConfiguration.html" title="interface in org.gradle.api">ActionConfiguration</a>&gt;&nbsp;configureAction)</span></div>
<div class="block">Adds a class based rule that may modify the metadata of any resolved software component.
 The rule itself is configured by the provided configure action.</div>
<dl class="notes">
<dt>Parameters:</dt>
<dd><code>rule</code> - the rule to be added</dd>
<dd><code>configureAction</code> - the rule configuration</dd>
<dt>Returns:</dt>
<dd>this</dd>
<dt>Since:</dt>
<dd>4.9</dd>
</dl>
</section>
"""
    }

    def "sanity check parser for class and method"() {
        expect:
        def result = JavadocLinkInlineMacro.parse("org.gradle.api.artifacts.dsl.ComponentMetadataHandler#all(java.lang.Class,org.gradle.api.Action)")
        result instanceof JavadocLinkInlineMacro.ClassAndMethod
        verifyAll((JavadocLinkInlineMacro.ClassAndMethod)result) {
            it.classInfo().className() == "org/gradle/api/artifacts/dsl/ComponentMetadataHandler"
            it.classInfo().simpleName() == "ComponentMetadataHandler"
            it.methodName() == "all"
            it.args() as List == ["java.lang.Class", "org.gradle.api.Action"]
            it.simpleArgs() as List == ["Class", "Action"]
        }
    }

    def "sanity check parser for class only"() {
        expect:
        def result = JavadocLinkInlineMacro.parse("org.gradle.api.artifacts.dsl.ComponentMetadataHandler")
        result instanceof JavadocLinkInlineMacro.ClassOnly
        verifyAll((JavadocLinkInlineMacro.ClassOnly)result) {
            it.className() == "org/gradle/api/artifacts/dsl/ComponentMetadataHandler"
            it.simpleName() == "ComponentMetadataHandler"
        }
    }

    def "check for errors"() {
        when:
        JavadocLinkInlineMacro.parse("ComponentMetadataHandler")
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Need to fully qualify all types. 'ComponentMetadataHandler' does not fully qualify type 'ComponentMetadataHandler'."

        when:
        JavadocLinkInlineMacro.parse("org/gradle/api/ComponentMetadataHandler")
        then:
        e = thrown(IllegalArgumentException)
        e.message == "Separate packages with '.' and not '/'. 'org/gradle/api/ComponentMetadataHandler' uses '/' for 'org/gradle/api/ComponentMetadataHandler'."

        when:
        JavadocLinkInlineMacro.parse("org.gradle.api.artifacts.dsl.ComponentMetadataHandler#method(Foo)")
        then:
        e = thrown(IllegalArgumentException)
        e.message == "Need to fully qualify all types. 'org.gradle.api.artifacts.dsl.ComponentMetadataHandler#method(Foo)' does not fully qualify type 'Foo'."

        when:
        JavadocLinkInlineMacro.parse("org.gradle.api.artifacts.dsl.ComponentMetadataHandler#nonsense#asdf")
        then:
        e = thrown(IllegalArgumentException)
        e.message == "don't know how to parse org.gradle.api.artifacts.dsl.ComponentMetadataHandler#nonsense#asdf"
    }

    def "renders link to method"() {
        given:
        String content = """
javadoc:org.gradle.api.artifacts.dsl.ComponentMetadataHandler#all(java.lang.Class,org.gradle.api.Action)[]
"""

        when:
        String rendered = convert(content)

        then:
        rendered.contains("""<div class="paragraph">
<p><a href="${javadocFile.absolutePath}#all(java.lang.Class,org.gradle.api.Action)"><code>ComponentMetadataHandler.all(Class,Action)</code></a></p>
</div>""")
    }

    def "renders link to class"() {
        given:
        String content = """
javadoc:org.gradle.api.artifacts.dsl.ComponentMetadataHandler[]
"""

        when:
        String rendered = convert(content)

        then:
        rendered.contains("""<div class="paragraph">
<p><a href="${javadocFile.absolutePath}"><code>ComponentMetadataHandler</code></a></p>
</div>""")
    }

    def "renders link to class with alt-text as positional argument"() {
        given:

        String content = """
javadoc:org.gradle.api.artifacts.dsl.ComponentMetadataHandler[Not code]
"""

        when:
        String rendered = convert(content)

        then:
        rendered.contains("""<div class="paragraph">
<p><a href="${javadocFile.absolutePath}">Not code</a></p>
</div>""")
    }

    def "renders link to class with alt-text"() {
        given:

        String content = """
javadoc:org.gradle.api.artifacts.dsl.ComponentMetadataHandler[alt-text="Not code"]
"""

        when:
        String rendered = convert(content)

        then:
        rendered.contains("""<div class="paragraph">
<p><a href="${javadocFile.absolutePath}">Not code</a></p>
</div>""")
    }

    private String convert(String content) {
        Asciidoctor asciidoctor = Asciidoctor.Factory.create()
        def actualContent = """
:javadocPath: ${tempDir.absolutePath}
$content
"""
        asciidoctor.convert(actualContent, Options.builder().build())
    }
}
