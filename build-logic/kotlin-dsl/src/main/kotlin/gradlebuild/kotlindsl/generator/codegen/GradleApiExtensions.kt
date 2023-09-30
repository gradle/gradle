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

import org.gradle.api.file.RelativePath
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.ApiSpec
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.generateKotlinDslApiExtensionsSourceTo

import java.io.File


internal
fun writeGradleApiKotlinDslExtensionsTo(
    asmLevel: Int,
    platformClassLoader: ClassLoader,
    incubatingAnnotationTypeDescriptor: String,
    outputDirectory: File,
    gradleJars: Collection<File>,
    gradleApiMetadataJar: File,
): List<File> {

    val gradleApiJars = gradleApiJarsFrom(gradleJars)

    val gradleApiMetadata = gradleApiMetadataFrom(gradleApiMetadataJar, gradleApiJars)

    return generateKotlinDslApiExtensionsSourceTo(
        asmLevel,
        platformClassLoader,
        incubatingAnnotationTypeDescriptor,
        outputDirectory,
        "org.gradle.kotlin.dsl",
        "GradleApiKotlinDslExtensions",
        gradleApiJars,
        gradleJars - gradleApiJars.toSet(),
        gradleApiMetadata.apiSpec,
        gradleApiMetadata.parameterNamesSupplier
    )
}


private
fun gradleApiJarsFrom(gradleJars: Collection<File>) =
    gradleJars.filter { it.name.startsWith("gradle-") && !it.name.contains("gradle-kotlin-") }


private
val GradleApiMetadata.apiSpec: ApiSpec
    get() = ApiSpec { sourceName ->
        val relativeSourcePath = relativeSourcePathOf(sourceName)
        spec.test(relativeSourcePath.segments, relativeSourcePath.isFile)
    }


private
fun relativeSourcePathOf(sourceName: String) =
    RelativePath.parse(true, sourceName.replace(".", File.separator))
