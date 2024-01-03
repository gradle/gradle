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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Test
import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginAccessorSettingEvaluationTest : AbstractPrecompiledScriptPluginTest() {

    @Test
    fun `settings and init scripts are not evaluated when generating accessors`() {
        // given:
        val evaluationLog = file("evaluation.log")
        withFolders {
            // a precompiled script plugin contributing an extension
            "producer/src/main/kotlin" {
                withFile(
                    "producer.plugin.gradle.kts",
                    """extensions.add("answer", 42)"""
                )
            }
            // a consumer of the precompiled script plugin extension
            "consumer/src/main/kotlin" {
                withFile(
                    "consumer.plugin.gradle.kts",
                    """
                        plugins { id("producer.plugin") }
                        println(answer)
                    """
                )
            }
        }
        withDefaultSettings().appendText(
            """
                include("producer", "consumer")
                file("${evaluationLog.normalisedPath}").appendText("<settings>")
            """
        )
        withKotlinDslPlugin().appendText(
            """
                subprojects {
                    apply(plugin = "org.gradle.kotlin.kotlin-dsl")
                    $repositoriesBlock
                }
                project(":consumer") {
                    dependencies {
                        implementation(project(":producer"))
                    }
                }
            """
        )

        // and: a bunch of init scripts
        fun initScript(file: File, label: String) = file.apply {
            parentFile.mkdirs()
            writeText("file('${evaluationLog.normalisedPath}') << '$label'")
        }

        val gradleUserHome = newDir("gradle-user-home")

        // <user-home>/init.gradle
        initScript(
            gradleUserHome.resolve("init.gradle"),
            "<init>"
        )
        // <user-home>/init.d/init.gradle
        initScript(
            gradleUserHome.resolve("init.d/init.gradle"),
            "<init.d>"
        )
        // -I init.gradle
        val initScript = initScript(
            file("init.gradle"),
            "<command-line>"
        )

        // when: precompiled script plugin accessors are generated
        buildWithGradleUserHome(
            gradleUserHome,
            "generatePrecompiledScriptPluginAccessors",
            "-I",
            initScript.absolutePath
        ).apply {
            // then: the settings and init scripts are only evaluated once by the outer build
            MatcherAssert.assertThat(
                evaluationLog.text,
                CoreMatchers.equalTo("<command-line><init><init.d><settings>")
            )
        }
    }

    private
    fun buildWithGradleUserHome(gradleUserHomeDir: File, vararg arguments: String) =
        gradleExecuterFor(arguments)
            .withGradleUserHomeDir(gradleUserHomeDir)
            .withOwnUserHomeServices()
            .run()
}
