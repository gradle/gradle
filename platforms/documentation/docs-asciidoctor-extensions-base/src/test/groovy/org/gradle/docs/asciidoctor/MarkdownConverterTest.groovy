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

import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class MarkdownConverterTest extends AbstractAsciidoctorTest {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    private String toMarkdown(String asciidoc) {
        asciidoctor.convert(asciidoc, Options.builder()
            .backend(MarkdownConverter.BACKEND)
            .standalone(true)
            .safe(SafeMode.UNSAFE)
            .build())
    }

    def "converts headings, paragraphs and inline formatting"() {
        when:
        String md = toMarkdown("""
= The Java Plugin

Some *bold*, _emphasized_, and `monospaced` text.

== Usage

Apply it.
""")

        then:
        md.startsWith("# The Java Plugin\n")
        md.contains("Some **bold**, *emphasized*, and `monospaced` text.")
        md.contains("## Usage\n\nApply it.")
    }

    def "keeps explicit ids as anchors and points cross-references at Markdown pages"() {
        when:
        String md = toMarkdown("""
[[java_plugin]]
= The Java Plugin

[[sec:tasks]]
== Tasks

See <<sec:tasks,the tasks>> and <<building_java_projects.adoc#building_java_projects,Building Java Projects>>.
""")

        then:
        md.contains('<a id="java_plugin"></a>\n# The Java Plugin')
        md.contains('<a id="sec:tasks"></a>\n## Tasks')
        md.contains('[the tasks](#sec:tasks)')
        md.contains('[Building Java Projects](building_java_projects.md#building_java_projects)')
    }

    def "uses the target title for cross-references without text"() {
        when:
        String md = toMarkdown("""
= Doc

[[sec:tasks]]
== Tasks

[[sec:custom,Custom Reftext]]
== Configuration

See <<sec:tasks>>, <<sec:custom>>, <<missing>> and xref:other_page.adoc[].
""")

        then:
        md.contains('[Tasks](#sec:tasks)')
        md.contains('[Custom Reftext](#sec:custom)')
        md.contains('[[missing]](#missing)')
        md.contains('[other_page.md](other_page.md)')
    }

    def "resolves attributes in link targets"() {
        when:
        String md = toMarkdown("""
= Doc
:javadocPath: ../javadoc

Use the link:{javadocPath}/org/gradle/api/Project.html[Project] API.
""")

        then:
        md.contains('[Project](../javadoc/org/gradle/api/Project.html)')
    }

    def "keeps only the Kotlin DSL variant of a multi-language sample"() {
        given:
        tmpDir.newFolder("sample", "kotlin")
        tmpDir.newFolder("sample", "groovy")
        tmpDir.newFile("sample/kotlin/build.gradle.kts") << 'plugins { id("java") }\n'
        tmpDir.newFile("sample/groovy/build.gradle") << "plugins { id 'java' }\n"

        when:
        String md = toMarkdown("""
= Doc
:samples-dir: ${tmpDir.root.canonicalPath}

====
include::sample[dir="sample/kotlin",files="build.gradle.kts[]"]
include::sample[dir="sample/groovy",files="build.gradle[]"]
====
""")

        then:
        md.contains('```kotlin\nplugins { id("java") }\n```')
        !md.contains('```groovy')
        !md.contains("id 'java'")
    }

    def "keeps a Groovy DSL sample that has no Kotlin DSL variant"() {
        given:
        tmpDir.newFolder("sample", "groovy")
        tmpDir.newFile("sample/groovy/build.gradle") << "plugins { id 'java' }\n"

        when:
        String md = toMarkdown("""
= Doc
:samples-dir: ${tmpDir.root.canonicalPath}

====
include::sample[dir="sample/groovy",files="build.gradle[]"]
====
""")

        then:
        md.contains("```groovy\nplugins { id 'java' }\n```")
    }

    def "keeps only the Kotlin DSL variant of a hand-written multi-language sample"() {
        when:
        String md = toMarkdown("""
= Doc

====
[.multi-language-sample]
=====
.build.gradle.kts
[source,kotlin]
----
plugins { id("java") }
----
=====
[.multi-language-sample]
=====
.build.gradle
[source, groovy]
----
plugins { id 'java' }
----
=====
====
""")

        then:
        md.contains('**build.gradle.kts**\n\n```kotlin\nplugins { id("java") }\n```')
        !md.contains('```groovy')
    }

    def "keeps a Groovy code block that has no Kotlin variant"() {
        when:
        String md = toMarkdown("""
= Doc

.build.gradle
[source,groovy]
----
ant.echo(message: 'hello')
----
""")

        then:
        md.contains("```groovy\nant.echo(message: 'hello')\n```")
    }

    def "keeps only the Kotlin DSL variant of multi-language text"() {
        when:
        String md = toMarkdown("""
= Doc

====
[.multi-language-text.lang-kotlin]
=====
Kotlin text.
=====
[.multi-language-text.lang-groovy]
=====
Groovy text.
=====
====
""")

        then:
        md.contains("Kotlin text.")
        !md.contains("Groovy text.")
    }

    def "writes code blocks with their language and without callout markers"() {
        when:
        String md = toMarkdown("""
= Doc

[source,kotlin]
----
tasks.register("hello") { // <1>
    println("<b>hello</b>") <2>
}
----
<1> Registers a task
<2> Prints
""")

        then:
        md.contains('```kotlin\ntasks.register("hello") {\n    println("<b>hello</b>")\n}\n```')
        md.contains("1. Registers a task\n2. Prints")
    }

    def "converts admonitions, lists and tables"() {
        when:
        String md = toMarkdown("""
= Doc

NOTE: Read this & that.

* one
* two
** nested

|===
|Name |Description

|`implementation`
|Implementation only
|===
""")

        then:
        md.contains("> **Note**\n>\n> Read this & that.")
        md.contains("* one\n* two")
        md.contains("* nested")
        md.contains("| Name | Description |\n| --- | --- |\n| `implementation` | Implementation only |")
    }

    def "collects footnotes at the end of the page"() {
        when:
        String md = toMarkdown("""
= Doc

Groovy can execute Strings.footnote:[In Groovy you can execute Strings.]
""")

        then:
        md.contains("Groovy can execute Strings.[^1]")
        md.trim().endsWith("[^1]: In Groovy you can execute Strings.")
    }

    def "keeps footnotes that precede an AsciiDoc table cell"() {
        when:
        String md = toMarkdown("""
= Doc

Groovy can execute Strings.footnote:[In Groovy you can execute Strings.]

|===
a| Some *nested* content.
|===
""")

        then:
        md.contains("| Some **nested** content. |")
        md.trim().endsWith("[^1]: In Groovy you can execute Strings.")
    }

    def "resolves attributes in code blocks that enable attribute substitution"() {
        when:
        String md = toMarkdown("""
= Doc
:gradleVersion: 9.6.0

[source,properties,subs="attributes"]
----
distributionUrl=https\\\\://services.gradle.org/distributions/gradle-{gradleVersion}-bin.zip
----

[source,kotlin,subs="+attributes"]
----
version = "{gradleVersion}" // <1>
----
<1> The version

[source,text]
----
No substitution here: {gradleVersion}
----
""")

        then:
        md.contains('```properties\ndistributionUrl=https\\\\://services.gradle.org/distributions/gradle-9.6.0-bin.zip\n```')
        md.contains('```kotlin\nversion = "9.6.0"\n```')
        md.contains('```text\nNo substitution here: {gradleVersion}\n```')
    }

    def "renders Gradle's label macros as plain text"() {
        when:
        String md = toMarkdown("""
= Doc

This API is incubating-label:[] and available since-gradle-label:9.1.0[].
""")

        then:
        md.contains("This API is *Incubating* and available *Since 9.1.0*.")
        !md.contains("<span")
    }

    def "drops passthrough blocks"() {
        when:
        String md = toMarkdown("""
= Doc

Before.

++++
<div style="position:relative;padding-top:56.25%;">
  <iframe src="https://www.youtube-nocookie.com/embed/abc"></iframe>
</div>
++++

++++
<script>
const currentAnchor = window.location.hash.substring(1);
</script>
++++

After.
""")

        then:
        md.contains("Before.\n\nAfter.")
        !md.contains("<div")
        !md.contains("<iframe")
        !md.contains("<script")
    }

    def "HTML-only extensions leave Markdown untouched"() {
        when:
        String md = toMarkdown("""
= Doc

[source,bash]
----
./gradlew build
----
""")

        then:
        !md.contains("<script")
        !md.contains("<style")
        !md.contains("<meta")
    }
}
