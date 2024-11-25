import gradlebuild.configureAsRuntimeJarClasspath
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

/*
 * Copyright 2020 the original author or authors.
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

plugins {
    id("gradlebuild.kotlin-library")
    id("gradlebuild.distribution-module")
    id("gradlebuild.distribution.api")
}

val apiGenDependencies = configurations.dependencyScope("apiGen")
val apiGenClasspath = configurations.resolvable("apiGenClasspath") {
    extendsFrom(apiGenDependencies.get())
    configureAsRuntimeJarClasspath(objects)
}

dependencies {
    apiGenDependencies(libs.kotlinJvmAbiGenEmbeddable)
}

val abiClassesDirectory = layout.buildDirectory.dir("generated/kotlin-abi")
kotlin {
    target.compilations.named("main") {
        compileTaskProvider.configure {
            this as BaseKotlinCompile // TODO: Is there a way we can avoid a cast here?
            pluginClasspath.from(apiGenClasspath)
            outputs.dir(abiClassesDirectory)
                .withPropertyName("abiClassesDirectory")
            pluginOptions.add(provider {
                CompilerPluginConfig().apply {
                    addPluginArgument("org.jetbrains.kotlin.jvm.abi", FilesSubpluginOption(
                        "outputDir", listOf(abiClassesDirectory.get().asFile)
                    ))
                }
            })
        }
    }
}

configurations {
    // TODO: Why are we not generating extensions for this configuration?
    named("apiStubElements") {
        outgoing.artifact(abiClassesDirectory) {
            builtBy(kotlin.target.compilations.named("main").flatMap { it.compileTaskProvider })
        }
    }
}
