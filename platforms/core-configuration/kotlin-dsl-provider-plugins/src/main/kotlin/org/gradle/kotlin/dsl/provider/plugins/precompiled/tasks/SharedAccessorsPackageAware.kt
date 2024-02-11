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

package org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks

import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter
import org.gradle.kotlin.dsl.support.ImplicitImports
import javax.inject.Inject


interface ClassPathAware {

    @get:InputFiles
    @get:Classpath
    val classPathFiles: ConfigurableFileCollection
}


interface SharedAccessorsPackageAware : ClassPathAware {

    @get:Inject
    val classPathFingerprinter: ClasspathFingerprinter
}


internal
fun <T> T.implicitImportsForPrecompiledScriptPlugins(
    implicitImports: ImplicitImports
): List<String> where T : Task, T : SharedAccessorsPackageAware =
    implicitImportsForPrecompiledScriptPlugins(implicitImports, classPathFingerprinter, classPathFiles)


internal
val <T> T.sharedAccessorsPackage: String where T : Task, T : SharedAccessorsPackageAware
    get() = classPathFingerprinter.sharedAccessorsPackageFor(classPathFiles)


internal
fun implicitImportsForPrecompiledScriptPlugins(
    implicitImports: ImplicitImports,
    classpathFingerprinter: ClasspathFingerprinter,
    classPathFiles: FileCollection
): List<String> {
    return implicitImports.list + "${classpathFingerprinter.sharedAccessorsPackageFor(classPathFiles)}.*"
}


private
fun ClasspathFingerprinter.sharedAccessorsPackageFor(classPathFiles: FileCollection): String =
    "$sharedAccessorsPackagePrefix${fingerprintHashFor(classPathFiles)}"


private
fun ClasspathFingerprinter.fingerprintHashFor(classPathFiles: FileCollection) =
    fingerprint(classPathFiles).hash


private
const val sharedAccessorsPackagePrefix = "gradle.kotlin.dsl.plugins._"
