package org.gradle.build.docs.releasenotes

interface ReleaseNotesCheck extends Serializable {

    void check(ReleaseNotes notes, ReleaseNotesErrorCollector errors)

}
