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
package gradlebuild

import org.gradle.gradlebuild.packaging.GradleDistributionSpecs.binDistributionSpec
import org.gradle.gradlebuild.packaging.GradleDistributionSpecs.allDistributionSpec

val installPathProperty = "gradle_installPath"
val installDirectory = rootProject.layout.projectDirectory.dir(providers.gradleProperty(installPathProperty).orElse(provider<String> {
    throw RuntimeException("You can't install without setting the $installPathProperty property.")
})).map { validateInstallDir(it) }

val install by tasks.registering(Sync::class) {
    description = "Installs the minimal distribution"
    group = "build"
    with(binDistributionSpec())
    into(installDirectory)
}

val installAll by tasks.registering(Sync::class) {
    description = "Installs the full distribution"
    group = "build"
    with(allDistributionSpec())
    into(installDirectory)
}

fun validateInstallDir(installDir: Directory) = installDir.also {
    if (it.asFile.isFile) {
        throw RuntimeException("Install directory $it does not look like a Gradle installation. Cannot delete it to install.")
    }
    if (it.asFile.isDirectory) {
        val libDir = File(it.asFile, "lib")
        if (libDir.list()?.none { it.matches(Regex("gradle.*\\.jar")) } == true) {
            throw RuntimeException("Install directory $it does not look like a Gradle installation. Cannot delete it to install.")
        }
    }
}
