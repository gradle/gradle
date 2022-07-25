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


val contributorLineRegex = "\\[(.*)]\\(https://github.com/(.*)\\)".toRegex()


const val pageSize = 100


@DisableCachingByDefault(because = "Depends on GitHub API")
abstract class AbstractCheckOrUpdateContributorsInReleaseNotes : DefaultTask() {
    @get: Internal
    abstract val releaseNotes: RegularFileProperty

    @get: Option(option = "milestone", description = "The milestone prefix to check for contributors, i.e. '7.5' includes PRs with '7.5', '7.5 RC1', '7.5.1'")
    @get: Internal
    abstract val milestone: Property<String>

    @get: Internal
    abstract val githubToken: Property<String>

    @Internal
    protected
    fun getContributorsInReleaseNotes(): List<GitHubUser> {
        val releaseNotesLines = parseReleaseNotes()
        return releaseNotesLines.contributorLines
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .onEach { check(contributorLineRegex.containsMatchIn(it)) { "Invalid contributor line: $it" } }
            .map { GitHubUser(contributorLineRegex.find(it)!!.groupValues[2], contributorLineRegex.find(it)!!.groupValues[1]) }
    }

    /**
     * Parses the release notes file and returns the triple: (linesBeforeContributors, contributorLines, linesAfterContributors)
     */
    protected
    fun parseReleaseNotes(): ReleaseNotesLines {
        val releaseNotesLines: List<String> = releaseNotes.asFile.get().readLines()
        val contributorSectionBeginIndex = releaseNotesLines.indexOfFirst { it.startsWith("We would like to thank the following community members for their contributions to this release of Gradle:") } + 1

        check(contributorSectionBeginIndex != 0) { "Can't find the contributors section in the release notes $releaseNotes." }

        val contributorSectionEndIndex = (contributorSectionBeginIndex until releaseNotesLines.size).firstOrNull {
            val line = releaseNotesLines[it].trim()
            line.isNotEmpty() && !line.startsWith("[")
        } ?: error("Can't find the contributors section end in the release notes $releaseNotes.")

        return ReleaseNotesLines(
            releaseNotesLines.subList(0, contributorSectionBeginIndex).toList(),
            releaseNotesLines.subList(contributorSectionBeginIndex, contributorSectionEndIndex).toList(),
            releaseNotesLines.subList(contributorSectionEndIndex, releaseNotesLines.size).toList()
        )
    }

    @Internal
    protected
    fun getContributorsFromPullRequests(): Set<GitHubUser> {
        check(milestone.isPresent) { "Milestone not set: please rerun the task with `--milestone <milestone>`" }
        val milestone = milestone.get()

        val prs: MutableList<GitHubPullRequest> = mutableListOf()
        (1..10).forEach { page ->
            val prPage = getMergedContributorPullRequests(page)
            prs.addAll(prPage)
            if (prPage.size < pageSize) {
                return@forEach
            }
        }

        return prs
            .filter { it.milestone?.title?.startsWith(milestone) == true }
            .map { it.user.login }
            .toSet()
            .map { getUserInfo(it) }
            .toSet()
    }

    private
    fun <T> invokeGitHubApi(uri: String, klass: Class<T>): T {
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

    private
    fun getUserInfo(login: String): GitHubUser {
        val uri = "https://api.github.com/users/$login"
        return invokeGitHubApi(uri, GitHubUser::class.java)
    }

    private
    fun getMergedContributorPullRequests(pageNumber: Int): List<GitHubPullRequest> {
        val uri = "https://api.github.com/search/issues?q=is:pr+is:merged+repo:gradle/gradle+label:%22from:contributor%22&sort=updated&order=desc&per_page=$pageSize&page=$pageNumber"
        return invokeGitHubApi(uri, GitHubPullRequestSearchResult::class.java).items
    }

    protected data class ReleaseNotesLines(
        val linesBeforeContributors: List<String>,
        val contributorLines: List<String>,
        val linesAfterContributors: List<String>,
    )

    companion object {

        @JvmStatic
        protected
        val GitHubUser.displayName: String get() = name ?: login

        /**
         * Names in the release notes could have been manually entered for attribution.
         */
        @JvmStatic
        protected
        fun Collection<GitHubUser>.overrideNames(others: Collection<GitHubUser>): List<GitHubUser> {
            val displayNameFromReleaseNotesByLogin = others.associate { it.login to it.name }
            return map { it.copy(name = displayNameFromReleaseNotesByLogin[it.login] ?: it.name) }
        }


        /**
         * Contributors list should be alphabetized by the resulting visible name.
         */
        @JvmStatic
        protected
        fun Collection<GitHubUser>.sortedForReleaseNotes(): List<GitHubUser> = sortedBy { it.displayName }
    }
}
