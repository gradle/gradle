/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.buildutils.tasks

import spock.lang.Specification

class PreparePatchReleaseNotesTest extends Specification {

    static final String MINOR_NOTES = '''<meta property="og:image" content="https://gradle.org/assets/images/releases/gradle-default.png" />
<meta name="twitter:description" content="We are excited to announce Gradle @version@.">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

This release improves [Configuration Cache](#configuration-cache-improvements) hit rates.

We would like to thank the following community members for their contributions to this release of Gradle:
[Some One](https://github.com/someone).

## Upgrade instructions

Switch your build to use Gradle @version@.
'''

    def "rewrites the minor intro into the patch intro with computed base version and ordinal"() {
        when:
        def result = PreparePatchReleaseNotesKt.patchReleaseNotes(MINOR_NOTES, version)

        then:
        result.contains("Gradle @version@ is the $ordinal patch release for Gradle $base. (released [@releaseDate@](https://gradle.org/releases/)).")
        // the minor-release intro line is gone
        !result.contains("We are excited to announce Gradle @version@ (released")

        where:
        version | base    | ordinal
        "9.6.1" | "9.6.0" | "first"
        "9.6.2" | "9.6.0" | "second"
        "8.14.3" | "8.14.0" | "third"
    }

    def "produces a fixed-issues section compatible with updateFixedIssuesInReleaseNotes"() {
        when:
        def result = PreparePatchReleaseNotesKt.patchReleaseNotes(MINOR_NOTES, "9.6.1")

        then:
        // exact marker the other task searches for
        result.contains(UpdateFixedIssuesInReleaseNotesKt.FIXED_ISSUES_INTRO)
        // placeholder bullet present
        result.contains("* TODO")
        // boundary line that terminates the issues list for findEndOfListSection
        result.contains("We recommend upgrading to Gradle @version@.")
        // separator before the retained feature notes
        result.contains("---")
    }

    def "keeps the rest of the notes and the render tokens intact"() {
        when:
        def result = PreparePatchReleaseNotesKt.patchReleaseNotes(MINOR_NOTES, "9.6.1")

        then:
        // feature notes preserved below the separator
        result.contains("This release improves [Configuration Cache](#configuration-cache-improvements) hit rates.")
        result.contains("## Upgrade instructions")
        // meta tags untouched
        result.contains('<meta name="twitter:description" content="We are excited to announce Gradle @version@.">')
        // render-time tokens left literal
        result.contains("@version@")
        result.contains("@releaseDate@")
    }

    def "rejects a non-patch version"() {
        when:
        PreparePatchReleaseNotesKt.patchReleaseNotes(MINOR_NOTES, version)

        then:
        thrown(IllegalArgumentException)

        where:
        version << ["9.6.0", "9.6", "9", "not.a.version"]
    }

    def "fails when the intro line is missing"() {
        when:
        PreparePatchReleaseNotesKt.patchReleaseNotes("# Already a patch release\\n\\nThe following issues were resolved:\\n", "9.6.1")

        then:
        thrown(IllegalStateException)
    }

    def "maps patch numbers to ordinal words with a numeric fallback"() {
        expect:
        PreparePatchReleaseNotesKt.patchOrdinal(n) == expected

        where:
        n  | expected
        1  | "first"
        2  | "second"
        3  | "third"
        10 | "tenth"
        11 | "11th"
    }
}
