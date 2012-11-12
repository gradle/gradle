package org.gradle.build.docs.releasenotes

class ReleaseNotesErrorCollector {

    private final List<ReleaseNotesError> errors

    ReleaseNotesErrorCollector(List<ReleaseNotesError> errors) {
        this.errors = errors
    }

    void add(String ruleName, String message) {
        errors << new ReleaseNotesError(ruleName, message)
    }

}
