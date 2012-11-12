package org.gradle.build.docs.releasenotes.checks

class MaxLineLengthReleaseNoteCheckTest extends AbstractReleaseNotesCheckTest {

    def "catches long lines"() {
        given:
        def length = 20
        def source = ["a" * length, "b" * length + 1, "c" * length + 2].join("\n")

        when:
        check(source, "", new MaxLineLengthReleaseNoteCheck(length))

        then:
        errors.size() == 2
        errors[0].message.contains("Line 2 of the source is longer than $length")
        errors[1].message.contains("Line 3 of the source is longer than $length")
    }

}
