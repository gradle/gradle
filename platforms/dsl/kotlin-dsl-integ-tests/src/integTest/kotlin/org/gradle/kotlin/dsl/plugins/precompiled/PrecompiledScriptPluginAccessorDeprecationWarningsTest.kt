/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginAccessorDeprecationWarningsTest : AbstractPrecompiledScriptPluginTest() {

    @Test
    fun `generated type-safe accessors suppress deprecation warnings`() {
        // `java-gradle-plugin` adds deprecated task `ValidateTaskProperties`
        givenPrecompiledKotlinScript(
            "java-project.gradle.kts",
            """
            plugins { `java-gradle-plugin` }
            """
        ).apply {
            assertNotOutput("'ValidateTaskProperties' is deprecated.")
        }
    }
}
