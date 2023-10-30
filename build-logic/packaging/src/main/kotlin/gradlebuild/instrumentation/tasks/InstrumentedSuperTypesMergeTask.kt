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

import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform.Companion.DIRECT_SUPER_TYPES_FILE
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform.Companion.INSTRUMENTED_CLASSES_FILE
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.ArrayDeque
import java.util.Properties
import java.util.Queue


/**
 * Merges all instrumented super types from multiple projects in to one file.
 */
@CacheableTask
abstract class InstrumentedSuperTypesMergeTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val instrumentationMetadataDirs: ConfigurableFileCollection

    /**
     * Output with all instrumented super types, merged from multiple projects in to one file.
     */
    @get:OutputFile
    abstract val instrumentedSuperTypes: RegularFileProperty

    @TaskAction
    fun mergeInstrumentedSuperTypes() {
        val instrumentedClasses = findInstrumentedClasses()
        if (instrumentedClasses.isEmpty()) {
            instrumentedSuperTypes.asFile.get().delete()
            return
        }

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
        if (onlyInstrumentedSuperTypes.isEmpty()) {
            // If there is no instrumented types just don't output any file
            outputFile.delete()
        } else {
            outputFile.writer().use {
                onlyInstrumentedSuperTypes.toSortedMap().forEach { (className, superTypes) ->
                    it.write("$className=${superTypes.sorted().joinToString(",")}\n")
                }
            }
        }
    }
}
