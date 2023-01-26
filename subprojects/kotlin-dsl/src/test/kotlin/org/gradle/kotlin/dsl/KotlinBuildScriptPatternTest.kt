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
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.util.PropertiesCollection
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
    fun `recognizes build scripts from script templates`() {
        checkScriptRecognizedBy(KotlinProjectScriptTemplate::class, ScriptType.BUILD)
    }

    @Test
    fun `recognizes settings scripts from script templates`() {
        checkScriptRecognizedBy(KotlinSettingsScriptTemplate::class, ScriptType.SETTINGS)
    }

    @Test
    fun `recognizes init scripts from script templates`() {
        checkScriptRecognizedBy(KotlinGradleScriptTemplate::class, ScriptType.INIT)
    }

    @Test
    fun `recognizes build scripts from legacy script templates`() {
        @Suppress("DEPRECATION")
        checkScriptRecognizedByLegacy(KotlinBuildScript::class, ScriptType.BUILD)
    }

    @Test
    fun `recognizes settings scripts from legacy script templates`() {
        @Suppress("DEPRECATION")
        checkScriptRecognizedByLegacy(KotlinSettingsScript::class, ScriptType.SETTINGS)
    }

    @Test
    fun `recognizes init scripts from legacy script templates`() {
        @Suppress("DEPRECATION")
        checkScriptRecognizedByLegacy(KotlinInitScript::class, ScriptType.INIT)
    }

    private
    fun checkScriptRecognizedBy(scriptParserClass: KClass<*>, supportedScriptType: ScriptType) {
        assertScriptFilePatternMatches(filePathPatternFrom(scriptParserClass), supportedScriptType)
    }

    private
    fun checkScriptRecognizedByLegacy(scriptParserClass: KClass<*>, supportedScriptType: ScriptType) {
        assertScriptFilePatternMatches(scriptFilePatternFromLegacy(scriptParserClass), supportedScriptType)
    }

    private
    fun filePathPatternFrom(scriptParserClass: KClass<*>): String {
        val kotlinScriptAnnotation = scriptParserClass.findAnnotation<KotlinScript>()!!
        val compilationConfigClass = kotlinScriptAnnotation.compilationConfiguration
        val compilationConfigConstructor = compilationConfigClass.java.constructors.single().apply {
            isAccessible = true
        }
        val compilationConfig = compilationConfigConstructor.newInstance() as ScriptCompilationConfiguration
        return compilationConfig[PropertiesCollection.Key<String>("filePathPattern")]!!
    }

    private
    fun scriptFilePatternFromLegacy(scriptParserClass: KClass<*>): String =
        scriptParserClass.findAnnotation<ScriptTemplateDefinition>()!!.scriptFilePattern

    private
    fun assertScriptFilePatternMatches(scriptFilePattern: String, supportedScriptType: ScriptType) {
        val shouldMatch = script.type == supportedScriptType
        assertEquals(
            "${script.name} should${if (shouldMatch) "" else " not"} match $scriptFilePattern",
            shouldMatch,
            script.name.matches(scriptFilePattern.toRegex())
        )
    }
}
