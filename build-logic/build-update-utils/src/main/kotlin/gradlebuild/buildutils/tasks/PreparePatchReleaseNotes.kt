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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault


/**
 * The minor-release intro line that every release starts from. A patch release replaces this
 * single line with the patch intro produced by [patchReleaseNotes].
 */
private
const val MINOR_RELEASE_INTRO_PREFIX = "We are excited to announce Gradle @version@ (released"


/**
 * The `(released ...)` suffix, reused verbatim from the minor intro line so the patch intro keeps
 * the same render-time tokens.
 */
private
const val RELEASE_DATE_SUFFIX = "(released [@releaseDate@](https://gradle.org/releases/))."


/**
 * Rewrites the release notes intro for a patch release.
 *
 * Invoked by the `preparePatchRelease` lifecycle task, after `bumpVersionForPatchRelease` has bumped
 * `version.txt` to the patch version, and before `updateFixedIssuesInReleaseNotes` fills in the list
 * of resolved issues. The minor-release feature notes below the intro are left untouched.
 *
 * The version is read from [versionFile] at execution time, so the just-bumped patch version is
 * picked up. Ordering after `bumpVersionForPatchRelease` is therefore required.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class PreparePatchReleaseNotes : DefaultTask() {

    @get:Internal
    abstract val releaseNotes: RegularFileProperty

    @get:Internal
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val notesFile = releaseNotes.asFile.get()
        val patchVersion = versionFile.asFile.get().readText().trim()
        notesFile.writeText(patchReleaseNotes(notesFile.readText(), patchVersion))
        println("Prepared release notes for patch release $patchVersion in $notesFile")
    }
}


/**
 * Replaces the minor-release intro line in [content] with the patch-release intro for [version].
 *
 * The produced intro contains the [FIXED_ISSUES_INTRO] marker followed by a `* TODO` placeholder
 * bullet and a `We recommend upgrading...` boundary line, so that [UpdateFixedIssuesInReleaseNotes]
 * can later replace the placeholder with the actual list of resolved issues.
 */
fun patchReleaseNotes(content: String, version: String): String {
    val parts = version.split(".")
    require(parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
        "Version '$version' is not a valid x.y.z version."
    }
    val patchNumber = parts[2].toInt()
    require(patchNumber > 0) {
        "Version '$version' is not a patch release (expected x.y.z with z > 0). Run preparePatchRelease first."
    }
    val baseVersion = "${parts[0]}.${parts[1]}.0"

    val introLine = content.lineSequence().firstOrNull { it.startsWith(MINOR_RELEASE_INTRO_PREFIX) }
        ?: error(
            "Could not find the release notes intro line starting with \"$MINOR_RELEASE_INTRO_PREFIX\". " +
                "The notes may already be prepared for a patch release, or the template has changed."
        )

    val patchIntro = listOf(
        "Gradle @version@ is the ${patchOrdinal(patchNumber)} patch release for Gradle $baseVersion. $RELEASE_DATE_SUFFIX",
        "",
        FIXED_ISSUES_INTRO,
        "",
        "* TODO",
        "",
        "We recommend upgrading to Gradle @version@.",
        "",
        "---"
    ).joinToString("\n")

    return content.replace(introLine, patchIntro)
}


private
val ORDINAL_WORDS = listOf(
    "zeroth", "first", "second", "third", "fourth",
    "fifth", "sixth", "seventh", "eighth", "ninth", "tenth"
)


/**
 * Returns the ordinal word for [n] (`1` -> `first`), falling back to a numeric ordinal (`11th`)
 * beyond the small set of words a patch release realistically needs.
 */
fun patchOrdinal(n: Int): String =
    ORDINAL_WORDS.getOrNull(n) ?: "${n}th"
