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

package org.gradle.kotlin.dsl.fixtures

import org.gradle.api.Project

import org.gradle.internal.classpath.ClassPath

import java.io.File


abstract class AbstractDslTest : TestWithTempFiles() {

    private
    val dslTestFixture: DslTestFixture by lazy {
        DslTestFixture(root)
    }

    protected
    val kotlinDslEvalBaseCacheDir: File
        get() = dslTestFixture.kotlinDslEvalBaseCacheDir

    protected
    val kotlinDslEvalBaseTempDir: File
        get() = dslTestFixture.kotlinDslEvalBaseTempDir

    /**
     * Evaluates the given Kotlin [script] against this [Project] writing compiled classes
     * to sub-directories of [baseCacheDir] using [scriptCompilationClassPath].
     */
    fun Project.eval(
        script: String,
        baseCacheDir: File = kotlinDslEvalBaseCacheDir,
        baseTempDir: File = kotlinDslEvalBaseTempDir,
        scriptCompilationClassPath: ClassPath = testRuntimeClassPath,
        scriptRuntimeClassPath: ClassPath = ClassPath.EMPTY
    ) =
        dslTestFixture.evalScript(
            script,
            this,
            baseCacheDir,
            baseTempDir,
            scriptCompilationClassPath,
            scriptRuntimeClassPath
        )
}


class DslTestFixture(private val testDirectory: File) {

    val kotlinDslEvalBaseCacheDir: File by lazy {
        testDirectory.resolve("kotlin-dsl-eval-cache").apply {
            mkdirs()
        }
    }

    val kotlinDslEvalBaseTempDir: File by lazy {
        testDirectory.resolve("kotlin-dsl-eval-temp").apply {
            mkdirs()
        }
    }

    /**
     * Evaluates the given Kotlin [script] against this [Project] writing compiled classes
     * to sub-directories of [baseCacheDir] using [scriptCompilationClassPath].
     */
    fun evalScript(
        script: String,
        target: Any,
        baseCacheDir: File = kotlinDslEvalBaseCacheDir,
        baseTempDir: File = kotlinDslEvalBaseTempDir,
        scriptCompilationClassPath: ClassPath = testRuntimeClassPath,
        scriptRuntimeClassPath: ClassPath = ClassPath.EMPTY
    ) =
        eval(
            script,
            target,
            baseCacheDir,
            baseTempDir,
            scriptCompilationClassPath,
            scriptRuntimeClassPath
        )
}
