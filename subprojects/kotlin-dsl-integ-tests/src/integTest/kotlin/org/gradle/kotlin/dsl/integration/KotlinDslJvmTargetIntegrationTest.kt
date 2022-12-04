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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaClassUtil
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class KotlinDslJvmTargetIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `scripts are compiled using the build jvm target`() {

        val jdk11 = AvailableJavaHomes.getJdk11()!!

        withClassJar("utils.jar", JavaClassUtil::class.java)

        withBuildScript("""
            import org.gradle.integtests.fixtures.jvm.JavaClassUtil

            buildscript {
                dependencies {
                    classpath(files("utils.jar"))
                }
            }

            println("Java Class Major Version = ${'$'}{JavaClassUtil.getClassMajorVersion(this::class.java)}")
        """)

        val result = gradleExecuterFor(arrayOf("help"))
            .withJavaHome(jdk11.javaHome)
            .run()

        assertThat(result.output, containsString("Java Class Major Version = 55"))
    }

    @Test
    fun `can use a different jvmTarget to compile precompiled scripts`() {

        val jdk11 = AvailableJavaHomes.getJdk11()!!

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

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", """
            import org.gradle.integtests.fixtures.jvm.JavaClassUtil

            println("Java Class Major Version = ${'$'}{JavaClassUtil.getClassMajorVersion(this::class.java)}")
        """)
        withBuildScript("""
            plugins {
                id("some")
            }
        """)

        val result = gradleExecuterFor(arrayOf("help"))
            .withJavaHome(jdk11.javaHome)
            .run()

        assertThat(result.output, containsString("Java Class Major Version = 55"))
    }
}
