/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.jvm

import org.gradle.api.Project
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Encapsulates the configuration for the compilation of a source set, including:
 * - The target JVM version
 * - Workarounds which may affect the way the compilation is performed
 *
 * The compilation workarounds include:
 * - Using JDK internal classes
 * - Using Java standard library APIs that were introduced after the JVM version they are targeting
 *
 * All of these workarounds should be generally avoided, but, with this data we can configure the
 * compile tasks to permit some of these requirements.
 */
abstract class JvmCompilation {

    abstract val name: String

    /**
     * Set this flag to true if the compilation compiles against JDK internal classes.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesJdkInternals: Property<Boolean>

    /**
     * Set this flag to true if the compilation compiles against Java standard library APIs
     * that were introduced after [targetJvmVersion].
     *
     * This workaround should be used sparingly.
     */
    abstract val usesFutureStdlib: Property<Boolean>

    /**
     * The JVM version that all JVM code in this compilation will target.
     */
    abstract val targetJvmVersion: Property<Int>

    fun Project.from(sourceSet: SourceSet) {
        associate(tasks.named<JavaCompile>(sourceSet.getCompileTaskName("java")))
        plugins.withType<GroovyBasePlugin>() {
            associate(tasks.named<GroovyCompile>(sourceSet.getCompileTaskName("groovy")))
        }
        plugins.withId("org.jetbrains.kotlin.jvm") {
            associate(tasks.named<KotlinCompile>(sourceSet.getCompileTaskName("kotlin")))
        }
    }

    @JvmName("associateJava")
    fun Project.associate(javaCompile: TaskProvider<JavaCompile>) {
        javaCompile.configure {
            // Set the release flag if requested.
            // Otherwise, we set the source and target compatibility in the afterEvaluate below.
            options.release = useRelease().zip(targetJvmVersion) { doUseRelease, target ->
                if (doUseRelease) {
                    target
                } else {
                    null
                }
            }
        }

        // Need to use afterEvaluate since source/target compatibility are not lazy
        afterEvaluate {
            tasks.withType<JavaCompile>().configureEach {
                if (!useRelease().get()) {
                    val version = targetJvmVersion.get().toString()
                    sourceCompatibility = version
                    targetCompatibility = version
                }
            }
        }
    }

    @JvmName("associateGroovy")
    fun Project.associate(groovyCompile: TaskProvider<GroovyCompile>) {
        if(!(OperatingSystem.current().isWindows && System.getProperty("os.arch") == "aarch64")) {
            groovyCompile.configure {
                val javaToolchains = project.the<JavaToolchainService>()
                // Groovy does not support the release flag. We must compile with the same
                // JDK we are targeting in order to see the correct standard lib classes
                // during compilation
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = targetJvmVersion.map { JavaLanguageVersion.of(it) }
                }
            }
        }

        // Need to use afterEvaluate since source/target Compatibility are not lazy
        afterEvaluate {
            groovyCompile.configure {
                val version = targetJvmVersion.get().toString()
                sourceCompatibility = version
                targetCompatibility = version
            }
        }
    }

    @JvmName("associateKotlin")
    fun associate(kotlinCompile: TaskProvider<KotlinCompile>) {
        kotlinCompile.configure {
            jvmTargetValidationMode.set(JvmTargetValidationMode.ERROR)
            compilerOptions {
                jvmTarget = targetJvmVersion.map {
                    JvmTarget.fromTarget(if (it < 9) "1.${it}" else it.toString())
                }

                // TODO KT-49746: Use the DSL to set the release version
                freeCompilerArgs.addAll(useRelease().zip(jvmTarget) { doUseRelease, targetVersion ->
                    if (doUseRelease) {
                        listOf("-Xjdk-release=${targetVersion.target}")
                    } else {
                        listOf()
                    }
                })
            }
        }
    }

    private fun useRelease(): Provider<Boolean> {
        return usesJdkInternals.zip(usesFutureStdlib) { internals, futureApis -> !internals && !futureApis }
    }

}
