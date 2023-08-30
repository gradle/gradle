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

package gradlebuild.instrumentation.tasks

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform.Companion.DIRECT_SUPER_TYPES_FILE
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform.Companion.INSTRUMENTED_CLASSES_FILE
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform.Companion.UPGRADED_PROPERTIES_FILE
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.hash.Hashing
import java.io.File
import java.io.FileReader
import java.util.ArrayDeque
import java.util.Properties
import java.util.Queue


@CacheableTask
abstract class InstrumentedMetadataMergeTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val instrumentationMetadataDirs: ConfigurableFileCollection

    @get:OutputFile
    abstract val instrumentedSuperTypes: RegularFileProperty

    @get:OutputFile
    abstract val instrumentedSuperTypesHash: RegularFileProperty

    @get:OutputFile
    abstract val upgradedProperties: RegularFileProperty

    @get:OutputFile
    abstract val upgradedPropertiesHash: RegularFileProperty

    @TaskAction
    fun run() {
        val instrumentedClasses = findInstrumentedClasses()
        if (instrumentedClasses.isEmpty()) {
            instrumentedSuperTypes.asFile.get().toEmptyFile()
            instrumentedSuperTypesHash.asFile.get().toEmptyFile()
            upgradedProperties.asFile.get().toEmptyFile()
            upgradedPropertiesHash.asFile.get().toEmptyFile()
            return
        }

        mergeAndWriteInstrumentedSuperTypes(instrumentedClasses)
        mergeAndWriteUpgradedProperties()
    }

    private
    fun mergeAndWriteInstrumentedSuperTypes(instrumentedClasses: Set<String>) {
        // Merge and find all transitive super types
        val superTypes = mergeSuperTypes()
        // Keep only instrumented super types as we don't need to others for instrumentation
        val onlyInstrumentedSuperTypes = keepOnlyInstrumentedSuperTypes(superTypes, instrumentedClasses)
        writeSuperTypes(onlyInstrumentedSuperTypes)
    }

    /**
     * Finds all instrumented classes from `instrumented-classes.txt` file.
     * That file is created by instrumentation annotation processor and artifact transform.
     * See :internal-instrumentation-processor project and [gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform].
     */
    private
    fun findInstrumentedClasses(): Set<String> {
        return instrumentationMetadataDirs
            .map { it.resolve(INSTRUMENTED_CLASSES_FILE) }
            .filter { it.exists() }
            .flatMap { it.readLines() }
            .toSet()
    }

    private
    fun mergeSuperTypes(): Map<String, Set<String>> {
        // Merge all super types files into a single map
        val directSuperTypes = mutableMapOf<String, MutableSet<String>>()
        instrumentationMetadataDirs
            .map { it.resolve(DIRECT_SUPER_TYPES_FILE) }
            .filter { it.exists() }
            .forEach { file ->
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

    private
    fun computeAllSuperTypes(className: String, directSuperTypes: Map<String, Set<String>>): Set<String> {
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

    private
    fun keepOnlyInstrumentedSuperTypes(superTypes: Map<String, Set<String>>, instrumentedClasses: Set<String>): Map<String, Set<String>> {
        return superTypes.mapValues {
            it.value.filter { superType -> instrumentedClasses.contains(superType) }.toSet()
        }.filter { it.value.isNotEmpty() }
    }

    private
    fun writeSuperTypes(onlyInstrumentedSuperTypes: Map<String, Set<String>>) {
        val outputFile = instrumentedSuperTypes.asFile.get()
        val outputHashFile = instrumentedSuperTypesHash.asFile.get()
        if (onlyInstrumentedSuperTypes.isEmpty()) {
            // If there is no instrumented types just create an empty file
            outputFile.toEmptyFile()
            outputHashFile.toEmptyFile()
        } else {
            val properties = Properties()
            onlyInstrumentedSuperTypes.forEach { (className, superTypes) ->
                properties.setProperty(className, superTypes.joinToString(","))
            }
            outputFile.outputStream().use { properties.store(it, null) }

            val hasher = Hashing.defaultFunction().newHasher()
            onlyInstrumentedSuperTypes.entries
                .sortedBy { it.key }
                .forEach { hasher.putString(it.key + "=" + it.value.sorted().joinToString(",")) }
            outputHashFile.outputStream().use { it.write(hasher.hash().toByteArray()) }
        }
    }

    private
    fun mergeAndWriteUpgradedProperties() {
        // Merge and find all upgraded properties
        val mergedUpgradedProperties = mergeProperties()
        if (mergedUpgradedProperties.isEmpty) {
            upgradedProperties.asFile.get().toEmptyFile()
            upgradedPropertiesHash.asFile.get().toEmptyFile()
            return
        }

        upgradedProperties.asFile.get().writer().use { Gson().toJson(mergedUpgradedProperties, it) }
        val hasher = Hashing.newHasher()
        mergedUpgradedProperties.map { it.asJsonObject.get("hash").asString }.sorted().forEach { hasher.putString(it) }
        upgradedPropertiesHash.asFile.get().outputStream().use { it.write(hasher.hash().toByteArray()) }
    }

    private
    fun mergeProperties(): JsonArray {
        val merged = JsonArray()
        instrumentationMetadataDirs
            .map { it.resolve(UPGRADED_PROPERTIES_FILE) }
            .filter { it.exists() }
            .sorted()
            .map { JsonParser.parseReader(FileReader(it)).asJsonArray }
            .forEach { merged.addAll(it) }
        return merged
    }

    private
    fun File.toEmptyFile() {
        this.delete()
        this.createNewFile()
    }
}
