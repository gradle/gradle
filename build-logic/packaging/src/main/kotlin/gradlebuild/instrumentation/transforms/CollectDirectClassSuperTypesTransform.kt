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
import org.gradle.api.tasks.CompileClasspath
import org.gradle.work.ChangeType.ADDED
import org.gradle.work.ChangeType.MODIFIED
import org.gradle.work.ChangeType.REMOVED
import org.gradle.work.InputChanges
import java.util.Properties
import javax.inject.Inject


@CacheableTransform
abstract class CollectDirectClassSuperTypesTransform : TransformAction<TransformParameters.None> {

    companion object {
        const val DIRECT_SUPER_TYPES = "directSuperTypes"
    }

    @get:Inject
    abstract val inputChanges: InputChanges

    @get:CompileClasspath
    @get:InputArtifact
    abstract val classesDir: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val properties = Properties()
        val outputFile = outputs.file("direct-super-types.properties")
        if (outputFile.exists()) {
            outputFile.inputStream().use { properties.load(it) }
        }
        findChanges(properties)
        outputFile.outputStream().use { properties.store(it, null) }
    }

    private
    fun findChanges(properties: Properties) {
        inputChanges.getFileChanges(classesDir)
            .filter { change -> change.fileType == FileType.FILE && change.normalizedPath.endsWith(".class") }
            .forEach { change ->
                val className = change.normalizedPath.removeSuffix(".class")
                when (change.changeType) {
                    ADDED, MODIFIED -> {
                        // Add also className itself, so we collect all classes
                        val superTypes = (change.file.getClassSuperTypes() + className).filter { it.startsWith("org/gradle") }
                        properties.setProperty(className, superTypes.joinToString(","))
                    }
                    REMOVED -> properties.remove(className)
                }
            }
    }
}
