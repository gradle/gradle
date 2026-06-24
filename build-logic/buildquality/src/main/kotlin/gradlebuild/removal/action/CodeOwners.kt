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

import java.io.File


/**
 * A parsed `.github/CODEOWNERS` file, used to map a source path to its owning Gradle team(s).
 *
 * Follows the GitHub CODEOWNERS semantics that matter here: comments (`#`) are stripped, patterns are
 * gitignore-style (leading `/` anchors to the repo root, a trailing `/` matches a directory's contents,
 * `*` does not cross `/`, `**` does), and the **last** matching rule wins. Only `@gradle/<team>` owners
 * are retained (individual `@user`/email owners are ignored); a rule with no team owner (e.g. one whose
 * owner is commented out) makes the path unowned, overriding any earlier rule.
 */
class CodeOwners(private val rules: List<Rule>) {

    class Rule(private val regex: Regex, val teams: List<String>) {
        fun matches(path: String): Boolean = regex.matches(path)
    }

    /** The owning team(s) for [path] (repo-relative, `/`-separated), or empty if unowned. */
    fun teamsFor(path: String): List<String> =
        rules.lastOrNull { it.matches(path) }?.teams ?: emptyList()

    companion object {
        private const val TEAM_PREFIX = "@gradle/"

        fun parse(file: File): CodeOwners =
            if (file.isFile) parse(file.readLines()) else CodeOwners(emptyList())

        fun parse(lines: List<String>): CodeOwners = CodeOwners(
            lines.mapNotNull { raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@mapNotNull null
                val tokens = line.split(WHITESPACE)
                val teams = tokens.drop(1)
                    .filter { it.startsWith(TEAM_PREFIX) }
                    .map { it.removePrefix(TEAM_PREFIX) }
                Rule(patternToRegex(tokens.first()), teams)
            }
        )

        private val WHITESPACE = Regex("\\s+")

        /** Translates a CODEOWNERS pattern into a regex matching any repo-relative file path it owns. */
        private fun patternToRegex(pattern: String): Regex {
            var p = pattern
            val anchored = p.startsWith("/")
            if (anchored) p = p.substring(1)
            if (p.endsWith("/")) p = p.dropLast(1)

            val core = buildString {
                var i = 0
                while (i < p.length) {
                    val c = p[i]
                    when (c) {
                        '*' -> if (i + 1 < p.length && p[i + 1] == '*') {
                            append(".*"); i++
                        } else {
                            append("[^/]*")
                        }
                        '?' -> append("[^/]")
                        '.', '(', ')', '+', '|', '^', '$', '{', '}', '[', ']', '\\' -> append('\\').append(c)
                        else -> append(c)
                    }
                    i++
                }
            }
            // A non-anchored pattern may match at any directory depth; a directory pattern matches its
            // contents. Since we only ever match file paths, "the path itself or anything under it" covers both.
            val prefix = if (anchored) "" else "(?:.*/)?"
            return Regex("^$prefix$core(?:/.*)?$")
        }
    }
}
