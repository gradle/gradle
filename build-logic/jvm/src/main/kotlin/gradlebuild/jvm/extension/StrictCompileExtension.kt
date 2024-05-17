/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*


/**
 * Strict compilation options honored by [gradlebuild.Strict_compile_gradle].
 */
abstract class StrictCompileExtension(val tasks: TaskContainer) {

    fun ignoreDeprecations() {
        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-Xlint:-deprecation")
        }
    }

    fun ignoreRawTypes() {
        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-Xlint:-rawtypes")
        }
    }

    fun ignoreAnnotationProcessing() {
        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-Xlint:-processing")
        }
    }

    fun ignoreParameterizedVarargType() {
        tasks.withType<JavaCompile>().configureEach {
            // There is no way to ignore this warning, so we need to turn off "-Werror" completely
            options.compilerArgs = options.compilerArgs - "-Werror"
        }
    }
}
