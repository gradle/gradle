import gradle.kotlin.dsl.accessors._23cdd86de02729e5f5eded3732f08da5.kotlin
import gradle.kotlin.dsl.accessors._23cdd86de02729e5f5eded3732f08da5.libs
import gradle.kotlin.dsl.accessors._6ace721833a4087eb4375b4fb92577a6.main
import gradle.kotlin.dsl.accessors._6ace721833a4087eb4375b4fb92577a6.sourceSets
import gradlebuild.basics.ClassFileContentsAttribute
import gradlebuild.configureAsRuntimeJarClasspath
import gradlebuild.packaging.tasks.ExtractJavaAbi
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

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

// Common configuration for everything that belongs to the Gradle distribution
plugins {
    id("gradlebuild.task-properties-validation")
}

val apiStubElements = configurations.consumable("apiStubElements") {
    isVisible = false
    extendsFrom(configurations.named("implementation").get())
    extendsFrom(configurations.named("compileOnly").get())
//    configureAsApiElements(objects)
    attributes {
        attribute(ClassFileContentsAttribute.attribute, ClassFileContentsAttribute.STUBS)
    }
}

// FIXME Publishing API stubs for mixed Java/Kotlin subprojects don't work currently;
//       we only publish the Kotlin stubs for some reason
pluginManager.withPlugin("gradlebuild.jvm-library") {
    val extractorClasspathConfig by configurations.creating

    dependencies {
        extractorClasspathConfig("org.gradle:java-api-extractor")
    }

    val extractJavaAbi by tasks.registering(ExtractJavaAbi::class) {
        classesDirectories = sourceSets.main.get().output.classesDirs
        outputDirectory = layout.buildDirectory.dir("generated/java-abi")
        extractorClasspath = extractorClasspathConfig
    }

    configurations {
        named("apiStubElements") {
            outgoing.artifact(extractJavaAbi)
        }
    }
}

pluginManager.withPlugin("gradlebuild.kotlin-library") {
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
                        addPluginArgument(
                            "org.jetbrains.kotlin.jvm.abi", FilesSubpluginOption(
                                "outputDir", listOf(abiClassesDirectory.get().asFile)
                            )
                        )
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
}
