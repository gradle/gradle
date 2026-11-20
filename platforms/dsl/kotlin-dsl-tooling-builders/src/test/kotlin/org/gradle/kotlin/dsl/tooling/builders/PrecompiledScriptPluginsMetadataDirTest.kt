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
package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.support.KotlinScriptHashing
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class PrecompiledScriptPluginsMetadataDirTest : TestWithTempFiles() {

    @Test
    fun `accessors imports are still found after a body-only edit of the script`() {
        val script = newFile("src/main/kotlin/my/conv/my-plugin.gradle.kts", "package my.conv\n\nplugins { java }\n\nprintln(\"v1\")\n")
        val metadataDir = newFolder()
        metadataDir.resolve("accessors").apply { mkdirs() }
            .resolve(KotlinScriptHashing.precompiledScriptKeyOfFile(script))
            .writeText("gradle.kotlin.dsl.accessors._abc.*")

        script.writeText(script.readText().replace("v1", "v2").replace("\n", "\r\n"))

        assertThat(
            PrecompiledScriptPluginsMetadataDir(metadataDir).implicitAccessorsImports(script),
            equalTo(listOf("gradle.kotlin.dsl.accessors._abc.*"))
        )
    }
}
