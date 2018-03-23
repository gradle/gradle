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
package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.JavaVersion.VERSION_1_6
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.JavaVersion.VERSION_1_9
import org.gradle.api.JavaVersion.VERSION_1_10
import org.hamcrest.CoreMatchers.hasItems
import org.junit.Test
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo


class ExcludedTestsTest {
    internal
    val excluder = TestExcluder(listOf(
        // TODO requires investigation
        "DaemonGroovyCompilerIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
        "DaemonJavaCompilerIntegrationTest" to listOf(VERSION_1_8, VERSION_1_9),
        "InProcessJavaCompilerIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10)))

    @Test
    fun `given no excluded tests for a Java version, it returns an empty set`() {
        // then:
        assertThat(
            excluder.excludesForJavaVersion(VERSION_1_6),
            equalTo(emptySet()))
    }

    @Test
    fun `given a Java version, it returns the excluded tests`() {
        // then:
        assertThat(
            excluder.excludesForJavaVersion(VERSION_1_10),
            equalTo(setOf("**/*DaemonGroovyCompilerIntegrationTest*", "**/*InProcessJavaCompilerIntegrationTest*")))
    }

    @Test
    fun `given the default instance, it is properly initialized`() {
        // then:
        assertThat(
            testExcluder.excludesForJavaVersion(excludedTests[0].second[0]),
                hasItems("**/*${excludedTests[0].first}*"))
    }
}
