/*
 * Copyright 2022 the original author or authors.
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

import gradlebuild.basics.toLowerCase
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Depends on GitHub API")
abstract class UpdateContributorsInReleaseNotes : AbstractCheckOrUpdateContributorsInReleaseNotes() {
    @TaskAction
    fun update() {
        val contributorsInReleaseNotes = getContributorsInReleaseNotes().associateBy { it.login }
        val contributorsFromPullRequests = getContributorsFromPullRequests().associateBy { it.login }

        val unrecognizedContributors = contributorsFromPullRequests.keys - contributorsInReleaseNotes.keys
        if (unrecognizedContributors.isNotEmpty()) {
            val contributorsToUpdate = contributorsInReleaseNotes + unrecognizedContributors.map { it to contributorsFromPullRequests[it]!! }
            val sortedContributors = contributorsToUpdate.entries.sortedBy { (it.value.name ?: it.key).toLowerCase() }
            val (linesBeforeContributors, _, linesAfterContributors) = parseReleaseNotes()
            releaseNotes.asFile.get().writeText(
                "${linesBeforeContributors.joinToString("\n")}\n${sortedContributors.joinToString(",\n") { "[${it.value.name ?: it.key}](https://github.com/${it.key})" }}\n\n${linesAfterContributors.joinToString("\n")}\n"
            )
        } else {
            println("Contributors in the release notes are up to date.")
        }
    }
}
