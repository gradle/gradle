package org.gradle.build.docs.releasenotes.checks

import org.gradle.build.docs.releasenotes.ReleaseNotes
import org.gradle.build.docs.releasenotes.ReleaseNotesCheck
import org.gradle.build.docs.releasenotes.ReleaseNotesError
import org.gradle.build.docs.releasenotes.ReleaseNotesErrorCollector
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class AbstractReleaseNotesCheckTest extends Specification {

    @Rule TemporaryFolder tmp

    List<ReleaseNotesError> errors

    List<ReleaseNotesError> check(String source, String rendered, ReleaseNotesCheck... checks) {
        def notes = notes(source, rendered)
        errors = []
        def collector = new ReleaseNotesErrorCollector(errors)
        checks.each {
            it.check(notes, collector)
        }
        errors
    }

    List<ReleaseNotesError> checkRendered(String rendered, ReleaseNotesCheck... checks) {
        check("", rendered, checks)
    }

    List<ReleaseNotesError> checkSource(String source, ReleaseNotesCheck... checks) {
        check(source, "", checks)
    }

    ReleaseNotes notes(source, rendered) {
        def encoding = "utf-8"
        println tmp.root.absolutePath
        def sourceFile = new File(tmp.root, "source.md")
        sourceFile.createNewFile()
        sourceFile.write(source, encoding)
        def renderedFile = new File(tmp.root, "rendered.md")
        renderedFile.createNewFile()
        renderedFile.write(rendered, encoding)
        new ReleaseNotes(sourceFile, renderedFile, encoding)
    }
}
