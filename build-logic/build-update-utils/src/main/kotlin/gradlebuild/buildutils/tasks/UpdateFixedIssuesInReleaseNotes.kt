/*
 * Copyright 2025 the original author or authors.
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
import com.google.gson.annotations.SerializedName
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


data class GitHubIssueSearchResult(val items: List<GitHubIssue>)


data class GitHubIssue(
    val number: Int,
    val title: String,
    @SerializedName("html_url") val htmlUrl: String
)


const val FIXED_ISSUES_INTRO = "The following issues were resolved:"


@DisableCachingByDefault(because = "Depends on GitHub API")
abstract class UpdateFixedIssuesInReleaseNotes : DefaultTask() {

    @get:Internal
    abstract val releaseNotes: RegularFileProperty

    @get:Option(option = "milestone", description = "The milestone to fetch fixed issues for, e.g. '9.0.1'")
    @get:Internal
    abstract val milestone: Property<String>

    @get:Internal
    abstract val githubToken: Property<String>

    @TaskAction
    fun update() {
        if (!milestone.isPresent) {
            error("Milestone not set: please rerun the task with `--milestone <milestone>`")
        }
        val version = milestone.get()
        val parts = version.split(".")
        require(parts.size == 3 && parts[2].toIntOrNull()?.let { it != 0 } == true) {
            "Version '$version' is not a patch release. Expected format: x.y.z with z > 0."
        }
        val issues = getFixedIssues()
        require(!issues.isEmpty()) {
            "No fixed issues found for milestone ${milestone.get()}"
        }
        val issuesList = issues.joinToString("\n") { "- [${it.title}](${it.htmlUrl})" }
        updateReleaseNotes(issuesList)
    }

    private
    fun getFixedIssues(): List<GitHubIssue> {
        val issues = mutableListOf<GitHubIssue>()
        for (pageNumber in 1..10) {
            val page = getIssuePage(pageNumber)
            issues.addAll(page)
            if (page.size < PAGE_SIZE) break
        }
        return issues.sortedBy { it.number }
    }

    private
    fun getIssuePage(pageNumber: Int): List<GitHubIssue> {
        val encodedMilestone = milestone.get().replace(" ", "+")
        val uri = "https://api.github.com/search/issues?q=is:issue+is:closed+repo:gradle/gradle+milestone:$encodedMilestone&per_page=$PAGE_SIZE&page=$pageNumber"
        val requestBuilder = HttpRequest.newBuilder().uri(URI(uri))
        if (githubToken.isPresent) {
            requestBuilder.header("Authorization", "token ${githubToken.get()}")
        }
        val response = HttpClient.newHttpClient().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() > 399) {
            throw RuntimeException("Failed to get issues: $uri ${response.statusCode()} ${response.body()}")
        }
        return Gson().fromJson(response.body(), GitHubIssueSearchResult::class.java).items
    }

    private
    fun updateReleaseNotes(issuesList: String) {
        val notesFile = releaseNotes.asFile.get()
        val content = notesFile.readText()
        val newSection = "$FIXED_ISSUES_INTRO\n\n$issuesList\n"

        val newContent = if (content.contains(FIXED_ISSUES_INTRO)) {
            val introStart = content.indexOf(FIXED_ISSUES_INTRO)
            val afterIntro = introStart + FIXED_ISSUES_INTRO.length
            // Find the end of the issues list: the first non-list line after the intro
            val listEnd = findEndOfListSection(content, afterIntro)
            content.substring(0, introStart) + newSection + "\n" + content.substring(listEnd)
        } else {
            newSection + "\n" + content
        }

        notesFile.writeText(newContent)
        println("Fixed issues section updated in $notesFile")
    }

    private
    fun findEndOfListSection(content: String, startAfterIntro: Int): Int {
        val lines = content.substring(startAfterIntro).lines()
        var pos = startAfterIntro
        var pastBlankLine = false
        for (line in lines) {
            if (pastBlankLine && !line.startsWith("- ") && line.isNotBlank()) {
                return pos
            }
            if (line.isNotBlank()) {
                pastBlankLine = true
            }
            pos += line.length + 1 // +1 for the newline
        }
        return content.length
    }
}
