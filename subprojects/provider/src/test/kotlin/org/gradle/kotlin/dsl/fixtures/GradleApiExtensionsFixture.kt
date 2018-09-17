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

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.codegen.generateApiExtensionsJar
import org.gradle.kotlin.dsl.isGradleKotlinDslJar
import org.gradle.kotlin.dsl.support.gradleApiMetadataModuleName

import java.io.File


val customInstallationGradleApiExtensionsClasspath: ClassPath
    get() = DefaultClassPath.of(customInstallationGradleApiExtensionsJar)


val customInstallationGradleApiExtensionsJar: File by lazy {
    val fixturesDir = File("build/tmp/fixtures").also { it.mkdirs() }
    File.createTempFile("gradle-api-extensions", "fixture", fixturesDir).also { jar ->
        generateCustomInstallationGradleApiExtensionsJarTo(jar)
        jar.deleteOnExit()
    }
}


internal
fun generateCustomInstallationGradleApiExtensionsJarTo(jar: File) {

    val customInstall = customInstallation()

    val gradleJars = customInstall
        .let { listOf(it.resolve("lib"), it.resolve("lib/plugins")) }
        .flatMap { dir ->
            dir.listFiles { jar ->
                jar.name.startsWith("gradle-") && !isGradleKotlinDslJar(jar)
            }.toList()
        }

    val gradleApiMetadataJar = customInstall
        .resolve("lib")
        .listFiles { file -> file.name.startsWith(gradleApiMetadataModuleName) }
        .single()

    generateApiExtensionsJar(
        jar,
        gradleJars,
        gradleApiMetadataJar
    ) {}
}
