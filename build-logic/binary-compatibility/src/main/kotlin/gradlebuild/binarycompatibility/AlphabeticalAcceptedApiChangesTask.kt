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

import org.gradle.api.GradleException
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import java.io.File


/**
 * This [Task][org.gradle.api.Task] checks that the contents of a given accepted API changes files
 * are present in alphabetical order (by type, then member), and throws an exception and reports
 * any changes that are not.
 */
@CacheableTask
abstract class AlphabeticalAcceptedApiChangesTask : AbstractAcceptedApiChangesMaintenanceTask() {

    @TaskAction
    fun execute() {
        val originalChanges = loadChanges()
        val sortedChanges = originalChanges.mapValues {
            sortChanges(it.value)
        }

        val mismatches = originalChanges.mapValues {
            findMismatches(it.value, sortedChanges[it.key]!!)
        }.filterValues {
            it.isNotEmpty()
        }

        if (mismatches.isNotEmpty()) {
            val formattedMismatches = mismatches.mapValues { mismatch ->
                mismatch.value.joinToString(separator = "\n", transform = { "\t" + it })
            }
            throw GradleException(buildErrorMessage(formattedMismatches))
        }
    }

    private
    fun findMismatches(originalChanges: List<AcceptedApiChange>, sortedChanges: List<AcceptedApiChange>): List<AcceptedApiChange> {
        return originalChanges.filterIndexed { index, acceptedApiChange -> sortedChanges[index] != acceptedApiChange }
    }

    private
    fun buildErrorMessage(formattedMismatches: Map<File, String>): String {
        val messages = formattedMismatches.map {
            "API changes in file '${it.key.name}' should be in alphabetical order (by type and member), yet these changes were not:\n" +
                "$formattedMismatches\n"
        }.joinToString(separator = "\n")

        return "$messages\nTo automatically alphabetize these changes run: './gradlew :architecture-test:sortAcceptedApiChanges'"
    }
}
