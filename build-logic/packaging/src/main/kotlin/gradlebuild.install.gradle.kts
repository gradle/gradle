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

import gradlebuild.basics.gradleInstallPath
import gradlebuild.basics.repoRoot
import gradlebuild.packaging.GradleDistributionSpecs.binDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.allDistributionSpec

val installDirectory = repoRoot().dir(gradleInstallPath).map { validateInstallDir(it) }

tasks.register<Sync>("install") {
    description = "Installs the minimal distribution"
    group = "build"
    with(binDistributionSpec())
    into(installDirectory)
}

tasks.register<Sync>("installAll") {
    description = "Installs the full distribution"
    group = "build"
    with(allDistributionSpec())
    into(installDirectory)
}

fun validateInstallDir(installDir: Directory) = installDir.also { dir ->
    val dirFile = dir.asFile
    if (dirFile.isFile) {
        throw RuntimeException("Install directory $dir is not valid: it is actually a file")
    }
    if (dirFile.list()?.isEmpty() != false) {
        return@also
    }
    val binDirFiles = dirFile.resolve("bin").list()
    if (binDirFiles != null && binDirFiles.isNotEmpty() && binDirFiles.all { it.matches(Regex("^gradle.*")) }) {
        val libDir = dirFile.resolve("lib")
        if (libDir.list()?.any { it.matches(Regex("^gradle.*\\.jar")) } == true) {
            return@also
        }
    }

    throw RuntimeException("Install directory $dir does not look like a Gradle installation. Cannot delete it to install.")
}
