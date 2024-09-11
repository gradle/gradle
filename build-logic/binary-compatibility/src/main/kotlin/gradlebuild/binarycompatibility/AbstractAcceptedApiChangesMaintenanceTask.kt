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

import com.google.common.annotations.VisibleForTesting
import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File


/**
 * Abstract base class for [Task][org.gradle.api.Task]s that verify and manipulate the given accepted API changes file.
 */
@CacheableTask
abstract class AbstractAcceptedApiChangesMaintenanceTask : DefaultTask() {

    /**
     * A directory that contains jsons with accepted API changes.
     * Any json is a directory is considered to contain accepted API changes.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apiChangesDirectory: DirectoryProperty

    protected
    fun loadChanges(): Map<File, List<AcceptedApiChange>> {
        return apiChangesDirectory.get().asFile.listFiles()
            ?.filter {
                it.name.endsWith(".json")
            }?.associate {
                it to it.readAcceptedChanges()
            }?.filterValues {
                it.acceptedApiChanges != null
            }?.mapValues {
                it.value.acceptedApiChanges!!
            } ?: emptyMap()
    }

    private
    fun File.readAcceptedChanges(): AcceptedApiChanges {
        val jsonString = this.readText()
        return Gson().fromJson(jsonString, AcceptedApiChanges::class.java)
    }

    /**
     * Sorts the given list of [AcceptedApiChange]s by type and member.
     * <p>
     * This sort ought to remain consistent with the sort used by the [EnrichedReportRenderer].
     */
    @Suppress("KDocUnresolvedReference")
    protected
    fun sortChanges(originalChanges: List<AcceptedApiChange>) = originalChanges.toMutableList().sortedBy { it.type + '#' + it.member }

    @VisibleForTesting
    data class AcceptedApiChange(
        val type: String?,
        val member: String?,
        val acceptation: String?,
        val changes: List<String>?
    ) {
        override fun toString(): String = "Type: '$type', Member: '$member'"
    }

    @VisibleForTesting
    data class AcceptedApiChanges(
        val acceptedApiChanges: List<AcceptedApiChange>?
    )
}
