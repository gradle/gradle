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

package org.gradle.kotlin.dsl.fixtures

import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.kotlin.dsl.codegen.generateApiExtensionsJar
import org.gradle.kotlin.dsl.support.gradleApiMetadataModuleName
import org.gradle.kotlin.dsl.support.isGradleKotlinDslJar
import java.io.File
import java.util.concurrent.ConcurrentHashMap


fun testInstallationGradleApiExtensionsClasspathFor(testInstallation: File, tmpDir: File): ClassPath =
    DefaultClassPath.of(testInstallationGradleApiExtensionsJarFor(testInstallation, tmpDir))


private
val testInstallationGradleApiExtensionsJars =
    ConcurrentHashMap<File, File>()


private
fun testInstallationGradleApiExtensionsJarFor(testInstallation: File, tmpDir: File) =
    testInstallationGradleApiExtensionsJars.getOrPut(testInstallation) {
        File(tmpDir, "gradle-api-extensions-fixture.jar").also { jar ->
            generateTestInstallationGradleApiExtensionsJarTo(jar, testInstallation, tmpDir)
            jar.deleteOnExit()
        }
    }


private
fun generateTestInstallationGradleApiExtensionsJarTo(jar: File, testInstallation: File, tmpDir: File) {

    val gradleJars = testInstallation
        .let { listOf(it.resolve("lib"), it.resolve("lib/plugins")) }
        .flatMap { dir ->
            dir.listFiles { jar ->
                jar.name.startsWith("gradle-") && !isGradleKotlinDslJar(jar)
            }.toList()
        }

    val gradleApiMetadataJar = testInstallation
        .resolve("lib")
        .listFiles { file -> file.name.startsWith(gradleApiMetadataModuleName) }
        .single()

    generateApiExtensionsJar(
        DefaultTemporaryFileProvider { tmpDir },
        jar,
        gradleJars,
        gradleApiMetadataJar
    ) {}
}
