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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.kotlin.dsl.tooling.models.EditorReportSeverity

import org.gradle.kotlin.dsl.resolver.EditorMessages

import org.gradle.internal.exceptions.LocationAwareException

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class EditorReportsBuilderTest : TestWithTempFiles() {

    @Test
    fun `report file warning on runtime failure in currently edited script on out of range line number`() {

        val script = withTwoLinesScript()

        val reports = buildEditorReportsFor(
            script,
            listOf(LocationAwareException(Exception("BOOM"), script.canonicalPath, 3)),
            true
        )

        assertThat(reports.size, equalTo(1))
        reports.single().let { report ->
            assertThat(report.severity, equalTo(EditorReportSeverity.WARNING))
            assertThat(report.position, nullValue())
            assertThat(report.message, equalTo(EditorMessages.buildConfigurationFailedInCurrentScript))
        }
    }

    @Test
    fun `report line warning on runtime failure in currently edited script last line without new line before eof`() {

        val script = file("some.gradle.kts").also { it.writeText("\n\nno-new-line") }

        val reports = buildEditorReportsFor(
            script,
            listOf(LocationAwareException(Exception("BOOM"), script.canonicalPath, 3)),
            true
        )

        assertThat(reports.size, equalTo(1))
        reports.single().let { report ->
            assertThat(report.severity, equalTo(EditorReportSeverity.WARNING))
            assertThat(report.position, notNullValue())
            assertThat(report.position!!.line, equalTo(3))
            assertThat(report.message, equalTo("BOOM"))
        }
    }

    @Test
    fun `report line warning on runtime failure in currently edited script with cause without message`() {

        val script = withTwoLinesScript()

        val reports = buildEditorReportsFor(
            script,
            listOf(
                LocationAwareException(java.lang.Exception(null as String?), script.canonicalPath, 1),
                LocationAwareException(java.lang.Exception(""), script.canonicalPath, 2)
            ),
            true
        )

        assertThat(reports.size, equalTo(2))
        reports.forEachIndexed { idx, report ->
            assertThat(report.severity, equalTo(EditorReportSeverity.WARNING))
            assertThat(report.position, notNullValue())
            assertThat(report.position!!.line, equalTo(idx + 1))
            assertThat(report.message, equalTo(EditorMessages.defaultLocationAwareHintMessageFor(java.lang.Exception())))
        }
    }

    @Test
    fun `valid line number range for file`() {

        val file = file("script.gradle.kts")

        file.writeText("")
        assertThat(file.readLinesRange(), equalTo(0..0L))

        file.writeText("some")
        assertThat(file.readLinesRange(), equalTo(1..1L))

        file.writeText("some\n")
        assertThat(file.readLinesRange(), equalTo(1..1L))

        file.writeText("some\nmore")
        assertThat(file.readLinesRange(), equalTo(1..2L))

        file.writeText("some\nmore\n")
        assertThat(file.readLinesRange(), equalTo(1..2L))

        file.writeText("some\nmore\nthings")
        assertThat(file.readLinesRange(), equalTo(1..3L))
    }

    private
    fun withTwoLinesScript() =
        file("two.gradle.kts").also { it.writeText("\n\n") }
}
