package org.gradle.build.docs.releasenotes.checks

import org.gradle.build.docs.releasenotes.ReleaseNotesCheck
import org.gradle.build.docs.releasenotes.ReleaseNotes
import org.gradle.build.docs.releasenotes.ReleaseNotesErrorCollector

class NoBrokenInternalLinksReleaseNotesCheck implements ReleaseNotesCheck {

    @Override
    void check(ReleaseNotes notes, ReleaseNotesErrorCollector errors) {
        def doc = notes.renderedDocument
        doc.select("a").each {
            def href = it.attr("href")
            if (href.startsWith("#")) {
                def target = href[1..-1]
                def withId = doc.select("*").findAll { it.id() == target }
                def anchorWithName = doc.select("a").findAll { it.attr("name") == target }
                if (withId.empty && anchorWithName.empty) {
                    errors.add("BrokenInternalLink", "Could not find anchor with name or element with id '$target' (link: $it)")
                }
            }
        }
    }
}
