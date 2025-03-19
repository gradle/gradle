/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild.docs

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class FindBrokenInternalLinksTest extends Specification {
    @TempDir
    private File projectDir
    private File docsRoot
    private File samplesRoot
    private File sampleDoc
    private File sampleSampleDoc
    private File releaseNotes
    private File linkErrors

    private setup() {
        docsRoot = new File(projectDir, "docsRoot")
        new File(docsRoot, 'javadoc').mkdirs()
        sampleDoc = new File(docsRoot, "sample.adoc")

        new File(projectDir,"build/working/samples/docs").mkdirs()
        sampleSampleDoc = new File(projectDir, "build/working/samples/docs/sample_sample.adoc")

        new File(projectDir,"build/working/release-notes").mkdirs()
        releaseNotes = new File(projectDir, "build/working/release-notes/raw.html")

        linkErrors = new File(projectDir, "build/reports/dead-internal-links.txt")


        new File(projectDir, "build.gradle") << """
            plugins {
                id 'java'
                id 'checkstyle'
                id 'gradlebuild.documentation'
            }

            gradleDocumentation {
                javadocs {
                    javaApi = project.uri("https://docs.oracle.com/javase/8/docs/api")
                    groovyApi = project.uri("https://docs.groovy-lang.org/docs/groovy-3/html/gapi")
                }
            }

            tasks.register('assembleSamples')

            javadocAll {
                enabled = false
            }

            tasks.named('checkDeadInternalLinks').configure {
                documentationRoot = project.layout.projectDirectory.dir('docsRoot')
                javadocRoot = documentationRoot.dir('javadoc')
                releaseNotesFile = project.layout.buildDirectory.file('working/release-notes/raw.html')
                samplesRoot = project.layout.buildDirectory.dir('working/samples/docs');
            }
        """
    }

    def "finds broken section links"() {
        given:
        sampleDoc << """
=== Dead Section Links
This section doesn't exist: <<missing_section>>
Also see this one, which is another dead link: <<other_missing_section>>
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        and:
        sampleSampleDoc << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadSectionLinks(sampleDoc, "missing_section", "other_missing_section")
    }

    def "finds broken sample links"() {
        given:
        sampleDoc << """
Nothing to write about
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        and:
        sampleSampleDoc << """
This doesn't exist either link:{userManualPath}/no_sample.html.
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks()
    }

    def "validates present section links"() {
        given:
        sampleDoc << """
[[prior_section]]
Text

=== Valid Section Links
This section comes earlier: <<prior_section>>
This section comes later: <<subsequent_section>>

[[subsequent_section]]
More text
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        and:
        sampleSampleDoc << """
This sample does exist <<sample_sample.adoc,sample>>.
        """

        when:
        run('checkDeadInternalLinks').build()

        then:
        assertNoDeadLinks()
    }

    def "finds broken javadoc method links"() {
        given:
        sampleDoc << """
=== Invalid Javadoc Links

The `link:{javadocPath}/nowhere/gradle/api/attributes/AttributesSchema.html#setAttributeDisambiguationPrecedence(List)--[AttributeSchema.setAttributeDisambiguationPrecedence(List)]` and `link:{javadocPath}/org/gradle/api/nowhere/AttributesSchema.html#getAttributeDisambiguationPrecedence()--[AttributeSchema.getAttributeDisambiguationPrecedence()]` methods now accept and return `List` instead of `Collection` to better indicate that the order of the elements in those collection is significant.
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadJavadocLinks(sampleDoc, "nowhere/gradle/api/attributes/AttributesSchema.html", "org/gradle/api/nowhere/AttributesSchema.html")
    }

    def "finds broken javadoc class links"() {
        given:
        sampleDoc << """
=== Invalid Javadoc Links

Be sure to see: `@link:{javadocPath}/org/gradle/nowhere/tasks/InputDirectory.html[InputDirectory]`
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadJavadocLinks(sampleDoc, "org/gradle/nowhere/tasks/InputDirectory.html")
    }

    def "finds broken javadoc links with leading javadoc path component"() {
        given:
        sampleDoc << """
=== Invalid Javadoc Links

The `link:{javadocPath}/javadoc/org/gradle/api/attributes/AttributesSchema.html#setAttributeDisambiguationPrecedence(List)--[AttributeSchema.setAttributeDisambiguationPrecedence(List)]` and `link:{javadocPath}/javadoc/org/gradle/api/attributes/AttributesSchema.html#getAttributeDisambiguationPrecedence()--[AttributeSchema.getAttributeDisambiguationPrecedence()]` methods now accept and return `List` instead of `Collection` to better indicate that the order of the elements in those collection is significant.
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadJavadocLinks(sampleDoc, "javadoc/org/gradle/api/attributes/AttributesSchema.html", "javadoc/org/gradle/api/attributes/AttributesSchema.html")
    }

    def "validates present files for javadoc links"() {
        given:
        sampleDoc << """
=== Valid Javadoc Links

Be sure to see: `@link:{javadocPath}/org/gradle/api/tasks/InputDirectory.html[InputDirectory]`
The `link:{javadocPath}/org/gradle/api/attributes/AttributesSchema.html#setAttributeDisambiguationPrecedence(List)--[AttributeSchema.setAttributeDisambiguationPrecedence(List)]` and `link:{javadocPath}/org/gradle/api/attributes/AttributesSchema.html#getAttributeDisambiguationPrecedence()--[AttributeSchema.getAttributeDisambiguationPrecedence()]` methods now accept and return `List` instead of `Collection` to better indicate that the order of the elements in those collection is significant.
        """

        createJavadocForClass("org/gradle/api/tasks/InputDirectory")
        createJavadocForClass("org/gradle/api/attributes/AttributesSchema")

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').build()

        then:
        assertNoDeadLinks()
    }

    def "finds Markdown style links"() {
        given:
        sampleDoc << """
=== Markdown Style Links
[Invalid markdown link](https://docs.gradle.org/nowhere)
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([DeadLink.forMarkdownLink(sampleDoc, "[Invalid markdown link](https://docs.gradle.org/nowhere)")])
    }

    def "finds Release notes broken links"() {
        given:
        sampleDoc << """
Nothing to write about
        """

        and:
        releaseNotes << """
<p>The Gradle team is excited to announce Gradle @version@.</p>
<p>This release features <a href="">1</a>, <a href="">2</a>, ... <a href="">n</a>, and more.</p>
<p>We would like to thank the following community members for their contributions to this release of Gradle:</p>
<p>Be sure to check out the <a href="https://blog.gradle.org/roadmap-announcement">public roadmap</a> for insight into what's planned for future releases.</p>
<h2>Upgrade instructions</h2>
<p>Switch your build to use Gradle @version@ by updating the <a href="userguide/gradle_super_wrapper.html">Wrapper</a> in your project:</p>
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks()
    }

    private File createJavadocForClass(String path) {
        new File(docsRoot, "javadoc/${path}.html").tap {
            parentFile.mkdirs()
            createNewFile()
            text = "Generated javadoc HTML goes here"
        }
    }

    private GradleRunner run(String... args) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(args)
            .forwardOutput()
    }

    private void assertNoDeadLinks() {
        assert linkErrors.exists()
        assert linkErrors.text.stripTrailing().endsWith('All clear!')
    }

    private void assertFoundDeadLinks(Collection<DeadLink> deadLinks) {
        assert linkErrors.exists()

        def lines = linkErrors.readLines()
        deadLinks.each { deadLink ->
            String errorStart = "ERROR: ${deadLink.file.name}:"
            assert lines.any { it.startsWith(errorStart) && it.endsWith(deadLink.message) }
        }
    }

    private void assertFoundDeadSectionLinks(File file, String... sections) {
        assertFoundDeadLinks(sections.collect { DeadLink.forSection(file, it) })
    }

    private void assertFoundDeadJavadocLinks(File file, String... paths) {
        assertFoundDeadLinks(paths.collect { DeadLink.forJavadoc(file, it) })
    }

    private static final class DeadLink {
        private final File file
        private final String message

        DeadLink(File file, String message) {
            this.file = file
            this.message = message
        }

        static DeadLink forSection(File file, String section) {
            return new DeadLink(file, "Looking for section named $section in ${file.name}")
        }

        static DeadLink forJavadoc(File file, String path) {
            return new DeadLink(file, "Missing Javadoc file for $path in ${file.name}" + (path.startsWith("javadoc") ? " (You may need to remove the leading `javadoc` path component)" : ""))
        }

        static DeadLink forMarkdownLink(File file, String link) {
            return new DeadLink(file, "Markdown-style links are not supported: $link")
        }
    }
}
