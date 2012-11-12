package org.gradle.build.docs.releasenotes

class ReleaseNotesError {

    final String rule
    final String message

    ReleaseNotesError(String rule, String message) {
        this.rule = rule
        this.message = message
    }

    @Override
    public String toString() {
        "$rule: $message"
    }
}
