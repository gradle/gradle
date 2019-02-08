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

package org.gradle.kotlin.dsl.support

import org.gradle.api.file.RelativePath
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs

import org.gradle.api.internal.file.pattern.PatternMatcherFactory

import org.gradle.kotlin.dsl.codegen.ParameterNamesSupplier

import java.io.File
import java.util.Properties
import java.util.jar.JarFile


const val gradleApiMetadataModuleName = "gradle-api-metadata"


data class GradleApiMetadata(
    val includes: List<String>,
    val excludes: List<String>,
    val parameterNamesSupplier: ParameterNamesSupplier
) {
    val spec = apiSpecFor(includes, excludes)
}


fun gradleApiMetadataFrom(gradleApiMetadataJar: File): GradleApiMetadata =
    JarFile(gradleApiMetadataJar).use { jar ->
        val apiDeclaration = jar.loadProperties(gradleApiDeclarationPropertiesName)
        val parameterNames = jar.loadProperties(gradleApiParameterNamesPropertiesName)
        GradleApiMetadata(
            apiDeclaration.getProperty("includes").split(":"),
            apiDeclaration.getProperty("excludes").split(":"),
            parameterNamesSupplierFrom(parameterNames))
    }


private
fun JarFile.loadProperties(name: String) =
    getInputStream(getJarEntry(name)).use { input ->
        Properties().also { it.load(input) }
    }


private
const val gradleApiDeclarationPropertiesName = "gradle-api-declaration.properties"


private
const val gradleApiParameterNamesPropertiesName = "gradle-api-parameter-names.properties"


private
fun parameterNamesSupplierFrom(parameterNames: Properties): ParameterNamesSupplier =
    { key: String -> parameterNames.getProperty(key, null)?.split(",") }


private
fun apiSpecFor(includes: List<String>, excludes: List<String>): Spec<RelativePath> =
    when {
        includes.isEmpty() && excludes.isEmpty() -> Specs.satisfyAll()
        includes.isEmpty() -> Specs.negate(patternSpecFor(excludes))
        excludes.isEmpty() -> patternSpecFor(includes)
        else -> Specs.intersect(patternSpecFor(includes), Specs.negate(patternSpecFor(excludes)))
    }


private
fun patternSpecFor(patterns: List<String>) =
    Specs.union(patterns.map {
        PatternMatcherFactory.getPatternMatcher(true, true, it)
    })
