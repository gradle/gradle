/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.jvm.JavaClassUtil
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class KotlinDslJvmTargetIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `scripts are compiled using the build jvm target`() {

        withClassJar("utils.jar", JavaClassUtil::class.java)

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("utils.jar"))
                }
            }

            $printScriptJavaClassFileMajorVersion
        """)

        assertThat(build("help").output, containsString(outputFor(JavaVersion.current())))
    }

    @Test
    fun `precompiled scripts use jvmTarget 8 by default`() {

        withClassJar("buildSrc/utils.jar", JavaClassUtil::class.java)

        withDefaultSettingsIn("buildSrc")
        withKotlinDslPluginIn("buildSrc").appendText("""
            dependencies {
                implementation(files("utils.jar"))
            }
        """)

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)
        withBuildScript("""plugins { id("some") }""")

        assertThat(build("help").output, containsString(outputFor(JavaVersion.VERSION_1_8)))
    }

    @Test
    fun `can use a different jvmTarget to compile precompiled scripts`() {

        assumeJava11OrHigher()

        withClassJar("buildSrc/utils.jar", JavaClassUtil::class.java)

        withDefaultSettingsIn("buildSrc")
        withKotlinDslPluginIn("buildSrc").appendText("""
            kotlinDslPluginOptions {
                jvmTarget.set("11")
            }

            dependencies {
                implementation(files("utils.jar"))
            }
        """)

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)
        withBuildScript("""plugins { id("some") }""")

        assertThat(build("help").output, containsString(outputFor(JavaVersion.VERSION_11)))
    }

    private
    val printScriptJavaClassFileMajorVersion = """
        println("Java Class Major Version = ${'$'}{org.gradle.integtests.fixtures.jvm.JavaClassUtil.getClassMajorVersion(this::class.java)}")
    """

    private
    fun outputFor(javaVersion: JavaVersion) =
        "Java Class Major Version = ${JavaClassUtil.getClassMajorVersion(javaVersion)}"
}
