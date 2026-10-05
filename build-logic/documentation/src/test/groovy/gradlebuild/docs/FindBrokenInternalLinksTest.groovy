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

import gradlebuild.basics.RepositoryMirrors
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class FindBrokenInternalLinksTest extends Specification {
    @TempDir
    private File projectDir
    private File docsRoot
    private File sampleDoc
    private File sampleSampleDoc
    private File releaseNotes
    private File linkErrors

    private setup() {
        docsRoot = new File(projectDir, "docsRoot")
        new File(docsRoot, 'javadoc').mkdirs()
        new File(docsRoot, 'dsl').mkdirs()
        sampleDoc = new File(docsRoot, "sample.adoc")

        new File(projectDir,"build/working/samples/docs").mkdirs()
        sampleSampleDoc = new File(projectDir, "build/working/samples/docs/sample_sample.adoc")

        new File(projectDir,"build/working/release-notes").mkdirs()
        releaseNotes = new File(projectDir, "build/working/release-notes/raw.html")

        linkErrors = new File(projectDir, "build/reports/dead-internal-links.txt")

        new File(projectDir, "gradle.properties") << """
            org.jetbrains.dokka.experimental.gradle.pluginMode=V2Enabled
            org.jetbrains.dokka.experimental.gradle.pluginMode.noWarn=true
        """.stripIndent()

        new File(projectDir, "settings.gradle") << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create('buildLibs') {
                        version('asciidoctor', '3.0.1')
                        version('asciidoctorPdf', '2.3.23')
                    }
                }
            }
        """.stripIndent()

        // `repoRoot()` walks up the tree looking for `version.txt`; `released-versions.json` is the
        // source for the `gradleVersion8` Asciidoctor attribute. Provide both so the documentation
        // plugin applies cleanly in this minimal TestKit project.
        new File(projectDir, "version.txt") << "test"
        new File(projectDir, "released-versions.json") << '{"finalReleases":[{"version":"8.99.99","buildTime":"20260101000000+0000"}]}'

        new File(projectDir, "src/docs/javaPackageList/8").mkdirs()
        new File(projectDir, "src/docs/javaPackageList/8/package-list") << """
        java.lang
        """.stripIndent()

        new File(projectDir, "build.gradle") << """
            plugins {
                id 'java'
                id 'checkstyle'
                id 'gradlebuild.documentation'
            }

            repositories {
                mavenCentral()
            }

            gradleDocumentation {
                javadocs {
                    javaApi = project.uri("https://docs.oracle.com/javase/8/docs/api")
                    javaPackageListLoc = project.layout.projectDirectory.dir("src/docs/javaPackageList/8/")
                    groovyApi = project.uri("https://docs.groovy-lang.org/docs/groovy-4.0.28/html/gapi")
                    groovyPackageListSrc = "org.apache.groovy:groovy-all:4.0.28:groovydoc"
                }
            }

            tasks.register('assembleSamples')

            javadocAll {
                enabled = false
            }

            tasks.named('checkDeadInternalLinks').configure {
                documentationRoot = project.layout.projectDirectory.dir('docsRoot')
                javadocRoot = documentationRoot.dir('javadoc')
                if (!providers.gradleProperty('quickDocs').present) {
                    dslRoot = documentationRoot.dir('dsl')
                }
                releaseNotesFile = project.layout.buildDirectory.file('working/release-notes/raw.html')
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
The `link:{javadocPath}/org/gradle/api/attributes/AttributesSchema.html#setAttributeDisambiguationPrecedence(java.util.List)[AttributeSchema.setAttributeDisambiguationPrecedence(List)]` and `link:{javadocPath}/org/gradle/api/attributes/AttributesSchema.html#getAttributeDisambiguationPrecedence()[AttributeSchema.getAttributeDisambiguationPrecedence()]` methods now accept and return `List` instead of `Collection` to better indicate that the order of the elements in those collection is significant.
        """

        createJavadocForClass("org/gradle/api/tasks/InputDirectory")
        createJavadocForClass("org/gradle/api/attributes/AttributesSchema", "setAttributeDisambiguationPrecedence(java.util.List)", "getAttributeDisambiguationPrecedence()")

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').build()

        then:
        assertNoDeadLinks()
    }

    def "finds missing javadoc anchors"() {
        given:
        sampleDoc << """
=== Invalid Javadoc Anchors

See link:{javadocPath}/org/gradle/api/Task.html#getName--[Task.getName()] and link:{javadocPath}/org/gradle/api/Task.html#dependsOn(Object++...++)[Task.dependsOn()].
Also link:{javaDocPath}/org/gradle/api/Task.html#getPath()[Task.getPath()] and link:{javadocPath}/org/gradle/api/Task.html#description[Task description].
        """
        createJavadocForClass("org/gradle/api/Task", "getName()", "dependsOn(java.lang.Object...)")

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([
            DeadLink.forJavadocAnchor(sampleDoc, "org/gradle/api/Task.html", "getName--"),
            DeadLink.forJavadocAnchor(sampleDoc, "org/gradle/api/Task.html", "dependsOn(Object...)"),
            DeadLink.forJavadocAnchor(sampleDoc, "org/gradle/api/Task.html", "getPath()"),
            DeadLink.forJavadocAnchor(sampleDoc, "org/gradle/api/Task.html", "description"),
        ])
    }

    def "validates present javadoc anchors"() {
        given:
        sampleDoc << """
=== Valid Javadoc Anchors

See link:{javadocPath}/org/gradle/api/Task.html#dependsOn(java.lang.Object++...++)[Task.dependsOn()], link:{javadocPath}/org/gradle/api/Task.html#getName()[Task.getName()] and link:{javadocPath}/org/gradle/api/Task.html#method-summary[methods].
Also link:{javadocPath}/org/gradle/api/Task.html#finalizedBy(java.lang.Object%2E%2E%2E)[Task.finalizedBy()].
        """
        createJavadocForClass("org/gradle/api/Task", "dependsOn(java.lang.Object...)", "getName()", "method-summary", "finalizedBy(java.lang.Object...)")

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').build()

        then:
        assertNoDeadLinks()
    }

    def "finds unescaped ellipses in link targets"() {
        given:
        sampleDoc << """
=== Unescaped Ellipses

See link:{javadocPath}/org/gradle/api/Task.html#dependsOn(java.lang.Object...)[Task.dependsOn()] and link:{javaApi}/java/lang/String.html#format(java.lang.String,java.lang.Object...)[String.format()].
        """
        createJavadocForClass("org/gradle/api/Task", "dependsOn(java.lang.Object...)")

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([
            DeadLink.forUnescapedEllipsis(sampleDoc, "{javadocPath}/org/gradle/api/Task.html#dependsOn(java.lang.Object...)"),
            DeadLink.forUnescapedEllipsis(sampleDoc, "{javaApi}/java/lang/String.html#format(java.lang.String,java.lang.Object...)"),
        ])
    }

    def "finds doubled link macros"() {
        given:
        sampleDoc << """
See link:link:{javadocPath}/org/gradle/api/Task.html[Task].
        """
        createJavadocForClass("org/gradle/api/Task")

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([new DeadLink(sampleDoc, "Doubled `link:` macro in link target link:{javadocPath}/org/gradle/api/Task.html")])
    }

    def "finds broken DSL reference links"() {
        given:
        sampleDoc << """
=== Invalid DSL Reference Links

See link:{groovyDslPath}/org.gradle.api.DefaultTask.html[DefaultTask] and link:{groovyDslPath}/org.gradle.api.Task.html#org.gradle.api.Task:dependsOn(java.lang.Object++...++)[Task.dependsOn()].
        """
        createDslPageForClass("org.gradle.api.Task", "org.gradle.api.Task:dependsOn(java.lang.Object[])")

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([
            new DeadLink(sampleDoc, "Missing DSL reference file for org.gradle.api.DefaultTask.html in ${sampleDoc.name}"),
            new DeadLink(sampleDoc, "Missing anchor org.gradle.api.Task:dependsOn(java.lang.Object...) in DSL reference file org.gradle.api.Task.html"),
        ])
    }

    def "validates present DSL reference links"() {
        given:
        sampleDoc << """
=== Valid DSL Reference Links

See link:{groovyDslPath}/org.gradle.api.Task.html#org.gradle.api.Task:dependsOn(java.lang.Object++[]++)[Task.dependsOn()], link:{groovyDslPath}/org.gradle.api.Task.html#org.gradle.api.Task:mustRunAfter(java.lang.Object&#91;&#93;)[Task.mustRunAfter()], link:{groovyDslPath}/org.gradle.api.Task.html#org.gradle.api.Task:shouldRunAfter(java.lang.Object&#x5B;&#x5D;)[Task.shouldRunAfter()] and link:{groovyDslPath}/org.gradle.api.Task.html#org.gradle.api.Task:onlyIf(java.lang.String,%20org.gradle.api.specs.Spec)[Task.onlyIf()].
        """
        createDslPageForClass("org.gradle.api.Task", "org.gradle.api.Task:dependsOn(java.lang.Object[])", "org.gradle.api.Task:mustRunAfter(java.lang.Object[])", "org.gradle.api.Task:shouldRunAfter(java.lang.Object[])", "org.gradle.api.Task:onlyIf(java.lang.String, org.gradle.api.specs.Spec)")

        and:
        releaseNotes << """
See [`Task`](dsl/org.gradle.api.Task.html), [`Dependencies`](javadoc/org/gradle/api/artifacts/dsl/Dependencies.html) and [`Project`](kotlin-dsl/gradle/org.gradle.api/-project/index.html).
        """
        createJavadocForClass("org/gradle/api/artifacts/dsl/Dependencies")

        when:
        run('checkDeadInternalLinks').build()

        then:
        assertNoDeadLinks()
    }

    def "finds broken DSL reference links in release notes"() {
        given:
        sampleDoc << """
Nothing to write about
        """
        createDslPageForClass("org.gradle.api.Task")

        and:
        releaseNotes << """
See [`Sync`](dsl/org.gradle.api.tasks.Sync.html) and [`Task.description`](dsl/org.gradle.api.Task.html#org.gradle.api.Task:description).
<p>Also see <a href="dsl/org.gradle.api.tasks.Copy.html">Copy</a>.</p>
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([
            new DeadLink(releaseNotes, "Missing DSL reference file for org.gradle.api.tasks.Sync.html in ${releaseNotes.name}"),
            new DeadLink(releaseNotes, "Missing anchor org.gradle.api.Task:description in DSL reference file org.gradle.api.Task.html"),
            new DeadLink(releaseNotes, "Missing DSL reference file for org.gradle.api.tasks.Copy.html in ${releaseNotes.name}"),
        ])
    }

    def "does not check DSL reference links in quick feedback mode"() {
        given:
        sampleDoc << """
See link:{groovyDslPath}/org.gradle.api.Nowhere.html[Nowhere].
        """

        and:
        releaseNotes << """
Nothing to write about
        """

        when:
        def result = run('checkDeadInternalLinks', '-PquickDocs').build()

        then:
        assertNoDeadLinks()
        result.output.contains("The DSL reference was not generated, links into it are not checked")
    }

    def "finds missing javadoc anchors in release notes"() {
        given:
        sampleDoc << """
Nothing to write about
        """
        createJavadocForClass("org/gradle/api/Task", "getName()")

        and:
        releaseNotes << """
* [`getName()`](javadoc/org/gradle/api/Task.html#getName()) is still there, but [`getPath()`](javadoc/org/gradle/api/Task.html#getPath()) is not.
        """

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([DeadLink.forJavadocAnchor(releaseNotes, "org/gradle/api/Task.html", "getPath()")])
        !linkErrors.text.contains("Missing anchor getName()")
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

    private File createJavadocForClass(String path, String... ids) {
        new File(docsRoot, "javadoc/${path}.html").tap {
            parentFile.mkdirs()
            createNewFile()
            text = """<meta name="description" content="Generated javadoc HTML goes here">\n""" + ids.collect { """<section class="detail" id="$it">""" }.join("\n")
        }
    }

    private File createDslPageForClass(String className, String... anchors) {
        new File(docsRoot, "dsl/${className}.html").tap {
            parentFile.mkdirs()
            text = "Generated DSL reference HTML goes here\n" + anchors.collect { """<a name="$it"></a>""" }.join("\n")
        }
    }

    private GradleRunner run(String... args) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(args.toList() + RepositoryMirrors.testKitArguments())
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

        static DeadLink forJavadocAnchor(File file, String path, String anchor) {
            return new DeadLink(file, "Missing anchor $anchor in Javadoc file $path")
        }

        static DeadLink forUnescapedEllipsis(File file, String target) {
            return new DeadLink(file, "Unescaped `...` in link target $target (write `++...++` instead)")
        }

        static DeadLink forMarkdownLink(File file, String link) {
            return new DeadLink(file, "Markdown-style links are not supported: $link")
        }
    }
}
