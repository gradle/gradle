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


@CacheableTask
open class ShadedJar : DefaultTask() {

    /**
     * The source files to generate the jar from.
     */
    @Classpath
    lateinit var sourceFiles: FileCollection

    /**
     * The directory to write temporary class files to.
     */
    @OutputDirectory
    lateinit var classesDir: File

    /**
     * The output Jar file.
     */
    @OutputFile
    lateinit var jarFile: File

    /**
     * File to write the text analysis report to.
     */
    @OutputFile
    lateinit var analysisFile: File

    /**
     * The package name to prefix all shaded class names with.
     */
    @Input
    lateinit var shadowPackage: String

    /**
     * Retain only those classes in the keep package hierarchies, plus any classes that are reachable from these classes.
     */
    @Input
    var keepPackages: Set<String> = emptySet()

    /**
     * Do not rename classes in the unshaded package hierarchies. Always includes 'java'.
     */
    @Input
    var unshadedPackages: Set<String> = emptySet()

    /**
     * Do not retain classes in the ignore packages hierarchies, unless reachable from some other retained class.
     */
    @Input
    var ignorePackages: Set<String> = emptySet()

    @TaskAction
    fun run() {
        ShadedJarCreator(
            sourceFiles, jarFile, analysisFile, classesDir,
            shadowPackage, keepPackages, unshadedPackages, ignorePackages).createJar()
    }
}
