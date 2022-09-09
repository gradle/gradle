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
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction


/**
 * This [Task][org.gradle.api.Task] checks that the contents of a given accepted API changes file
 * are present in alphabetical order (by type, then member), and throws an exception and reports
 * any changes that are not.
 */
@CacheableTask
abstract class AlphabeticalAcceptedApiChangesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apiChangesFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val jsonString = apiChangesFile.get().asFile.readText()
        val json = Gson().fromJson(jsonString, AcceptedApiChanges::class.java)

        val originalChanges = json.acceptedApiChanges!!
        val sortedChanges = originalChanges.toMutableList().sortedBy { it.type + '#' + it.member }

        val mismatches = originalChanges.filterIndexed { index, acceptedApiChange -> sortedChanges[index] != acceptedApiChange }
        if (mismatches.isNotEmpty()) {
            val formattedMismatches = mismatches.joinToString(separator = "\n", transform = { "\t" + it })
            throw GradleException("API changes in file '${apiChangesFile.get().asFile.name}' should be in alphabetical order (by type and member), yet these changes were not:\n$formattedMismatches")
        }
    }

    private
    data class AcceptedApiChange(
        val type: String?,
        val member: String?,
        val acceptation: String?,
        val changes: List<String>?
    ) {
        override fun toString(): String = "Type: '$type', Member: '$member'"
    }

    private
    data class AcceptedApiChanges(
        val acceptedApiChanges: List<AcceptedApiChange>?
    )
}
