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

@file:JvmName("ApiExtensionsGenerator")

package org.gradle.kotlin.dsl.codegen

import java.io.File


/**
 * Generate source file with Kotlin extensions enhancing the given api for the Gradle Kotlin DSL.
 *
 * @param outputFile the file where the generated source will be written
 * @param classPath the api classpath elements
 * @param additionalClassPath the api classpath additional elements
 * @param includes the api include patterns
 * @param excludes the api exclude patterns
 * @param parameterNamesIndices the api function parameter names indices
 */
fun generateKotlinDslApiExtensionsSourceTo(
    outputFile: File,
    classPath: List<File>,
    additionalClassPath: List<File>,
    includes: List<String>,
    excludes: List<String>,
    parameterNamesIndices: List<File>
): Unit =

    outputFile.writeText("""
        package org.gradle.kotlin.dsl

        // Generated API extensions for the Gradle Kotlin DSL
        //  classPath = $classPath
        //  additionalClassPath = $additionalClassPath
        //  includes = $includes
        //  excludes = $excludes
        //  parameterNamesIndices = $parameterNamesIndices
        //  outputFile = $outputFile

    """.trimIndent())
