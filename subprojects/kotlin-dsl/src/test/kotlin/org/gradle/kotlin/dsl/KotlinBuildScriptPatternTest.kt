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

package org.gradle.kotlin.dsl

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.script.templates.ScriptTemplateDefinition


@RunWith(Parameterized::class)
class KotlinBuildScriptPatternTest(val script: Script) {
    enum class ScriptType {
        BUILD,
        INIT,
        SETTINGS
    }

    data class Script(val name: String, val type: ScriptType)

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun scripts(): Iterable<Script> = listOf(
            Script("settings.gradle.kts", ScriptType.SETTINGS),
            Script("my.settings.gradle.kts", ScriptType.SETTINGS),
            Script("init.gradle.kts", ScriptType.INIT),
            Script("my.init.gradle.kts", ScriptType.INIT),
            Script("build.gradle.kts", ScriptType.BUILD),
            Script("no-settings.gradle.kts", ScriptType.BUILD),
            Script("no-init.gradle.kts", ScriptType.BUILD),
            Script("anything.gradle.kts", ScriptType.BUILD),
        )
    }

    @Test
    fun `recognizes build scripts`() {
        checkScriptRecognizedBy(KotlinBuildScript::class, ScriptType.BUILD)
    }

    @Test
    fun `recognizes settings scripts`() {
        checkScriptRecognizedBy(KotlinSettingsScript::class, ScriptType.SETTINGS)
    }

    @Test
    fun `recognizes init scripts`() {
        checkScriptRecognizedBy(KotlinInitScript::class, ScriptType.INIT)
    }

    private
    fun checkScriptRecognizedBy(scriptParserClass: KClass<*>, supportedScriptType: ScriptType) {
        val buildScriptPattern = scriptParserClass.findAnnotation<ScriptTemplateDefinition>()!!.scriptFilePattern
        val shouldMatch = script.type == supportedScriptType
        assertEquals("${script.name} should${if (shouldMatch) "" else " not"} match $buildScriptPattern", shouldMatch, script.name.matches(buildScriptPattern.toRegex()))
    }
}
