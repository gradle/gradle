/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinDslPluginsIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class KotlinDslJvmDefaultIntegrationTest : AbstractKotlinDslPluginsIntegrationTest() {

    @Test
    fun `kotlin-dsl scripts can call and override java default methods`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/java/some/Some.java",
            """
            package some;
            public interface Some {
                default void something() {
                    System.out.println("original");
                }
            }
            """
        )
        withFile(
            "buildSrc/src/main/kotlin/some-plugin.gradle.kts",
            """
            import some.Some

            class SomePrecompiledScript : Some

            class SomeOverriddenPrecompiledScript : Some {
                override fun something() {
                    println("precompiled overridden")
                }
            }

            SomePrecompiledScript().something()
            SomeOverriddenPrecompiledScript().something()
            """
        )
        withBuildScript(
            """
            import some.Some

            plugins {
                `some-plugin`
            }

            class SomeBuildScript : Some

            class SomeOverriddenBuildScript : Some {
                override fun something() {
                    println("script overridden")
                }
            }

            tasks.register("test") {
                doLast {
                    SomeBuildScript().something()
                    SomeOverriddenBuildScript().something()
                }
            }
            """
        )

        build("test", "-q").apply {
            assertThat(
                output,
                containsMultiLineString(
                    """
                    original
                    precompiled overridden
                    original
                    script overridden
                    """
                )
            )
        }
    }

    @Test
    fun `kotlin-dsl java and groovy consumers can use kotlin interface default methods directly`() {

        file("settings.gradle.kts").appendText(
            """
            include("kotlin-dsl-producer")
            include("java-consumer")
            include("groovy-consumer")
            """
        )
        withBuildScript("subprojects { ${mavenCentralRepository(KOTLIN)} }")

        withBuildScriptIn("kotlin-dsl-producer", "plugins { `kotlin-dsl` }")
        withFile(
            "kotlin-dsl-producer/src/main/kotlin/some/Some.kt",
            """
            package some
            interface Some { fun some() = Unit }
            """
        )

        withBuildScriptIn(
            "java-consumer",
            """
            plugins { `java-library` }
            dependencies { implementation(project(":kotlin-dsl-producer")) }
            """
        )
        withFile(
            "java-consumer/src/main/java/jc/SomeJava.java",
            """
            package jc;
            class SomeJava implements some.Some {
                public static void main(String[] args) {
                    new SomeJava().some();
                }
            }
            """
        )

        withBuildScriptIn(
            "groovy-consumer",
            """
            plugins { `groovy` }
            dependencies { implementation(project(":kotlin-dsl-producer")) }
            """
        )
        withFile(
            "java-consumer/src/main/groovy/gc/SomeGroovy.groovy",
            """
            package gc;
            class SomeGroovy implements some.Some {
                public static void main(String[] args) {
                    new SomeGroovy().some();
                }
            }
            """
        )

        build("classes")
    }
}
