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

package gradlebuild.binarycompatibility

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction


/**
 * This [Task][org.gradle.api.Task] reorders the changes in an accepted API changes file
 * so that they are alphabetically sorted (by type, then member).
 */
@CacheableTask
abstract class SortAcceptedApiChangesTask : AbstractAcceptedApiChangesMaintenanceTask() {

    @TaskAction
    fun execute() {
        val sortedChanges = sortChanges(loadChanges())
        val json = formatChanges(sortedChanges)
        apiChangesFile.asFile.get().bufferedWriter().use { out -> out.write(json) }
    }

    private
    fun formatChanges(changes: List<AbstractAcceptedApiChangesMaintenanceTask.AcceptedApiChange>): String {
        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        val initialString = gson.toJson(AcceptedApiChanges(changes))
        return adjustIndentation(initialString) + "\n"
    }

    /**
     * It appears there is no way to configure Gson to use 4 spaces instead of 2 for indentation.
     *
     * See: https://github.com/google/gson/blob/master/UserGuide.md#TOC-Compact-Vs.-Pretty-Printing-for-JSON-Output-Format
     */
    private
    fun adjustIndentation(initalJsonString: String): String {
        val indentationRegex = """^\s+""".toRegex(RegexOption.MULTILINE)
        return indentationRegex.replace(initalJsonString) { m ->
            " ".repeat(m.value.length * 2)
        }
    }
}
