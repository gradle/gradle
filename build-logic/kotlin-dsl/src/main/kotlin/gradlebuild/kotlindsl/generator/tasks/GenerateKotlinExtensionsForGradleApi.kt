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

package gradlebuild.kotlindsl.generator.tasks

import gradlebuild.kotlindsl.generator.codegen.FunctionSinceRepository
import gradlebuild.kotlindsl.generator.codegen.GradleApiMetadata
import gradlebuild.kotlindsl.generator.codegen.KotlinExtensionsForGradleApiFacade
import gradlebuild.kotlindsl.generator.codegen.gradleApiMetadataFrom
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.KOTLIN_DSL_PACKAGE_PATH
import org.gradle.model.internal.asm.AsmConstants.ASM_LEVEL
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.objectweb.asm.Type
import java.io.File


@CacheableTask
abstract class GenerateKotlinExtensionsForGradleApi : DefaultTask() {

    @get:Classpath
    abstract val sharedRuntimeClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @TaskAction
    fun action() {
        KotlinExtensionsForGradleApiFacade(
            DefaultClassPath.of(sharedRuntimeClasspath)
        ).use { facade ->
            destinationDirectory.get().asFile.let { outputDir ->
                outputDir.deleteRecursively()
                outputDir.mkdirs()
                GradleJars(classpath.files).run {
                    facade.writeBuiltinPluginIdExtensionsTo(
                        builtInPluginIdExtFileIn(outputDir),
                        gradleApiJars,
                        PluginDependenciesSpec::class.qualifiedName!!,
                        PluginDependencySpec::class.qualifiedName!!,
                    )
                    FunctionSinceRepository(classpath.files, sources.files).use { sinceRepo ->
                        facade.generateKotlinDslApiExtensionsSourceTo(
                            ASM_LEVEL,
                            ClassLoaderUtils.getPlatformClassLoader(),
                            Type.getDescriptor(Incubating::class.java),
                            outputDir,
                            "org.gradle.kotlin.dsl",
                            "GradleApiKotlinDslExtensions",
                            ::hashTypeSourceName,
                            gradleApiJars,
                            classpathDependencies.toList(),
                            gradleApiMetadata.apiSpec,
                            sinceRepo::since
                        )
                    }
                }
            }
        }
    }
}


private
class GradleJars(distroJars: Set<File>) {
    val gradleApiJars = distroJars.filter { it.name.startsWith("gradle-") && !it.name.contains("gradle-kotlin-") }
    val apiMetadataJar = gradleApiJars.single { it.name.startsWith("gradle-api-metadata") }
    val classpathDependencies = distroJars - gradleApiJars.toSet()
    val gradleApiMetadata = gradleApiMetadataFrom(apiMetadataJar)
}


private
fun builtInPluginIdExtFileIn(outputDir: File): File =
    outputDir.resolve("$KOTLIN_DSL_PACKAGE_PATH/BuiltinPluginIdExtensions.kt").apply {
        parentFile.mkdirs()
    }


private
fun hashTypeSourceName(typeSourceName: String): String =
    Hashing.hashString(typeSourceName).toCompactString()


private
val GradleApiMetadata.apiSpec: (String) -> Boolean
    get() = { sourceName ->
        val relativeSourcePath = relativeSourcePathOf(sourceName)
        spec.test(relativeSourcePath.segments, relativeSourcePath.isFile)
    }


private
fun relativeSourcePathOf(sourceName: String) =
    RelativePath.parse(true, sourceName.replace(".", File.separator))
