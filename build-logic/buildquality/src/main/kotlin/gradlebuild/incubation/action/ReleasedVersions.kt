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

package gradlebuild.incubation.action

import java.io.File


/**
 * Shared parsing of the `released-versions.json` file used by both the per-project producer
 * (to attach release dates) and the aggregator (to compute the age of an incubating API in
 * terms of minor releases). The file is parsed line-by-line in the same lenient way it has
 * always been, to avoid pulling in a JSON dependency into the worker classpath.
 */
object ReleasedVersions {

    data class ReleasedVersion(val version: String, val date: String)

    /**
     * All released versions in the order they appear in the file (newest first), each with its
     * release date formatted as `yyyy-MM-dd`.
     */
    fun parse(releasedVersionsFile: File): List<ReleasedVersion> {
        val result = mutableListOf<ReleasedVersion>()
        var version: String? = null
        releasedVersionsFile.forEachLine(Charsets.UTF_8) {
            val line = it.trim()
            if (line.startsWith("\"version\"")) {
                version = line.substring(line.indexOf("\"", 11) + 1, line.lastIndexOf("\""))
            }
            if (line.startsWith("\"buildTime\"")) {
                val raw = line.substring(line.indexOf("\"", 12) + 1, line.lastIndexOf("\""))
                val date = raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8)
                version?.let { v -> result.add(ReleasedVersion(v, date)) }
            }
        }
        return result
    }

    /**
     * Map of full version string (e.g. `8.4.1`) to its release date.
     */
    fun versionDates(releasedVersionsFile: File): Map<String, String> =
        parse(releasedVersionsFile).associate { it.version to it.date }

    /**
     * The `major.minor` part of a version string, e.g. `9.7.0` -> `9.7`, `9.6.0-rc-1` -> `9.6`.
     */
    fun toMinor(version: String): String {
        val parts = version.split('.')
        return when {
            parts.size >= 2 -> "${parts[0]}.${parts[1].takeWhile { it.isDigit() }}"
            else -> version
        }
    }
}
