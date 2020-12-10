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

import gradlebuild.basics.repoRoot
import gradlebuild.packaging.GradleDistributionSpecs.binDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.allDistributionSpec

val installPathProperty = "gradle_installPath"
val installDirectory = repoRoot().dir(
    providers.gradleProperty(installPathProperty).orElse(
        provider<String> {
            throw RuntimeException("You can't install without setting the $installPathProperty property.")
        }
    )
).map { validateInstallDir(it) }

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
    if (dir.asFile.isFile) {
        throw RuntimeException("Install directory $dir does not look like a Gradle installation. Cannot delete it to install.")
    }
    if (dir.asFile.isDirectory) {
        val libDir = dir.asFile.resolve("lib")
        if (libDir.list()?.none { it.matches(Regex("gradle.*\\.jar")) } == true) {
            throw RuntimeException("Install directory $dir does not look like a Gradle installation. Cannot delete it to install.")
        }
    }
}
