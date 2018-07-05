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

import org.gradle.kotlin.dsl.provider.gradleApiMetadataModuleName

import java.io.File
import java.util.Properties
import java.util.jar.JarFile


internal
fun writeGradleApiKotlinDslExtensionsTo(outputDirectory: File, gradleJars: List<File>) =
    (gradleApiJarsFrom(gradleJars) to gradleApiMetadataFrom(gradleJars)).let { (gradleApiJars, gradleApiMetadata) ->
        doGenerateKotlinDslApiExtensionsSourceTo(
            outputDirectory,
            "org.gradle.kotlin.dsl",
            "GradleApiKotlinDslExtensions",
            gradleApiJars,
            gradleJars - gradleApiJars,
            gradleApiMetadata.includes,
            gradleApiMetadata.excludes,
            gradleApiMetadata.parameterNamesSupplier
        )
    }


private
fun gradleApiJarsFrom(gradleJars: List<File>) =
    gradleJars.filter { it.name.startsWith("gradle-") && !it.name.contains("gradle-kotlin-") }


private
fun gradleApiMetadataFrom(gradleJars: List<File>) =
    JarFile(gradleJars.single { it.name.startsWith(gradleApiMetadataModuleName) }).use { jar ->
        jar.loadProperties(gradleApiDeclarationPropertiesName) to jar.loadProperties(gradleApiParameterNamesPropertiesName)
    }.let { (apiDeclaration, parameterNames) ->
        GradleApiMetadata(
            apiDeclaration.getProperty("includes").split(":"),
            apiDeclaration.getProperty("excludes").split(":")
        ) { key: String ->
            parameterNames.getProperty(key, null)?.split(",")
        }
    }


private
data class GradleApiMetadata(
    val includes: List<String>,
    val excludes: List<String>,
    val parameterNamesSupplier: ParameterNamesSupplier
)


private
fun JarFile.loadProperties(name: String) =
    getInputStream(getJarEntry(name)).use { input ->
        Properties().also { it.load(input) }
    }


private
const val gradleApiDeclarationPropertiesName = "gradle-api-declaration.properties"


private
const val gradleApiParameterNamesPropertiesName = "gradle-api-parameter-names.properties"
