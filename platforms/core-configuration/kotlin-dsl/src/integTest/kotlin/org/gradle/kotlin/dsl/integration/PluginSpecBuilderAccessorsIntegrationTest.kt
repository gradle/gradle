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

package org.gradle.kotlin.dsl.integration

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class PluginSpecBuilderAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `can use accessors for plugins in the buildSrc classpath`() {
        assumeNonEmbeddedGradleExecuter()

        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my/plugin-a.gradle.kts",
            """
            package my
            println("*my.plugin-a*")
            """
        )

        withDefaultSettings()
        withBuildScript(
            """
            plugins {
                my.`plugin-a`
            }
            """
        )

        assertThat(
            build("help", "-q").output,
            containsString("*my.plugin-a*")
        )
    }
}
