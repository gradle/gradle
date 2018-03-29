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
package org.gradle.gradlebuild.profiling.buildscan

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Task
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.classloader.ClassLoaderVisitor
import java.net.URLClassLoader


class Visitor(private val buildScan: BuildScanExtension, private val hasher: ClassLoaderHierarchyHasher, private val prefix: String) : ClassLoaderVisitor() {
    private
    var counter = 0

    constructor(buildScan: BuildScanExtension, hasher: ClassLoaderHierarchyHasher, task: Task) :
        this(buildScan, hasher, "${task.path}-classloader")


    private
    fun classloaderHash(loader: ClassLoader): String? {
        return hasher.getClassLoaderHash(loader)?.toString()
    }

    override fun visit(classLoader: ClassLoader) {
        val hash = classloaderHash(classLoader)
        if (!hash.isNullOrEmpty()) {
            val classloaderName = classLoader::class.java.simpleName
            buildScan.value("$prefix-${counter++}-$classloaderName-hash", hash)
            if ((this.counter <= 2) && (classLoader is URLClassLoader && (!classloaderName.contains("ExtClassLoader")))) {
                buildScan.value("$prefix-${counter - 1}-classpath", classLoader.urLs.joinToString(":"))
            }
        }
        super.visit(classLoader)
    }
}
