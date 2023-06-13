/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.shade.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.ArrayDeque
import java.util.Properties
import java.util.Queue

@CacheableTask
abstract class InstrumentationMergeSuperTypesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val superTypesFiles: ConfigurableFileCollection

    @get:Input
    abstract val instrumentedClasses: SetProperty<String>

    @get:OutputFile
    abstract val instrumentedSuperTypes: RegularFileProperty

    @TaskAction
    fun run() {
        val superTypes = mergeSuperTypes()
        val onlyInstrumentedSuperTypes = keepOnlyInstrumentedSuperTypes(superTypes)
        writeOutput(onlyInstrumentedSuperTypes)
    }

    private fun mergeSuperTypes(): Map<String, Set<String>> {
        // Merge all super types files into a single map
        val directSuperTypes = mutableMapOf<String, MutableSet<String>>()
        superTypesFiles.forEach { file ->
            val properties = Properties()
            file.inputStream().use { properties.load(it) }
            properties.forEach { key, value ->
                val className = key.toString()
                val superTypeNames = value.toString().split(",")
                directSuperTypes.computeIfAbsent(className) { linkedSetOf() }.addAll(superTypeNames)
            }
        }

        // Note: superTypesFiles contains only direct super types, but no transitive ones,
        // so we have to collect also transitive super types
        return directSuperTypes.map {
            it.key to computeAllSuperTypes(it.key, directSuperTypes)
        }.toMap()
    }

    private fun computeAllSuperTypes(className: String, directSuperTypes: Map<String, Set<String>>): Set<String> {
        val superTypes: Queue<String> = ArrayDeque(directSuperTypes[className] ?: emptySet())
        val collected = mutableSetOf<String>()
        collected.add(className)
        while (!superTypes.isEmpty()) {
            val superType = superTypes.poll()
            if (collected.add(superType)) {
                superTypes.addAll(directSuperTypes[superType] ?: emptySet())
            }
        }
        return collected
    }

    private fun keepOnlyInstrumentedSuperTypes(superTypes: Map<String, Set<String>>): Map<String, Set<String>> {
        val instrumentedClasses = instrumentedClasses.get()
        return superTypes.mapValues {
            it.value.filter { superType -> instrumentedClasses.contains(superType) }.toSet()
        }.filter { it.value.isNotEmpty() }
    }

    private fun writeOutput(onlyInstrumentedSuperTypes: Map<String, Set<String>>) {
        val outputFile = instrumentedSuperTypes.asFile.get()
        if (onlyInstrumentedSuperTypes.isEmpty()) {
            // If there is no instrumented types just create an empty file
            outputFile.delete()
            outputFile.createNewFile()
        } else {
            val properties = Properties()
            onlyInstrumentedSuperTypes.forEach { (className, superTypes) ->
                properties.setProperty(className, superTypes.joinToString(","))
            }
            outputFile.outputStream().use { properties.store(it, null) }
        }
    }
}
