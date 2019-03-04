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

package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.fixtures.matching

import org.gradle.kotlin.dsl.integration.ScriptModelIntegrationTest

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class AccessorsClassPathIntegrationTest : ScriptModelIntegrationTest() {

    @Test
    fun `classpath model includes jit accessors by default`() {

        withDefaultSettings()
        val buildFile = withBuildScript("""
            plugins { java }
        """)

        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `jit accessors can be turned off`() {

        withDefaultSettings()
        val buildFile = withBuildScript("""
            plugins { java }
        """)

        withFile("gradle.properties", "org.gradle.kotlin.dsl.accessors=off")

        assertThat(
            classPathFor(buildFile),
            not(hasAccessorsClasses())
        )
    }

    @Test
    fun `the set of jit accessors is a function of the set of applied plugins`() {

        // TODO:accessors - rework this test to ensure it's providing enough coverage
        val s1 = setOfAutomaticAccessorsFor(setOf("application"))
        val s2 = setOfAutomaticAccessorsFor(setOf("java"))
        val s3 = setOfAutomaticAccessorsFor(setOf("application"))
        val s4 = setOfAutomaticAccessorsFor(setOf("application", "java"))
        val s5 = setOfAutomaticAccessorsFor(setOf("java"))

        assertThat(s1, not(equalTo(s2))) // application ≠ java
        assertThat(s1, equalTo(s3))      // application = application
        assertThat(s2, equalTo(s5))      // java        = java
        assertThat(s1, equalTo(s4))      // application ⊇ java
    }

    @Test
    fun `warning is emitted if a gradle slash project dash schema dot json file is present`() {

        withDefaultSettings()
        withBuildScript("")

        withFile(projectSchemaResourcePath)

        assertThat(build("help").output, containsString(projectSchemaResourceDiscontinuedWarning))
    }

    private
    fun setOfAutomaticAccessorsFor(plugins: Set<String>): File {
        withDefaultSettings()
        val script = "plugins {\n${plugins.joinToString(separator = "\n")}\n}"
        val buildFile = withBuildScript(script, produceFile = ::newOrExisting)
        return accessorsClassFor(buildFile)!!.relativeTo(buildFile.parentFile)
    }

    private
    fun assertAccessorsInClassPathOf(buildFile: File) {
        val model = kotlinBuildScriptModelFor(buildFile)
        assertThat(model.classPath, hasAccessorsClasses())
        assertThat(model.sourcePath, hasAccessorsSource())
    }

    private
    fun hasAccessorsSource() =
        hasItem(
            matching<File>({ appendText("accessors source") }) {
                resolve(accessorsSourceFilePath).isFile
            }
        )

    private
    fun hasAccessorsClasses() =
        hasItem(
            matching<File>({ appendText("accessors classes") }) {
                resolve(accessorsClassFilePath).isFile
            }
        )

    private
    fun accessorsClassFor(buildFile: File) =
        classPathFor(buildFile).find {
            it.isDirectory && it.resolve(accessorsClassFilePath).isFile
        }

    private
    val accessorsSourceFilePath = "org/gradle/kotlin/dsl/ArchivesConfigurationAccessors.kt"

    private
    val accessorsClassFilePath = "org/gradle/kotlin/dsl/ArchivesConfigurationAccessorsKt.class"
}
