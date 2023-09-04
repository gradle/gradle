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
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileReader


/**
 * Merges all upgraded properties from multiple projects in to one file.
 */
@CacheableTask
abstract class UpgradedPropertiesMergeTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val instrumentationMetadataDirs: ConfigurableFileCollection

    /**
     * Output with all upgraded properties, merged from multiple projects in to one file.
     * This is also used in InstrumentingClasspathFileTransformer to invalidate instrumentation cache on changes. So output must be stable.
     */
    @get:OutputFile
    abstract val upgradedProperties: RegularFileProperty

    @TaskAction
    fun mergeUpgradedProperties() {
        val mergedUpgradedProperties = mergeProperties()
        if (mergedUpgradedProperties.isEmpty) {
            upgradedProperties.asFile.get().toEmptyFile()
        } else {
            upgradedProperties.asFile.get().writer().use { Gson().toJson(mergedUpgradedProperties, it) }
        }
    }

    private
    fun mergeProperties(): JsonArray {
        val merged = JsonArray()
        instrumentationMetadataDirs
            .map { it.resolve(InstrumentationMetadataTransform.UPGRADED_PROPERTIES_FILE) }
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
