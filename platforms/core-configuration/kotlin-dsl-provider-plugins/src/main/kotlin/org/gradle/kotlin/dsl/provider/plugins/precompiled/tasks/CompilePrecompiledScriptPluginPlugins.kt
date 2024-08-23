/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.initialization.GradlePropertiesController
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledPluginsBlock
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.compileKotlinScriptModuleTo
import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.gradle.kotlin.dsl.support.scriptDefinitionFromTemplate
import javax.inject.Inject


/**
 * Compiles the extracted `plugins {}` blocks from precompiled scripts of all targets.
 */
@CacheableTask
abstract class CompilePrecompiledScriptPluginPlugins @Inject constructor(

    private
    val implicitImports: ImplicitImports,

    private
    val gradleProperties: GradlePropertiesController

) : DefaultTask(), SharedAccessorsPackageAware {

    private
    companion object {
        const val kotlinModuleName = "precompiled-script-plugin-plugins"
    }

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sourceFiles: ConfigurableFileCollection = project.files(
        project.objects.sourceDirectorySet(
            kotlinModuleName,
            "Precompiled script plugin plugins"
        )
    )

    fun sourceDir(dir: Provider<Directory>) {
        sourceFiles.from(dir)
    }

    @get:Nested
    internal
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Optional
    @get:Input
    @Deprecated("Configure a Java Toolchain instead")
    internal
    abstract val jvmTarget: Property<JavaVersion>

    @get:Input
    protected
    val compilerOptions: Provider<KotlinCompilerOptions> = project.provider {
        kotlinCompilerOptions(gradleProperties).copy(jvmTarget = resolveJvmTarget())
    }

    @TaskAction
    fun compile() {
        outputDir.withOutputDirectory { outputDir ->
            val scriptFiles = sourceFiles.map { it.path }
            if (scriptFiles.isNotEmpty())
                compileKotlinScriptModuleTo(
                    outputDir,
                    compilerOptions.get(),
                    kotlinModuleName,
                    scriptFiles,
                    scriptDefinitionFromTemplate(
                        PrecompiledPluginsBlock::class,
                        implicitImportsForPrecompiledScriptPlugins(implicitImports)
                    ),
                    classPathFiles.filter { it.exists() },
                    logger,
                ) { it } // TODO: translate paths
        }
    }

    @Suppress("DEPRECATION")
    private
    fun resolveJvmTarget(): JavaVersion =
        if (jvmTarget.isPresent) jvmTarget.get()
        else JavaVersion.toVersion(javaLauncher.get().metadata.languageVersion.asInt())
}
