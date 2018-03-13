/*
 * Copyright 2017 the original author or authors.
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

// To make it easier to access these functions from Groovy
@file:JvmName("Cleanup")

package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.api.Project
import org.gradle.api.specs.Spec
import org.gradle.kotlin.dsl.invoke
import org.gradle.util.GradleVersion

import java.io.File


private
val dirVersionPattern = "\\d+\\.\\d+(\\.\\d+)?(-\\w+)*(-\\d{14}[+-]\\d{4})?".toRegex()


/**
 * Removes state for versions that we're unlikely to ever need again, such as old snapshot versions.
 */
fun Project.removeOldVersionsFromDir(dir: File, shouldDelete: Spec<GradleVersion>, dirPrefix: String = "", dirSuffix: String = "") {

    if (dir.isDirectory) {

        for (cacheDir in dir.listFiles()) {
            val cacheDirName = cacheDir.name
            if (!cacheDirName.startsWith(dirPrefix) || !cacheDirName.endsWith(dirSuffix)) {
                continue
            }
            val dirVersion = cacheDirName.removePrefix(dirPrefix).removeSuffix(dirSuffix)
            if (!dirVersion.matches(dirVersionPattern)) {
                continue
            }

            val cacheVersion =
                try {
                    GradleVersion.version(dirVersion)
                } catch (e: IllegalArgumentException) {
                    // Ignore
                    continue
                }

            if (shouldDelete(cacheVersion)) {
                println("Removing old cache directory : $cacheDir")
                delete(cacheDir)
            }
        }
    }
}


fun Project.removeCachedScripts(cachesDir: File) {

    if (cachesDir.isDirectory) {

        cachesDir.listFiles()
            .filter { it.isDirectory }
            .flatMap { scriptsCacheDirsUnder(it) }
            .forEach { scriptsCacheDir ->
                println("Removing scripts cache directory : $scriptsCacheDir")
                delete(scriptsCacheDir)
            }
    }
}


private
val scriptCacheDirNames =
    listOf("scripts", "scripts-remapped", "gradle-kotlin-dsl", "gradle-kotlin-dsl-accessors")


private
fun scriptsCacheDirsUnder(cacheDir: File) =
    scriptCacheDirNames
        .map { File(cacheDir, it) }
        .filter { it.isDirectory }


/**
 * Clean up cache files for older versions that aren't multi-process safe.
 */
fun Project.removeDodgyCacheFiles(dir: File) {

    if (dir.isDirectory) {

        for (cacheDir in dir.listFiles()) {
            if (!cacheDir.name.matches(dirVersionPattern)) {
                continue
            }
            for (name in listOf("fileHashes", "outputFileStates", "fileSnapshots")) {
                val stateDir = File(cacheDir, name)
                if (stateDir.isDirectory) {
                    println("Removing old cache directory : $stateDir")
                    delete(stateDir)
                }
            }
        }
    }
}


/**
 * Clean up daemon log files produced in integration tests.
 */
fun Project.removeDaemonLogFiles(dir: File) {
    if (dir.isDirectory) {
        val daemonLogFiles = fileTree(dir) {
            include("**/*.log")
        }
        delete(daemonLogFiles)
    }
}
