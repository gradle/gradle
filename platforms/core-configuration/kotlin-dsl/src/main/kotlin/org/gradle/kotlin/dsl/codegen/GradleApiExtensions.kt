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

package org.gradle.kotlin.dsl.codegen

import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.generateKotlinDslApiExtensionsSourceTo
import org.gradle.kotlin.dsl.internal.sharedruntime.support.gradleApiMetadataFrom

import java.io.File


internal
fun writeGradleApiKotlinDslExtensionsTo(outputDirectory: File, gradleJars: Collection<File>, gradleApiMetadataJar: File): List<File> {

    val gradleApiJars = gradleApiJarsFrom(gradleJars)

    val gradleApiMetadata = gradleApiMetadataFrom(gradleApiMetadataJar, gradleApiJars)

    return generateKotlinDslApiExtensionsSourceTo(
        outputDirectory,
        "org.gradle.kotlin.dsl",
        "GradleApiKotlinDslExtensions",
        gradleApiJars,
        gradleJars - gradleApiJars,
        gradleApiMetadata.spec,
        gradleApiMetadata.parameterNamesSupplier
    )
}


private
fun gradleApiJarsFrom(gradleJars: Collection<File>) =
    gradleJars.filter { it.name.startsWith("gradle-") && !it.name.contains("gradle-kotlin-") }
