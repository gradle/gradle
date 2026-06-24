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

package gradlebuild.removal.action

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject


/**
 * Resolves a commit that is **known to exist on `gradle/gradle`**, so the report's GitHub source links
 * don't 404 for local-only branch commits.
 *
 * Order of preference:
 *  1. The CI-provided commit (`BUILD_COMMIT_ID` / `BUILD_VCS_NUMBER`) — the exact commit being built, which
 *     is always pushed.
 *  2. `git merge-base HEAD <ref>` against the main-line branches of every remote whose URL points at
 *     `gradle/gradle` (matched by URL, not name, so a fork named `origin` is correctly ignored). The
 *     merge-base is the most recent commit the working tree shares with the canonical repo, so line numbers
 *     stay accurate for files the branch hasn't touched.
 *  3. Local `HEAD` as a last resort (may 404 if unpushed, but nothing better is determinable).
 */
abstract class UpstreamCommitValueSource : ValueSource<String, UpstreamCommitValueSource.Params> {

    interface Params : ValueSourceParameters {
        /** Repository root to run git in (handles worktrees and subproject working dirs). */
        val workingDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        ciCommit()?.let { return it }

        for (ref in upstreamMainLineRefs()) {
            val base = git("merge-base", "HEAD", ref).firstLineOrNull()
            if (base != null) {
                return base
            }
        }

        // Last resort: local HEAD. Valid on CI (HEAD is the pushed build commit); may 404 for a purely
        // local branch with no resolvable upstream remote.
        return git("rev-parse", "HEAD").firstLineOrNull() ?: "HEAD"
    }

    private
    fun ciCommit(): String? =
        sequenceOf("BUILD_COMMIT_ID", "BUILD_VCS_NUMBER")
            .mapNotNull { System.getenv(it)?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()

    /** `<remote>/{main,master,release}` for every remote whose URL is the canonical `gradle/gradle` repo. */
    private
    fun upstreamMainLineRefs(): List<String> =
        canonicalRemotes().flatMap { remote -> listOf("$remote/main", "$remote/master", "$remote/release") }

    private
    fun canonicalRemotes(): List<String> =
        git("remote", "-v").lineSequence()
            .filter { GRADLE_REPO.containsMatchIn(it) }
            .mapNotNull { it.substringBefore('\t').trim().takeIf(String::isNotEmpty) }
            .distinct()
            .toList()

    private
    fun git(vararg args: String): String {
        val stdout = ByteArrayOutputStream()
        return try {
            execOperations.exec {
                commandLine(listOf("git") + args)
                workingDir = parameters.workingDir.get().asFile
                standardOutput = stdout
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
            stdout.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            // git not on PATH, not a repository, etc. — degrade gracefully.
            ""
        }
    }

    private
    fun String.firstLineOrNull(): String? = lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }

    companion object {
        // Matches https/ssh/scp forms of gradle/gradle(.git), e.g. git@github.com:gradle/gradle.git
        private val GRADLE_REPO = Regex("""[:/]gradle/gradle(\.git)?(\s|$)""")
    }
}
