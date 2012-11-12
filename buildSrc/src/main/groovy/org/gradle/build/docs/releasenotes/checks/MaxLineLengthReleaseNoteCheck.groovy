package org.gradle.build.docs.releasenotes.checks

import org.gradle.build.docs.releasenotes.ReleaseNotesCheck
import org.gradle.build.docs.releasenotes.ReleaseNotes
import org.gradle.build.docs.releasenotes.ReleaseNotesErrorCollector

class MaxLineLengthReleaseNoteCheck implements ReleaseNotesCheck {

    int maxLineLength

    MaxLineLengthReleaseNoteCheck(int maxLineLength) {
        this.maxLineLength = maxLineLength
    }

    @Override
    void check(ReleaseNotes notes, ReleaseNotesErrorCollector errors) {
        notes.sourceText.eachLine { String line, int num ->
            if (line.size() > maxLineLength) {
                errors.add("MaxLineLength", "Line ${num + 1} of the source is longer than $maxLineLength characters long (it's ${line.size()})")
            }
        }
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        MaxLineLengthReleaseNoteCheck that = (MaxLineLengthReleaseNoteCheck) o

        if (maxLineLength != that.maxLineLength) {
            return false
        }

        return true
    }

    int hashCode() {
        return maxLineLength
    }
}
