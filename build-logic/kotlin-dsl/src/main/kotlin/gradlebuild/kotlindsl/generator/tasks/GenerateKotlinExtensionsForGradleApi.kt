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

package gradlebuild.kotlindsl.generator.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.generateGradleApiExtensionsSources


@CacheableTask
abstract class GenerateKotlinExtensionsForGradleApi : DefaultTask() {

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @TaskAction
    fun action() {

        val gradleJars = classpath.files.filter { it.name.startsWith("gradle-") && !it.name.startsWith("gradle-kotlin-dsl-") }
        val apiMetadataJar = gradleJars.single { it.name.startsWith("gradle-api-metadata") }
        val outputDir = destinationDirectory.get().asFile
        generateGradleApiExtensionsSources(outputDir, gradleJars, apiMetadataJar)
    }
}
