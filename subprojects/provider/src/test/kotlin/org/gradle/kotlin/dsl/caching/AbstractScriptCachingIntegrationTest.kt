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

package org.gradle.kotlin.dsl.caching

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.normalisedPath

import org.gradle.testkit.runner.BuildResult

import java.io.File


abstract class AbstractScriptCachingIntegrationTest : AbstractIntegrationTest() {

    protected
    fun buildWithDaemonHeapSize(heapMb: Int, vararg arguments: String): BuildResult =
        withGradleJvmArguments("-Xms${heapMb}m", "-Xmx${heapMb}m", "-Dfile.encoding=UTF-8").run {
            buildForCacheInspection(*arguments)
        }

    protected
    fun buildForCacheInspection(vararg arguments: String): BuildResult =
        gradleRunnerForCacheInspection(*arguments)
            .build()

    protected
    fun gradleRunnerForCacheInspection(vararg arguments: String) =
        gradleRunnerForArguments("-d", "-Dorg.gradle.internal.operations.trace=${newFile("operation-trace")}", *arguments)

    protected
    fun buildWithAnotherDaemon(vararg arguments: String): BuildResult =
        buildWithDaemonHeapSize(160, *arguments)

    protected
    fun buildWithUniqueGradleHome(vararg arguments: String): BuildResult =
        buildWithGradleHome(uniqueGradleHome(), *arguments)

    protected
    fun buildWithGradleHome(gradleHomePath: String, vararg arguments: String) =
        buildForCacheInspection("-g", gradleHomePath, *arguments)

    protected
    fun <T> withUniqueGradleHome(f: (String) -> T): T =
        f(uniqueGradleHome())

    private
    fun uniqueGradleHome() =
        createTempDir(directory = uniqueGradleHomesDir).normalisedPath

    private
    val uniqueGradleHomesDir: File
        get() = existing("unique-gradle-homes").apply {
            mkdir()
        }
}
