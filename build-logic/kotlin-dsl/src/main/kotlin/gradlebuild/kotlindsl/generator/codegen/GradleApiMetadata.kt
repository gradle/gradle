/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.kotlindsl.generator.codegen

import org.gradle.api.internal.file.pattern.PatternMatcher
import org.gradle.api.internal.file.pattern.PatternMatcherFactory
import java.io.File
import java.util.Properties
import java.util.jar.JarFile


internal
data class GradleApiMetadata(
    val includes: List<String>,
    val excludes: List<String>
) {
    val spec = apiSpecFor(includes, excludes)
}


internal
fun gradleApiMetadataFrom(gradleApiMetadataJar: File): GradleApiMetadata =
    apiDeclarationFrom(gradleApiMetadataJar).let { (includes, excludes) ->
        GradleApiMetadata(includes, excludes)
    }


private
fun apiDeclarationFrom(gradleApiMetadataJar: File): Pair<List<String>, List<String>> =
    JarFile(gradleApiMetadataJar).use { jar ->
        val apiDeclaration = jar.loadProperties(GRADLE_API_DECLARATION_PROPERTIES_NAME)
        apiDeclaration.getProperty("includes").split(":") to apiDeclaration.getProperty("excludes").split(":")
    }


private
fun JarFile.loadPropertiesOrNull(name: String): Properties? =
    getJarEntry(name)?.let { entry ->
        getInputStream(entry)?.use { input ->
            Properties().also { it.load(input) }
        }
    }


private
fun JarFile.loadProperties(name: String): Properties =
    loadPropertiesOrNull(name)!!


private
const val GRADLE_API_DECLARATION_PROPERTIES_NAME = "gradle-api-declaration.properties"


private
fun apiSpecFor(includes: List<String>, excludes: List<String>): PatternMatcher =
    when {
        includes.isEmpty() && excludes.isEmpty() -> PatternMatcher.MATCH_ALL
        includes.isEmpty() -> patternSpecFor(excludes).negate()
        excludes.isEmpty() -> patternSpecFor(includes)
        else -> patternSpecFor(includes).and(patternSpecFor(excludes).negate())
    }


private
fun patternSpecFor(patterns: List<String>) =
    PatternMatcherFactory.getPatternsMatcher(true, true, patterns)
