/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.File
import javax.inject.Inject


@CacheableTask
open class ShadedJar @Inject constructor(
    @get:Classpath val sourceFiles: FileCollection,
    @get:OutputDirectory val classesDir: File,
    @get:OutputFile val jarFile: File,
    @get:OutputFile val analysisFile: File,
    @get:Input val shadowPackage: String,
    @get:Input val keepPackages: Set<String>,
    @get:Input val unshadedPackages: Set<String>,
    @get:Input val ignorePackages: Set<String>
) : DefaultTask() {

    @TaskAction
    fun run() {
        ShadedJarCreator(
            sourceFiles, jarFile, analysisFile, classesDir,
            shadowPackage, keepPackages, unshadedPackages, ignorePackages).createJar()
    }
}
