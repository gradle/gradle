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

package org.gradle.kotlin.dsl.support

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class KotlinScriptHashingTest {

    private
    val headers = listOf(
        "",
        "\n\n",
        "/*\n * License header\n */\n\n",
        "/* outer /* nested */ still a comment */\n// line comment\n",
        "#!/usr/bin/env kotlin\n",
        "@file:Suppress(\"UnstableApiUsage\")\n\n",
        "/*\n * License\n */\n@file:Suppress(\"a)\", \"b\")\n@file:[OptIn(Foo::class) JvmName(\"x\")]\n",
        "#!/usr/bin/env kotlin\n/* License */\n@file:Suppress(\"x\")\n",
    )

    private
    val body = "\nplugins { java }\n\ntasks.register(\"hello\") { println(\"package not.this\") }\n"

    private
    fun variantsOf(text: String) = listOf(
        text,
        text.replace("\n", "\r\n"),
        "﻿" + text,
        "﻿" + text.replace("\n", "\r\n"),
    )

    @Test
    fun `package is found after any header in every line separator and BOM variant`() {
        for (header in headers) {
            for (variant in variantsOf(header + "package my.conv" + body)) {
                assertThat(variant, ScriptPackageScanner.packageOf(variant), equalTo("my.conv"))
            }
        }
    }

    @Test
    fun `no package is found when there is no package declaration`() {
        for (header in headers) {
            for (variant in variantsOf(header + body)) {
                assertThat(variant, ScriptPackageScanner.packageOf(variant), nullValue())
            }
        }
    }

    @Test
    fun `key is the same for every line separator and BOM variant and ignores the body`() {
        for (header in headers) {
            val expected = KotlinScriptHashing.precompiledScriptKeyOfText(header + "package my.conv" + body, "my-plugin.gradle.kts")
            for (variant in variantsOf(header + "package my.conv" + body.replace("hello", "edited"))) {
                assertThat(variant, KotlinScriptHashing.precompiledScriptKeyOfText(variant, "my-plugin.gradle.kts"), equalTo(expected))
            }
        }
    }

    @Test
    fun `key depends on package and file name`() {
        val key = KotlinScriptHashing.precompiledScriptKeyOfText("package a\n", "p.gradle.kts")
        assertThat(KotlinScriptHashing.precompiledScriptKeyOfText("package b\n", "p.gradle.kts"), not(equalTo(key)))
        assertThat(KotlinScriptHashing.precompiledScriptKeyOfText("", "p.gradle.kts"), not(equalTo(key)))
        assertThat(KotlinScriptHashing.precompiledScriptKeyOfText("package a\n", "q.gradle.kts"), not(equalTo(key)))
    }
}
