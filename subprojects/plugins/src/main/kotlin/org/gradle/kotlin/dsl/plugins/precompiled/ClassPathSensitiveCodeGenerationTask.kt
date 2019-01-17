/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.kotlin.dsl.support.serviceOf


abstract class ClassPathSensitiveCodeGenerationTask : DefaultTask() {

    @get:OutputDirectory
    var sourceCodeOutputDir = project.objects.directoryProperty()

    @get:Classpath
    lateinit var classPathFiles: FileCollection

    protected
    val classPath by lazy {
        DefaultClassPath.of(classPathFiles.files)
    }

    protected
    val classPathHash
        get() = hashOf(classPath)

    private
    fun hashOf(classPath: ClassPath): HashCode =
        project.serviceOf<ClasspathHasher>().hash(classPath)
}


internal
fun kotlinPackageNameFor(packageName: String) =
    packageName.split('.').joinToString(".") { "`$it`" }
