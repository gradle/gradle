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

package gradlebuild.shade.tasks

import gradlebuild.basics.classanalysis.JarPackager
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


abstract class ShadedJar : DefaultTask() {
    @get:Input
    abstract val shadowPackage: Property<String>

    @get:Input
    abstract val keepPackages: SetProperty<String>

    @get:Input
    abstract val unshadedPackages: SetProperty<String>

    @get:Input
    abstract val ignoredPackages: SetProperty<String>

    @get:Input
    abstract val keepResources: SetProperty<String>

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    /**
     * The output Jar file.
     */
    @get:OutputFile
    abstract val jarFile: RegularFileProperty

    @TaskAction
    fun shade() {
        val inputFile = inputJar.get().asFile
        val outputFile = jarFile.get().asFile

        JarPackager().minify(inputFile, classpath.toList(), outputFile) {
            renameClassesIntoPackage(shadowPackage.get())
            keepPackages(keepPackages.get())
            keepDirectories()
            doNotRenamePackages(unshadedPackages.get())
            excludePackages(ignoredPackages.get())
            keepResources(keepResources.get())
        }
    }
}
