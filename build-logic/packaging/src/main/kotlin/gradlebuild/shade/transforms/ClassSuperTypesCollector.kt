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

package gradlebuild.shade.transforms

import gradlebuild.basics.classanalysis.getClassSuperTypes
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileType
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import java.util.Properties
import javax.inject.Inject


@CacheableTransform
abstract class ClassSuperTypesCollector : TransformAction<ClassSuperTypesCollector.Parameters> {

    companion object {
        val logging: Logger = Logging.getLogger(ClassSuperTypesCollector::class.java)
    }

    interface Parameters : TransformParameters {
        @get:Internal
        val rootDir: Property<String>
    }

    @get:Inject
    abstract val inputChanges: InputChanges

    @get:Classpath
    @get:InputArtifact
    abstract val classesDir: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val path = classesDir.get().asFile.path
            .replace(parameters.rootDir.get(), "")
            .replace("/", ".")
            .removePrefix(".subprojects")
            .removePrefix(".")
        val outputFile = outputs.file("$path.classes.properties")
        val properties = Properties()
        if (outputFile.exists()) {
            outputFile.inputStream().use {  properties.load(it) }
        } else {
            outputFile.createNewFile()
        }
        inputChanges.getFileChanges(classesDir).forEach { change ->
            if (change.fileType != FileType.FILE || !change.normalizedPath.endsWith(".class")) {
                return@forEach
            }
            val className = change.normalizedPath.removeSuffix(".class")
            when (change.changeType) {
                ChangeType.ADDED, ChangeType.MODIFIED -> {
                    val superTypes = getClassSuperTypes(change.file.toPath()).filter { it.startsWith("org/gradle") }
                    if (superTypes.isNotEmpty()) {
                        properties.setProperty(className, superTypes.joinToString(","))
                    }
                }
                ChangeType.REMOVED -> properties.remove(className)
                else -> {}
            }
        }
        outputFile.outputStream().use { properties.store(it, null) }
    }
}
