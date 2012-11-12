package org.gradle.build.docs.releasenotes.checks

import org.gradle.build.docs.releasenotes.ReleaseNotes
import org.gradle.build.docs.releasenotes.ReleaseNotesCheck
import org.gradle.build.docs.releasenotes.ReleaseNotesErrorCollector

class CssContentReleaseNotesCheck implements ReleaseNotesCheck {
    final String selector
    final String message
    final int min
    final int max

    public static final String CODE = "CssContentCheck"

    CssContentReleaseNotesCheck(String selector, int min, String message = null) {
        this(selector, min, min, message)
    }

    CssContentReleaseNotesCheck(String selector, int min, int max, String message = null) {
        this.selector = selector
        this.min = min
        this.max = max
        this.message = message
    }

    @Override
    void check(ReleaseNotes notes, ReleaseNotesErrorCollector errors) {
        def match = notes.renderedDocument.body().select(selector)
        def matchSize = match.size()

        if (min == max) {
            if (matchSize != min) {
                errors.add(CODE, "Expected $min matches for '$selector', got $matchSize${message ? " ($message)" : ""}")
            }
        } else if (matchSize < min || matchSize > max) {
            errors.add(CODE, "Expected between $min and $max matches for '$selector', got $matchSize${message ? " ($message)" : ""}")
        }
    }
}
