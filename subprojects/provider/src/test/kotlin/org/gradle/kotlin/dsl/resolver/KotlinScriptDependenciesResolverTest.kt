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

package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.customInstallation

import org.gradle.kotlin.dsl.tooling.models.EditorMessages

import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptContents.Position
import kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.File

import kotlin.reflect.KClass


class KotlinScriptDependenciesResolverTest : AbstractIntegrationTest() {

    @Test
    fun `succeeds with no script`() {

        withDefaultSettings()
        assertSucceeds()
    }

    @Test
    fun `succeeds on init script`() {

        withDefaultSettings()
        assertSucceeds(withFile("my.init.gradle.kts", """
            require(this is Gradle)
        """))
    }

    @Test
    fun `succeeds on settings script`() {

        assertSucceeds(withSettings("""
            require(this is Settings)
        """))

        recorder.clear()

        assertSucceeds(withFile("my.settings.gradle.kts", """
            require(this is Settings)
        """))
    }

    @Test
    fun `succeeds on project script`() {

        withDefaultSettings()
        assertSucceeds(withFile("build.gradle.kts", """
            require(this is Project)
        """))

        recorder.clear()

        assertSucceeds(withFile("plugin.gradle.kts", """
            require(this is Project)
        """))
    }

    @Test
    fun `succeeds on precompiled init script`() {

        withPrecompiledScriptBuildSrc()

        withDefaultSettings()

        assertSucceeds(withFile("buildSrc/src/main/kotlin/my-plugin.init.gradle.kts", """
            require(this is Gradle)
        """))
    }

    @Test
    fun `succeeds on precompiled settings script`() {

        withPrecompiledScriptBuildSrc()

        withSettings("""
            apply(plugin = "my-plugin")
        """)

        assertSucceeds(withFile("buildSrc/src/main/kotlin/my-plugin.settings.gradle.kts", """
            require(this is Settings)
        """))
    }


    @Test
    fun `succeeds on precompiled project script`() {

        withPrecompiledScriptBuildSrc()

        withDefaultSettings()
        withBuildScript("""
            plugins {
                id("my-plugin")
            }
        """)

        assertSucceeds(withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            require(this is Project)
        """))
    }

    @Test
    fun `report file warning on script compilation failure in currently edited script`() {

        withDefaultSettings()
        val editedScript = withBuildScript("""
            doNotExists()
        """)

        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolvedDependenciesWithErrors::class)
            assertSingleFileWarningReport(EditorMessages.exceptions)
        }
    }

    @Test
    fun `report file warning on script compilation failure in another script`() {

        withSettings("""
            include("a", "b")
        """)
        withBuildScript("")
        withBuildScriptIn("a", """
            doNotExists()
        """)
        val editedScript = withBuildScriptIn("b", "")

        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolvedDependenciesWithErrors::class)
            assertSingleFileWarningReport(EditorMessages.exceptions)
        }
    }

    @Test
    fun `report file warning on runtime failure in currently edited script`() {

        withDefaultSettings()
        val editedScript = withBuildScript("""
            configurations.getByName("doNotExists")
        """)

        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolvedDependenciesWithErrors::class)
            assertSingleFileWarningReport(EditorMessages.exceptions)
        }
    }


    @Test
    fun `report file warning on runtime failure in another script`() {

        withDefaultSettings()
        val editedScript = withBuildScript("""
            configurations.getByName("doNotExists")
        """)

        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolvedDependenciesWithErrors::class)
            assertSingleFileWarningReport(EditorMessages.exceptions)
        }
    }

    private
    val recorder = ResolverTestRecorder()

    private
    val resolver = KotlinBuildScriptDependenciesResolver(recorder)

    private
    fun environment(vararg entries: Pair<String, Any?>) =
        mapOf(
            "projectRoot" to projectRoot,
            "gradleHome" to customInstallation()
        ) + entries.toMap()

    private
    fun resolvedScriptDependencies(scriptFile: File? = null, vararg env: Pair<String, Any?>) =
        resolver.resolve(
            scriptContentFor(scriptFile),
            environment(*env),
            recorder,
            null
        ).get().also { assertThat("resolved script dependencies", it, notNullValue()) }!!

    private
    fun withPrecompiledScriptBuildSrc() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", """
            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
                `kotlin-dsl-precompiled-script-plugins`
            }
        """)
    }

    private
    fun assertSucceeds(editedScript: File? = null) {

        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertSuccessScenario()
        }
    }

    private
    fun KotlinScriptExternalDependencies.assertContainsBasicDependencies() {
        assertTrue(classpath.toList().isNotEmpty())
        assertTrue(imports.toList().isNotEmpty())
        assertTrue(sources.toList().isNotEmpty())
    }
}


private
fun scriptContentFor(scriptFile: File?) =
    mock<ScriptContents> {
        on { file } doReturn scriptFile
    }


private
class ResolverTestRecorder : ResolverEventLogger, (ReportSeverity, String, Position?) -> Unit {

    data class LogEvent(
        val event: ResolverEvent,
        val prettyPrinted: String
    )

    data class IdeReport(
        val severity: ReportSeverity,
        val message: String,
        val position: Position?
    )

    val events = mutableListOf<LogEvent>()
    val reports = mutableListOf<IdeReport>()

    override fun log(event: ResolverEvent) {
        DefaultResolverEventLogger.prettyPrint(event).let { prettyPrinted ->
            events.add(LogEvent(event, prettyPrinted))
        }
    }

    override fun invoke(severity: ReportSeverity, message: String, position: Position?) {
        reports.add(IdeReport(severity, message, position))
    }

    fun clear() {
        events.clear()
        reports.clear()
    }

    fun assertSuccessScenario() {
        assertThat(
            events.map { it.event },
            hasItems(
                instanceOf(ResolutionRequest::class.java),
                instanceOf(SubmittedModelRequest::class.java),
                instanceOf(ReceivedModelResponse::class.java),
                instanceOf(ResolvedDependencies::class.java)
            )
        )
        assertThat(events.size, equalTo(4))
        assertTrue(reports.isEmpty())
    }

    fun <T : ResolverEvent> assertLastEventIsInstanceOf(eventType: KClass<T>) {
        assertThat(
            events.last().event,
            instanceOf(eventType.java)
        )
    }

    fun assertSingleFileWarningReport(message: String) {
        assertThat(reports.size, equalTo(1))
        reports.single().let { report ->
            assertThat(report.severity, equalTo(ReportSeverity.WARNING))
            assertThat(report.position, nullValue())
            assertThat(report.message, equalTo(message))
        }
    }
}
