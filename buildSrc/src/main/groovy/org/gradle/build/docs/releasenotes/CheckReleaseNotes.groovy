package org.gradle.build.docs.releasenotes

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.logging.ConsoleRenderer

import java.nio.charset.Charset

class CheckReleaseNotes extends DefaultTask implements VerificationTask {

    boolean ignoreFailures
    @Input String encoding = Charset.defaultCharset().name()

    @InputFile File sourceFile
    @InputFile File renderedFile

    List<ReleaseNotesCheck> checks = []

    void checks(ReleaseNotesCheck... checks) {
        checks.each { this.checks << it }
    }

    @TaskAction
    void doCheck() {
        def releaseNotes = new ReleaseNotes(sourceFile, renderedFile, encoding)
        def errors = []
        def collector = new ReleaseNotesErrorCollector(errors)

        checks.each {
            it.check(releaseNotes, collector)
        }

        if (errors) {
            def consoleRenderer = new ConsoleRenderer()
            println "Errors found with release notes."
            println " - Source: ${consoleRenderer.asClickableFileUrl(releaseNotes.source)}"
            println " - Rendered: ${consoleRenderer.asClickableFileUrl(releaseNotes.rendered)}"
            println ""
            println "Errors:"
            errors.each {
                println " > $it"
            }

            if (!ignoreFailures) {
                throw new InvalidUserDataException("Errors were found with the release notes")
            }
        }
    }
}
