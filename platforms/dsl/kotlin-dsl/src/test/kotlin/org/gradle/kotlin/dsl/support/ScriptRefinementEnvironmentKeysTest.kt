/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test

import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.getRefinementEnvironment


class ScriptRefinementEnvironmentKeysTest {

    @Test
    fun `refinement environment key matches the Kotlin one`() {
        assertEquals(ScriptingHostConfiguration.getRefinementEnvironment, refinementEnvironmentKey)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy environment key matches the Kotlin one`() {
        assertEquals(ScriptingHostConfiguration.getEnvironment, legacyEnvironmentKey)
    }
}
