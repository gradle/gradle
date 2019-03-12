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

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matcher

import org.junit.Assert.assertSame
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue

import org.junit.Test

import java.io.File

import kotlin.reflect.KClass

import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptContents.Position
import kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity


class KotlinScriptDependenciesResolverTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `succeeds with no script`() {

        withDefaultSettings()
        assertSucceeds()
    }

    @Test
    fun `returns given Java home`() {

        val javaHome = System.getProperty("java.home")
        val env = arrayOf("gradleJavaHome" to javaHome)
        assertThat(
            resolvedScriptDependencies(env = *env)?.javaHome,
            equalTo(javaHome)
        )
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

        withKotlinBuildSrc()

        withDefaultSettings()

        assertSucceeds(withFile("buildSrc/src/main/kotlin/my-plugin.init.gradle.kts", """
            require(this is Gradle)
        """))
    }

    @Test
    fun `succeeds on precompiled settings script`() {

        withKotlinBuildSrc()

        withSettings("""
            apply(plugin = "my-plugin")
        """)

        assertSucceeds(withFile("buildSrc/src/main/kotlin/my-plugin.settings.gradle.kts", """
            require(this is Settings)
        """))
    }

    @Test
    fun `succeeds on precompiled project script`() {

        withKotlinBuildSrc()

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
    fun `report file fatality on TAPI failure`() {
        // thus disabling syntax highlighting

        withDefaultSettings()
        val editedScript = withBuildScript("")

        val wrongEnv = arrayOf("gradleHome" to existing("absent"))
        resolvedScriptDependencies(editedScript, env = *wrongEnv).apply {
            assertThat(this, nullValue())
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolutionFailure::class)
            assertSingleFileReport(ReportSeverity.FATAL, EditorMessages.failure)
        }
    }

    @Test
    fun `report file error on TAPI failure when reusing previous dependencies`() {

        withDefaultSettings()
        val editedScript = withBuildScript("")

        val previous = resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        val wrongEnv = arrayOf("gradleHome" to existing("absent"))
        resolvedScriptDependencies(editedScript, previous, *wrongEnv).apply {
            assertSame(previous, this)
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolutionFailure::class)
            assertSingleFileReport(ReportSeverity.ERROR, EditorMessages.failureUsingPrevious)
        }
    }

    @Test
    fun `report file fatality on early build configuration failure`() {
        // thus disabling syntax highlighting

        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/Foo.kt", """
            BOOM
        """)

        withDefaultSettings()
        val editedScript = withBuildScript("")

        resolvedScriptDependencies(editedScript).apply {
            assertThat(this, nullValue())
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolutionFailure::class)
            assertSingleFileReport(ReportSeverity.FATAL, EditorMessages.buildConfigurationFailed)
        }
    }

    @Test
    fun `report file warning on early build configuration failure when reusing previous dependencies`() {

        withKotlinBuildSrc()
        val buildSrcKotlinSource = withFile("buildSrc/src/main/kotlin/Foo.kt", "")

        withDefaultSettings()
        val editedScript = withBuildScript("")

        val previous = resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        buildSrcKotlinSource.writeText("BOOM")

        resolvedScriptDependencies(editedScript, previous).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolutionFailure::class)
            assertSingleFileReport(ReportSeverity.WARNING, EditorMessages.buildConfigurationFailedUsingPrevious)
        }
    }

    @Test
    fun `do not report file warning on script compilation failure in currently edited script`() {
        // because the IDE already provides user feedback for those

        withDefaultSettings()
        val editedScript = withBuildScript("""
            doNotExists()
        """)

        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolvedDependenciesWithErrors::class)
            assertNoEditorReport()
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
            assertSingleFileWarningReport(EditorMessages.buildConfigurationFailed)
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
            assertSingleFileWarningReport(EditorMessages.buildConfigurationFailedInCurrentScript)
        }
    }

    @Test
    fun `report line warning on runtime failure in currently edited script when location aware hints are enabled`() {

        withDefaultSettings()
        withFile("gradle.properties", """
            ${EditorReports.locationAwareEditorHintsPropertyName}=true
        """)
        val editedScript = withBuildScript("""
            configurations.getByName("doNotExists")
        """)

        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolvedDependenciesWithErrors::class)
            assertSingleLineWarningReport("Configuration with name 'doNotExists' not found.", 1)
        }
    }

    @Test
    fun `report file warning on runtime failure in another script`() {

        withSettings("""
            include("a", "b")
        """)
        withBuildScript("")
        withBuildScriptIn("a", """
            configurations.getByName("doNotExists")
        """)
        val editedScript = withBuildScriptIn("b", "")


        resolvedScriptDependencies(editedScript).apply {
            assertContainsBasicDependencies()
        }

        recorder.apply {
            assertLastEventIsInstanceOf(ResolvedDependenciesWithErrors::class)
            assertSingleFileWarningReport(EditorMessages.buildConfigurationFailed)
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
            "gradleHome" to distribution.gradleHomeDir,
            "gradleUserHome" to buildContext.gradleUserHomeDir.canonicalPath
        ) + entries.toMap()

    private
    fun resolvedScriptDependencies(
        scriptFile: File? = null,
        previousDependencies: KotlinScriptExternalDependencies? = null,
        vararg env: Pair<String, Any?>
    ) =
        resolver.resolve(
            scriptContentFor(scriptFile),
            environment(*env),
            recorder,
            previousDependencies
        ).get()

    private
    fun assertSucceeds(editedScript: File? = null) {

        resolvedScriptDependencies(editedScript).apply {
            assertThat(this, notNullValue())
            this!!.assertContainsBasicDependencies()
        }

        recorder.apply {
            assertSuccessScenario()
        }
    }

    private
    fun KotlinScriptExternalDependencies?.assertContainsBasicDependencies() {
        assertThat(this, notNullValue())
        this as KotlinScriptExternalDependencies
        assertTrue(classpath.toList().isNotEmpty())
        assertTrue(imports.toList().isNotEmpty())
        assertTrue(sources.toList().isNotEmpty())
    }
}


internal
fun scriptContentFor(scriptFile: File?) =
    mock<ScriptContents> {
        on { file } doReturn scriptFile
    }


internal
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

    private
    val events = mutableListOf<LogEvent>()

    private
    val reports = mutableListOf<IdeReport>()

    override fun log(event: ResolverEvent) {
        prettyPrint(event).let { prettyPrinted ->
            println(prettyPrinted)
            events.add(LogEvent(event, prettyPrinted))
        }
    }

    override fun invoke(severity: ReportSeverity, message: String, position: Position?) {
        println("[EDITOR_$severity] '$message' ${position?.line ?: ""}")
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
                instanceOf<ResolutionRequest>(),
                instanceOf<SubmittedModelRequest>(),
                instanceOf<ReceivedModelResponse>(),
                instanceOf<ResolvedDependencies>()
            )
        )
        assertThat(events.size, equalTo(4))
        assertNoEditorReport()
    }

    fun <T : ResolverEvent> assertLastEventIsInstanceOf(eventType: KClass<T>) {
        assertThat(
            events.last().event,
            instanceOf(eventType.java)
        )
    }

    fun assertNoEditorReport() {
        assertTrue(reports.isEmpty())
    }

    fun assertSingleEditorReport() {
        assertThat(reports.size, equalTo(1))
    }

    fun assertSingleFileWarningReport(message: String) {
        assertSingleFileReport(ReportSeverity.WARNING, message)
    }

    fun assertSingleFileReport(severity: ReportSeverity, message: String) {
        assertSingleEditorReport()
        reports.single().let { report ->
            assertThat(report.severity, equalTo(severity))
            assertThat(report.position, nullValue())
            assertThat(report.message, equalTo(message))
        }
    }

    fun assertSingleLineWarningReport(message: String, line: Int) {
        assertSingleEditorReport()
        reports.single().let { report ->
            assertThat(report.severity, equalTo(ReportSeverity.WARNING))
            assertThat(report.position, notNullValue())
            assertThat(report.position!!.line, equalTo(line))
            assertThat(report.message, equalTo(message))
        }
    }
}


internal
inline fun <reified T : Any> instanceOf(): Matcher<Any> = instanceOf(T::class.java)
