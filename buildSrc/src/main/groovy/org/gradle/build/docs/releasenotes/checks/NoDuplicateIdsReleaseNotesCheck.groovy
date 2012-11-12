package org.gradle.build.docs.releasenotes.checks

import org.gradle.build.docs.releasenotes.ReleaseNotesCheck
import org.gradle.build.docs.releasenotes.ReleaseNotes
import org.gradle.build.docs.releasenotes.ReleaseNotesErrorCollector

class NoDuplicateIdsReleaseNotesCheck implements ReleaseNotesCheck {

    @Override
    void check(ReleaseNotes notes, ReleaseNotesErrorCollector errors) {
        def groupedElements = notes.renderedDocument.body().allElements.groupBy { it.id() }
        groupedElements.each { id, elements ->
            if (id && elements.size() > 1) {
                errors.add("NoDuplicateIds", "Found more than one element with id '$id': ${elements*.tagName()}")
            }
        }
    }

}
