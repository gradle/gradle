/*
 * Copyright 2026 Gradle and contributors.
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

import java.time.LocalDate

class GradleReleaseNotesPluginTest extends Specification {
    private static final String BASE_VERSION = "9.9.9"
    private static final String RELEASE_DATE_PREFIX = "Released on "
    private static final String RELEASE_DATE_TERMINATOR = "."

    @TempDir
    private File projectDir
    private File releaseNotesHtml

    def setup() {
        write("src/docs/release/notes.md", "## Release notes\n\n${RELEASE_DATE_PREFIX}@releaseDate@${RELEASE_DATE_TERMINATOR}\n")
        write("src/docs/release/content/releaseIssues.js", "// empty\n")
        write("src/docs/css/base.css", "body { }\n")
        write("src/docs/css/release-notes.css", "body { }\n")

        releaseNotesHtml = new File(projectDir, "build/working/release-notes/release-notes.html")

        write("settings.gradle", """
            dependencyResolutionManagement {
                versionCatalogs {
                    create('buildLibs') {
                        version('asciidoctor', '3.0.1')
                        version('asciidoctorPdf', '2.3.23')
                    }
                }
            }
        """.stripIndent())

        // `repoRoot()` walks up the tree looking for `version.txt`; `released-versions.json` is the
        // source for the `gradleVersion8` Asciidoctor attribute. Provide both so the documentation
        // plugin applies cleanly in this minimal TestKit project.
        write("version.txt", BASE_VERSION)
        write("released-versions.json", '{"finalReleases":[{"version":"8.99.99","buildTime":"20260101000000+0000"}]}')
        write("src/docs/javaPackageList/8/package-list", "java.lang\n")

        write("build.gradle", """
            plugins {
                id 'java'
                id 'checkstyle'
                id 'gradlebuild.module-identity'
                id 'gradlebuild.documentation'
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
        """)
    }

    /**
     * The rendered date, taken from between the marker text and the full stop that follows it.
     * `LocalDate.parse` accepts ISO_LOCAL_DATE only, so it doubles as the format check.
     */
    private String renderedReleaseDate() {
        def text = releaseNotesHtml.text
        def start = text.indexOf(RELEASE_DATE_PREFIX) + RELEASE_DATE_PREFIX.length()
        return text.substring(start, text.indexOf(RELEASE_DATE_TERMINATOR, start))
    }

    private void write(String path, String content) {
        def target = new File(projectDir, path)
        target.parentFile.mkdirs()
        target.text = content
    }

    def "is compatible with the configuration cache for a #description version"() {
        when:
        def result = run(['releaseNotes', '--configuration-cache'] + versionArgs).build()

        then:
        result.task(':releaseNotes').outcome.name() == 'SUCCESS'

        and:
        LocalDate.parse(renderedReleaseDate())

        where:
        // Only the last three rows take the build timestamp out of the version. The snapshot row
        // keeps it and resolves it early, so it stays green even when the formatters are captured.
        description         | versionArgs
        'snapshot'          | []
        'final release'     | ['-PfinalRelease=true']
        'release candidate' | ['-PrcNumber=1']
        'milestone'         | ['-PmilestoneNumber=1']
    }

    private GradleRunner run(List<String> args) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(args)
            .forwardOutput()
    }
}
