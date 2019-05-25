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

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.junit.After
import org.junit.Before


abstract class AbstractScriptCachingIntegrationTest : AbstractKotlinIntegrationTest() {

    @Before
    fun isolatedDaemons() {
        executer.apply {
            requireDaemon()
            requireIsolatedDaemons()
        }
    }

    @After
    fun stopDaemons() {
        build("--stop")
    }

    protected
    fun buildForCacheInspection(vararg arguments: String): ExecutionResult =
        executerForCacheInspection(*arguments).run()

    protected
    fun buildWithDaemonHeapSize(heapMb: Int, vararg arguments: String): ExecutionResult =
        executerForCacheInspection(*arguments)
            .withBuildJvmOpts("-Xms${heapMb}m", "-Xmx${heapMb}m")
            .run()

    private
    fun executerForCacheInspection(vararg arguments: String): GradleExecuter =
        gradleExecuterFor(arrayOf(
            "-d",
            "-Dorg.gradle.internal.operations.trace=${newFile("operation-trace")}",
            *arguments
        ))
}
