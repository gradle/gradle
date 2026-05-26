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

package org.gradle.internal.cc.impl

import org.gradle.api.GradleException
import org.gradle.internal.exceptions.ResolutionProvider
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class ExecutionTimeOnlyOptionsValidationExceptionTest {

    @Test
    fun `is a GradleException so it propagates as a build failure`() {
        val ex = ExecutionTimeOnlyOptionsValidationException(
            executionTimeOnlyTaskPath = ":a",
            configTimeTaskPath = ":b",
            optionName = "tests"
        )
        assertThat(ex, instanceOf(GradleException::class.java))
    }

    @Test
    fun `implements ResolutionProvider so Gradle renders the suggestions`() {
        val ex = ExecutionTimeOnlyOptionsValidationException(
            executionTimeOnlyTaskPath = ":a",
            configTimeTaskPath = ":b",
            optionName = "tests"
        )
        assertThat(ex, instanceOf(ResolutionProvider::class.java))
    }

    @Test
    fun `collision message names both tasks and the option`() {
        val ex = ExecutionTimeOnlyOptionsValidationException(
            executionTimeOnlyTaskPath = ":myTestLike",
            configTimeTaskPath = ":myCustom",
            optionName = "tests"
        )
        val message = ex.message!!
        assertThat(message, containsString("':myTestLike'"))
        assertThat(message, containsString("':myCustom'"))
        assertThat(message, containsString("'--tests'"))
        assertThat(message, containsString("These tasks cannot be invoked together"))
    }

    @Test
    fun `collision resolutions cover the three actionable fixes`() {
        val ex = ExecutionTimeOnlyOptionsValidationException(
            executionTimeOnlyTaskPath = ":myTestLike",
            configTimeTaskPath = ":myCustom",
            optionName = "tests"
        )
        val resolutions = ex.resolutions
        assertThat(resolutions.size, equalTo(3))
        // Annotate the config-time violator.
        assertThat(resolutions[0], containsString("Annotate '--tests' on ':myCustom'"))
        // Rename collision.
        assertThat(resolutions[1], containsString("different option name"))
        // Separate invocations.
        assertThat(resolutions[2], containsString("separate builds"))
    }

    @Test
    fun `stale manifest message degrades gracefully when no contributor is found`() {
        val ex = ExecutionTimeOnlyOptionsValidationException(
            executionTimeOnlyTaskPath = null,
            configTimeTaskPath = ":myCustom",
            optionName = "tests"
        )
        val message = ex.message!!
        assertThat(message, containsString("':myCustom'"))
        assertThat(message, containsString("'--tests'"))
        assertThat(message, containsString("manifest may be stale"))
        // Stale branch must not pretend it knows about a peer task.
        assertThat(message, not(containsString("another task")))
        assertThat(message, not(containsString("cannot be invoked together")))
    }

    @Test
    fun `stale manifest resolutions advise deleting the cache directory`() {
        val ex = ExecutionTimeOnlyOptionsValidationException(
            executionTimeOnlyTaskPath = null,
            configTimeTaskPath = ":myCustom",
            optionName = "tests"
        )
        val resolutions = ex.resolutions
        assertThat(resolutions.size, equalTo(2))
        assertThat(resolutions[0], containsString("Annotate '--tests' on ':myCustom'"))
        assertThat(resolutions[1], containsString("Delete the .gradle/configuration-cache directory"))
    }

    @Test
    fun `field values are preserved verbatim for callers that introspect`() {
        val ex = ExecutionTimeOnlyOptionsValidationException(
            executionTimeOnlyTaskPath = ":a:b",
            configTimeTaskPath = ":c:d",
            optionName = "filter"
        )
        assertThat(ex.executionTimeOnlyTaskPath, equalTo(":a:b"))
        assertThat(ex.configTimeTaskPath, equalTo(":c:d"))
        assertThat(ex.optionName, equalTo("filter"))
    }
}
