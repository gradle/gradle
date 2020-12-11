/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.lifecycle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction


abstract class GradlePropertiesCheck : DefaultTask() {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val buildPropertiesFile: RegularFileProperty

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val rootPropertiesFile: RegularFileProperty

    @TaskAction
    fun compareProperties() {
        val buildProperties = readProperties(buildPropertiesFile)
        val rootProperties = readProperties(rootPropertiesFile).filter { it.key != "org.gradle.dependency.verification" }
        if (buildProperties != rootProperties) {
            throw GradleException(
                """
                    To make sure builds behave the and can reuse the same daemon when built individually (e.g. to run 'clean' on CI)
                    all 'gradle.properties' files should correspond to the 'gradle.properties' file in the root build.
                    Please fix the following: ${buildPropertiesFile.get().asFile.relativeTo(rootPropertiesFile.get().asFile.parentFile)}

                    Root:  $rootProperties

                    Build: $buildProperties
                """.trimIndent()
            )
        }
    }

    private
    fun readProperties(propertiesFile: RegularFileProperty) = java.util.Properties().apply {
        propertiesFile.get().asFile.inputStream().use { fis -> load(fis) }
    }.mapKeys { it.key.toString() }.mapValues { it.value.toString() }.toSortedMap()
}
