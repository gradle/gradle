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

import org.gradle.kotlin.dsl.tooling.models.EditorMessages
import org.gradle.kotlin.dsl.tooling.models.EditorReportSeverity

import org.gradle.internal.exceptions.LocationAwareException

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Test


class EditorReportsBuilderTest : TestWithTempFiles() {

    @Test
    fun `report file error on runtime failure in currently edited script on out of range line number`() {

        val script = withTwoLinesScript()

        val reports = buildEditorReportsFor(
            script,
            listOf(LocationAwareException(Exception("BOOM"), script.canonicalPath, 3))
        )

        assertThat(reports.size, equalTo(1))
        reports.single().let { report ->
            assertThat(report.severity, equalTo(EditorReportSeverity.ERROR))
            assertThat(report.position, nullValue())
            assertThat(report.message, equalTo(EditorMessages.buildConfigurationFailedInCurrentScript))
        }
    }

    private
    fun withTwoLinesScript() =
        file("empty.gradle.kts").also { it.writeText("\n\n") }
}
