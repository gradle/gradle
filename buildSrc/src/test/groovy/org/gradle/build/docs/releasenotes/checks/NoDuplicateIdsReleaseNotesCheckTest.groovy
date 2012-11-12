package org.gradle.build.docs.releasenotes.checks

class NoDuplicateIdsReleaseNotesCheckTest extends AbstractReleaseNotesCheckTest {

    def "check"() {
        def noIds = new NoDuplicateIdsReleaseNotesCheck()

        expect:
        with(check("", "<p id='a'/><p id='a'/>", noIds)) {
            size() == 1
            first().message == "Found more than one element with id 'a': [p, p]"
        }
        with(check("", "<p id='a'/><p id='b'/>", noIds)) {
            empty
        }
    }

}
