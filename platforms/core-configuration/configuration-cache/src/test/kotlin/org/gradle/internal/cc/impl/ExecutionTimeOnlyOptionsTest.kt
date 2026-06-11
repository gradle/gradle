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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class ExecutionTimeOnlyOptionsTest {

    @Test
    fun `stripFrom returns input unchanged when candidate names are empty`() {
        val args = listOf("foo", "--bar", "baz")
        assertThat(ExecutionTimeOnlyOptionsManifestService.stripFrom(args, emptySet()), equalTo(args))
    }

    @Test
    fun `extractFrom returns empty list when candidate names are empty`() {
        val args = listOf("foo", "--bar", "baz")
        assertThat(ExecutionTimeOnlyOptionsManifestService.extractFrom(args, emptySet()), equalTo(emptyList()))
    }

    @Test
    fun `stripFrom drops --name value pair`() {
        val args = listOf("task", "--tests", "MyClass", "--other")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests")),
            equalTo(listOf("task", "--other"))
        )
    }

    @Test
    fun `extractFrom keeps --name value pair`() {
        val args = listOf("task", "--tests", "MyClass", "--other")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests")),
            equalTo(listOf("--tests", "MyClass"))
        )
    }

    @Test
    fun `stripFrom drops --name=value token`() {
        val args = listOf("task", "--tests=MyClass", "--other")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests")),
            equalTo(listOf("task", "--other"))
        )
    }

    @Test
    fun `extractFrom keeps --name=value token`() {
        val args = listOf("task", "--tests=MyClass", "--other")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests")),
            equalTo(listOf("--tests=MyClass"))
        )
    }

    @Test
    fun `stripFrom handles trailing --name without value`() {
        val args = listOf("task", "--tests")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests")),
            equalTo(listOf("task"))
        )
    }

    @Test
    fun `extractFrom handles trailing --name without value`() {
        val args = listOf("task", "--tests")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests")),
            equalTo(listOf("--tests"))
        )
    }

    @Test
    fun `stripFrom and extractFrom handle multiple occurrences of the same flag`() {
        val args = listOf(":a", "--tests", "X", ":b", "--tests", "Y")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests")),
            equalTo(listOf(":a", ":b"))
        )
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests")),
            equalTo(listOf("--tests", "X", "--tests", "Y"))
        )
    }

    @Test
    fun `option name is matched exactly to avoid substring collision`() {
        // --tests-extra is NOT --tests; both helpers must leave it alone when only `tests` is a candidate.
        val args = listOf("task", "--tests-extra", "value", "--tests", "X")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests")),
            equalTo(listOf("task", "--tests-extra", "value"))
        )
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests")),
            equalTo(listOf("--tests", "X"))
        )
    }

    @Test
    fun `--name=value key form is matched exactly to avoid substring collision`() {
        // --tests-extra=v shares the `tests` prefix but is not the `tests` option.
        val args = listOf("task", "--tests-extra=v", "--tests=X")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests")),
            equalTo(listOf("task", "--tests-extra=v"))
        )
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests")),
            equalTo(listOf("--tests=X"))
        )
    }

    @Test
    fun `multiple candidate names`() {
        val args = listOf("task", "--tests", "X", "--filter", "Y", "--other", "Z")
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests", "filter")),
            equalTo(listOf("task", "--other", "Z"))
        )
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests", "filter")),
            equalTo(listOf("--tests", "X", "--filter", "Y"))
        )
    }

    @Test
    fun `empty args produces empty result for both helpers`() {
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.stripFrom(emptyList(), setOf("tests")),
            equalTo(emptyList())
        )
        assertThat(
            ExecutionTimeOnlyOptionsManifestService.extractFrom(emptyList(), setOf("tests")),
            equalTo(emptyList())
        )
    }

    @Test
    fun `round-trip — strip + extract partition all candidate-related tokens`() {
        // For any args, strip produces tokens not belonging to candidates, extract produces those that do.
        // The union must contain every token from args except adjacent values consumed by --name forms.
        val args = listOf("task", "--tests", "X", "--keep", "--tests=Y", "tail")
        val stripped = ExecutionTimeOnlyOptionsManifestService.stripFrom(args, setOf("tests"))
        val extracted = ExecutionTimeOnlyOptionsManifestService.extractFrom(args, setOf("tests"))
        assertThat(stripped, equalTo(listOf("task", "--keep", "tail")))
        assertThat(extracted, equalTo(listOf("--tests", "X", "--tests=Y")))
    }
}
