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

package gradlebuild.instrumentation.transforms

import gradlebuild.basics.classanalysis.getClassSuperTypes
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileType
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.ChangeType.ADDED
import org.gradle.work.ChangeType.MODIFIED
import org.gradle.work.ChangeType.REMOVED
import org.gradle.work.FileChange
import org.gradle.work.InputChanges
import java.io.File
import java.util.Properties
import javax.inject.Inject


@CacheableTransform
abstract class InstrumentationMetadataTransform : TransformAction<TransformParameters.None> {

    companion object {
        const val INSTRUMENTATION_METADATA = "instrumentationMetadata"
        const val DIRECT_SUPER_TYPES_FILE = "direct-super-types.properties"
        const val INSTRUMENTED_CLASSES_FILE = "instrumented-classes.txt"
        const val UPGRADED_PROPERTIES_FILE = "upgraded-properties.json"
    }

    @get:Inject
    abstract val inputChanges: InputChanges

    @get:Classpath
    @get:InputArtifact
    abstract val classesDir: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val oldSuperTypes = Properties()
        val outputDir = outputs.dir("instrumentation")
        val superTypesFile = File(outputDir, DIRECT_SUPER_TYPES_FILE)
        if (superTypesFile.exists()) {
            superTypesFile.inputStream().use { oldSuperTypes.load(it) }
        }
        val instrumentedClassesFile = File(outputDir, INSTRUMENTED_CLASSES_FILE)
        val upgradedPropertiesFile = File(outputDir, UPGRADED_PROPERTIES_FILE)

        // Find changes
        val (newSuperTypes, newInstrumentedClassesFile, newUpgradedPropertiesFile) = findChanges(
            oldSuperTypes,
            instrumentedClassesFile,
            upgradedPropertiesFile
        )

        // Print output
        superTypesFile.outputStream().use { newSuperTypes.store(it, null) }
        newInstrumentedClassesFile.copyTo(instrumentedClassesFile, defaultValue = "")
        newUpgradedPropertiesFile.copyTo(upgradedPropertiesFile, defaultValue = "[]")
    }

    private
    fun findChanges(oldSuperTypes: Properties, oldInstrumentedClassesFile: File, oldUpgradedPropertiesFile: File): Triple<Properties, File?, File?> {
        val superTypes = Properties().apply { putAll(oldSuperTypes) }
        var instrumentedClassesFile: File? = oldInstrumentedClassesFile
        var upgradedPropertiesFile: File? = oldUpgradedPropertiesFile
        inputChanges.getFileChanges(classesDir)
            .filter { change -> change.fileType == FileType.FILE }
            .forEach { change ->
                when {
                    change.normalizedPath.endsWith(".class") -> handleClassChange(change, superTypes)
                    // "instrumented-classes.txt" is always just one in a classes dir
                    change.file.name.equals(INSTRUMENTED_CLASSES_FILE) -> instrumentedClassesFile = handleInstrumentedMetadataFileChange(change)
                    // "upgraded-properties.json" is always just one in a classes dir
                    change.file.name.equals(UPGRADED_PROPERTIES_FILE) -> upgradedPropertiesFile = handleInstrumentedMetadataFileChange(change)
                }
            }
        return Triple(superTypes, instrumentedClassesFile, upgradedPropertiesFile)
    }

    private
    fun handleClassChange(change: FileChange, superTypes: Properties) {
        val className = change.normalizedPath.removeSuffix(".class")
        when (change.changeType) {
            ADDED, MODIFIED -> {
                // Add also className itself, so we collect all classes
                val classSuperTypes = (change.file.getClassSuperTypes() + className).filter { it.startsWith("org/gradle") }
                superTypes.setProperty(className, classSuperTypes.joinToString(","))
            }
            REMOVED -> superTypes.remove(className)
        }
    }

    private
    fun handleInstrumentedMetadataFileChange(change: FileChange): File? {
        return when (change.changeType) {
            ADDED, MODIFIED -> change.file
            REMOVED -> null
        }
    }

    private
    fun File?.copyTo(other: File, defaultValue: String) {
        when {
            // Write default value if this doesn't exist
            this == null || !this.exists() -> other.writeText(defaultValue)
            // Copy to other, but don't allow overwriting self
            this != other -> this.copyTo(other, overwrite = true)
        }
    }
}
