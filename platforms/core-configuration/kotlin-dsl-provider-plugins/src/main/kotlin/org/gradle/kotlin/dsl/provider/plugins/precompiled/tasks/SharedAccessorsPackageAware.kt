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
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.accessors.PluginTree
import org.gradle.kotlin.dsl.accessors.pluginTreesFrom
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
    implicitImportsForPrecompiledScriptPlugins(implicitImports, classPathFiles)


internal
fun implicitImportsForPrecompiledScriptPlugins(
    implicitImports: ImplicitImports,
    classPathFiles: FileCollection
): List<String> {
    return implicitImports.list + "${sharedAccessorsPackageFor(pluginTreesFrom(classPathFiles))}.*"
}


internal
fun sharedAccessorsPackageFor(pluginTrees: Map<String, PluginTree>): String {
    fun hash(pluginTrees: Map<String, PluginTree>): HashCode {
        val hasher = Hashing.newHasher()
        pluginTrees.entries.forEach {
            hasher.putString(it.key)
            hasher.putInt(it.value.hashCode())
        }
        return hasher.hash()
    }

    return "$SHARED_ACCESSORS_PACKAGE_PREFIX${hash(pluginTrees)}"
}


private
const val SHARED_ACCESSORS_PACKAGE_PREFIX = "gradle.kotlin.dsl.plugins._"
