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

package org.gradle.kotlin.dsl.accessors

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor

import org.junit.Test


/**
 * This definition is here so its metadata can be inspected.
 */
@Suppress("unused_parameter")
fun <T : Dependency> DependencyHandler.foo(
    dependency: T,
    action: Action<T>
): T = TODO()


class KotlinMetadataIntegrationTest : TestWithTempFiles() {

    @Test
    fun `extract file metadata`() {
        dumpFileFacadeHeaderOf(
            javaClass.classLoader.loadClass(javaClass.name + "Kt")
        )
    }

    @Test
    fun `extract module metadata`() {

        val outputDir = newFolder("main")
        val moduleName = outputDir.name
        require(
            compileToDirectory(
                outputDir,
                JavaVersion.current(),
                moduleName,
                listOf(
                    newFile(
                        "ConfigurationAccessors.kt",
                        """
                        package org.gradle.kotlin.dsl

                        import org.gradle.api.artifacts.*

                        val ConfigurationContainer.api: Configuration
                            inline get() = TODO()
                        """
                    )
                ),
                loggerFor<KotlinMetadataIntegrationTest>(),
                testRuntimeClassPath.asFiles
            )
        )

        dumpMetadataOfModule(outputDir, moduleName)
    }
}
