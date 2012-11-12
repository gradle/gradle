package org.gradle.build.docs.releasenotes.checks

class NoBrokenInternalLinksReleaseNotesCheckTest extends AbstractReleaseNotesCheckTest {

    def c = new NoBrokenInternalLinksReleaseNotesCheck()

    def "check"() {
        expect:
        checkRendered("<p />", c).empty
        checkRendered("<a href='foo' />", c).empty
        with(checkRendered("<a href='#foo' /><a href='#bar' />", c)) {
            size() == 2
            first().message == "Could not find anchor with name or element with id 'foo' (link: <a href=\"#foo\"></a>)"
            last().message == "Could not find anchor with name or element with id 'bar' (link: <a href=\"#bar\"></a>)"
        }
        with(checkRendered("<p id='foo'/><a href='#foo' /><a href='#bar' />", c)) {
            size() == 1
            first().message == "Could not find anchor with name or element with id 'bar' (link: <a href=\"#bar\"></a>)"
        }
        with(checkRendered("<a name='bar'><p id='foo'/></a><a href='#foo' /><a href='#bar' />", c)) {
            empty
        }
    }
}
