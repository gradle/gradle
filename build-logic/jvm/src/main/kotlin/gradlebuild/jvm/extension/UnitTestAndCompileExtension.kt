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

package gradlebuild.jvm.extension

import gradle.kotlin.dsl.accessors._7758f57c6dd35933dbd9dc8c103ba8e9.javaToolchains
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*


abstract class UnitTestAndCompileExtension(
    private val project: Project,
    private val tasks: TaskContainer,
) {

    /**
     * Enforces **Java 6** compatibility.
     */
    fun usedInWorkers() {
        enforceCompatibility(6)
    }

    /**
     * Enforces **Java 6** compatibility.
     */
    fun usedForStartup() {
        enforceCompatibility(6)
    }

    /**
     * Enforces **Java 7** compatibility.
     */
    fun usedInToolingApi() {
        enforceCompatibility(7)
    }

    private
    fun enforceCompatibility(majorVersion: Int) {
        tasks.withType<JavaCompile>().configureEach {
            javaCompiler.set(project.javaToolchains.compilerFor {
                languageVersion.set(JavaLanguageVersion.of(majorVersion))
            })
            options.release = null
            options.compilerArgs.remove("-parameters")
            sourceCompatibility = "$majorVersion"
            targetCompatibility = "$majorVersion"
        }
        // Apply ParameterNamesIndex since 6 doesn't support -parameters
        project.apply(plugin = "gradlebuild.api-parameter-names-index")
    }
}
