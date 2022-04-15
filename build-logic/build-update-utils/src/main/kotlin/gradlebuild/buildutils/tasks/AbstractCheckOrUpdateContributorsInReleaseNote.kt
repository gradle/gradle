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

import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class GitHubPullRequestSearchResult(val items: List<GitHubPullRequest>)
data class GitHubUser(val login: String, val name: String?)
data class GitHubPullRequestMilestone(val title: String)
data class GitHubPullRequest(
    val number: Int,
    val user: GitHubUser,
    val milestone: GitHubPullRequestMilestone?,
)

@DisableCachingByDefault(because = "Depends on GitHub API")
abstract class AbstractCheckOrUpdateContributorsInReleaseNote : DefaultTask() {
    @get: Internal
    val releaseNote: RegularFileProperty = project.objects.fileProperty()

    @get: Option(option = "milestone", description = "The milestone prefix to check for contributors, i.e. '7.5' includes PRs with '7.5', '7.5 RC1', '7.5.1'")
    @get: Internal
    var milestone: Property<String> = project.objects.property(String::class.java)

    @get: Option(option = "github-token", description = "The GitHub token to use for API requests. Will use anonymous requests if not specified.")
    @get: Internal
    var githubToken: Property<String> = project.objects.property(String::class.java)

    @get: Internal
    protected val contributorSectionRegex =
        "(?s)(.*We would like to thank the following community members for their contributions to this release of Gradle:\\n)(.*)(\\n<!--\nInclude only their name.*)".toRegex()
    private val contributorLineRegex = "\\[(.*)]\\(https://github.com/(.*)\\)".toRegex()
    private val pageSize = 100

    @Internal
    protected fun getContributorsInReleaseNote(): Set<GitHubUser> {
        val releaseNoteText = releaseNote.asFile.get().readText()
        if (!contributorSectionRegex.containsMatchIn(releaseNoteText)) {
            throw IllegalStateException("We can't find contributors in the release note $releaseNote. Is anything changed?")
        }
        return contributorSectionRegex.find(releaseNoteText)!!.groupValues[2].lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .onEach { if (!contributorLineRegex.containsMatchIn(it)) throw IllegalStateException("Invalid contributor line: $it") }
            .map { GitHubUser(contributorLineRegex.find(it)!!.groupValues[2], contributorLineRegex.find(it)!!.groupValues[1]) }
            .toSet()
    }

    @Internal
    protected fun getContributorsFromPullRequests(): Set<GitHubUser> {
        if (!milestone.isPresent) {
            throw IllegalStateException("Milestone not set: please rerun the task with `--milestone <milestone>`")
        }
        val prs: MutableList<GitHubPullRequest> = mutableListOf()
        var page = 0
        while (++page <= 10) { // at most 1000 PRs
            val prPage = getMergedContributorPullRequests(page)
            prs.addAll(prPage)
            if (prPage.size < pageSize) {
                break
            }
        }
        return prs
            .filter { it.milestone?.title?.startsWith(milestone.get()) == true }
            .map { it.user.login }
            .toSet()
            .map { getUserInfo(it) }
            .toSet()
    }

    private fun <T> invokeGitHubApi(uri: String, klass: Class<T>): T {
        val request = HttpRequest.newBuilder()
            .uri(URI(uri))
            .apply {
                if (githubToken.isPresent) {
                    header("Authorization", "token ${githubToken.get()}")
                }
            }
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() > 399) {
            throw RuntimeException("Failed to get pull requests: $uri ${response.statusCode()} ${response.body()}")
        }
        return Gson().fromJson(response.body(), klass)
    }

    private fun getUserInfo(login: String): GitHubUser {
        val uri = "https://api.github.com/users/$login"
        return invokeGitHubApi(uri, GitHubUser::class.java)
    }

    private fun getMergedContributorPullRequests(pageNumber: Int): List<GitHubPullRequest> {
        val uri = "https://api.github.com/search/issues?q=is:pr+is:merged+repo:gradle/gradle+label:%22from:contributor%22&sort=updated&order=desc&per_page=$pageSize&page=$pageNumber"
        return invokeGitHubApi(uri, GitHubPullRequestSearchResult::class.java).items
    }
}

open class CheckContributorsInReleaseNote : AbstractCheckOrUpdateContributorsInReleaseNote() {
    @TaskAction
    fun check() {
        val contributorsInReleaseNote = getContributorsInReleaseNote().map { it.login }.sorted()
        val contributorsFromPullRequests = getContributorsFromPullRequests().map { it.login }.sorted()

        if (contributorsFromPullRequests != contributorsInReleaseNote) {
            throw IllegalStateException(
                """The contributors in the release note $releaseNote don't match the contributors in the PRs.
                Release note:  $contributorsInReleaseNote
                Pull requests: $contributorsFromPullRequests

                You can run `./gradlew updateContributorsInReleaseNote` to update the release note with correct contributors automatically.
                """.trimIndent()
            )
        }
    }
}

open class UpdateContributorsInReleaseNote : AbstractCheckOrUpdateContributorsInReleaseNote() {
    @TaskAction
    fun update() {
        val contributorsInReleaseNote = getContributorsInReleaseNote().associateBy { it.login }
        val contributorsFromPullRequests = getContributorsFromPullRequests().associateBy { it.login }

        val unrecognizedContributors = contributorsFromPullRequests.keys - contributorsInReleaseNote.keys
        if (unrecognizedContributors.isNotEmpty()) {
            val contributorsToUpdate = contributorsInReleaseNote + unrecognizedContributors.map { it to contributorsFromPullRequests[it]!! }
            val textBeforeContributorsSection = contributorSectionRegex.find(releaseNote.asFile.get().readText())!!.groupValues[1]
            val textAfterContributorsSection = contributorSectionRegex.find(releaseNote.asFile.get().readText())!!.groupValues[3]
            releaseNote.asFile.get().writeText(
                "$textBeforeContributorsSection${
                    contributorsToUpdate.entries.joinToString(",\n") { "[${it.value.name ?: it.key}](https://github.com/${it.key})" }
                }\n$textAfterContributorsSection"
            )
        } else {
            println("Contributors in the release note are up to date.")
        }
    }
}
