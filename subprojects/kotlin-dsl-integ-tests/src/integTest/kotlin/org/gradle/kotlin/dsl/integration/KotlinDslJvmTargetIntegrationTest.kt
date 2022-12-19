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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.classanalysis.JavaClassUtil
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assume.assumeThat
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
    fun `precompiled scripts use the build jvm target default`() {

        withClassJar("buildSrc/utils.jar", JavaClassUtil::class.java)

        withDefaultSettingsIn("buildSrc")
        withKotlinDslPluginIn("buildSrc").appendText("""
            dependencies {
                implementation(files("utils.jar"))
            }
        """)

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)
        withBuildScript("""plugins { id("some") }""")

        assertThat(build("help").output, containsString(outputFor(JavaVersion.current())))
    }

    @Test
    fun `can use a different jvmTarget to compile precompiled scripts`() {

        assumeJava11OrHigher()

        withClassJar("buildSrc/utils.jar", JavaClassUtil::class.java)

        withDefaultSettingsIn("buildSrc")
        withKotlinDslPluginIn("buildSrc").appendText("""

            java {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }

            kotlinDslPluginOptions {
                jvmTarget.set("11")
            }

            dependencies {
                implementation(files("utils.jar"))
            }
        """)

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)
        withBuildScript("""plugins { id("some") }""")

        executer.expectDocumentedDeprecationWarning("The KotlinDslPluginOptions.jvmTarget property has been deprecated. This is scheduled to be removed in Gradle 9.0. Configure a Java Toolchain instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#kotlin_dsl_plugin_toolchains")

        assertThat(build("help").output, containsString(outputFor(JavaVersion.VERSION_11)))
    }

    @Test
    fun `can use Java Toolchain to compile precompiled scripts`() {

        val jdk11 = AvailableJavaHomes.getJdk11()
        assumeThat(jdk11, not(nullValue()))

        withClassJar("buildSrc/utils.jar", JavaClassUtil::class.java)

        withDefaultSettingsIn("buildSrc")
        withKotlinDslPluginIn("buildSrc").appendText("""

            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(11))
                }
            }

            dependencies {
                implementation(files("utils.jar"))
            }
        """)

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)
        withBuildScript("""plugins { id("some") }""")

        val result = gradleExecuterFor(arrayOf("help"))
            .withArgument("-Porg.gradle.java.installations.paths=${jdk11!!.javaHome.absolutePath}")
            .run()

        assertThat(result.output, containsString(outputFor(JavaVersion.VERSION_11)))
    }

    private
    val printScriptJavaClassFileMajorVersion = """
        println("Java Class Major Version = ${'$'}{org.gradle.internal.classanalysis.JavaClassUtil.getClassMajorVersion(this::class.java)}")
    """

    private
    fun outputFor(javaVersion: JavaVersion) =
        "Java Class Major Version = ${JavaClassUtil.getClassMajorVersion(javaVersion)}"
}
