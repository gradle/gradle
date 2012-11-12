package org.gradle.build.docs.releasenotes.checks

class CssContentReleaseNotesCheckTest extends AbstractReleaseNotesCheckTest {

    def "single num"() {
        expect:
        with(check("", "<p class='a'/><p class='b'/><p class='c'/>", new CssContentReleaseNotesCheck("p", 2))) {
            size() == 1
            first().message == "Expected 2 matches for 'p', got 3"
        }
        with(check("", "<p class='a'/><p class='b'/><p class='c'/>", new CssContentReleaseNotesCheck("p", 3))) {
            empty
        }
    }

    def "ranges"() {
        expect:
        with(check("", "<p class='a'/><p class='b'/><p class='c'/>", new CssContentReleaseNotesCheck("p", 1, 3))) {
            empty
        }
        with(check("", "<p class='a'/><p class='b'/><p class='c'/>", new CssContentReleaseNotesCheck("p", 1, 2))) {
            size() == 1
            first().message == "Expected between 1 and 2 matches for 'p', got 3"
        }
    }
}
