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
    private File sampleDoc
    private File linkErrors

    private setup() {
        docsRoot = new File(projectDir, "docsRoot")
        sampleDoc = new File(docsRoot, "sample.adoc")
        linkErrors = new File(projectDir, "build/reports/dead-internal-links.txt")

        new File(projectDir, "gradle.properties") << """
            org.jetbrains.dokka.experimental.gradle.pluginMode=V2Enabled
            org.jetbrains.dokka.experimental.gradle.pluginMode.noWarn=true
        """.stripIndent()

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

            tasks.named('checkDeadInternalLinks').configure {
                documentationRoot = project.layout.projectDirectory.dir('docsRoot')
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

        when:
        run('checkDeadInternalLinks').buildAndFail()

        then:
        assertFoundDeadLinks([DeadLink.forMarkdownLink(sampleDoc, "[Invalid markdown link](https://docs.gradle.org/nowhere)")])
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

        static DeadLink forMarkdownLink(File file, String link) {
            return new DeadLink(file, "Markdown-style links are not supported: $link")
        }
    }
}
