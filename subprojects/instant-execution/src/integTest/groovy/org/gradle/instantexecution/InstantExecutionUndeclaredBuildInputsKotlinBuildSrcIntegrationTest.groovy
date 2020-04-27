/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.integtests.fixtures.instantexecution.HasInstantExecutionProblemsSpec

class InstantExecutionUndeclaredBuildInputsKotlinBuildSrcIntegrationTest extends AbstractInstantExecutionUndeclaredBuildInputsIntegrationTest {
    @Override
    void pluginDefinition() {
        file("buildSrc/build.gradle.kts").text = KotlinDslTestUtil.kotlinDslBuildSrcScript
        file("buildSrc/src/main/kotlin/SneakyPlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SneakyPlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    val ci = System.getProperty("CI")
                    println("apply CI = " + ci)
                    println("apply CI2 = \${System.getProperty("CI2")}")
                    project.tasks.register("thing") {
                        doLast {
                            val ci2 = System.getProperty("CI")
                            println("task CI = " + ci2)
                        }
                    }
                }
            }
        """
    }

    @Override
    void additionalProblems(HasInstantExecutionProblemsSpec spec) {
        spec.withProblem("unknown property: read system property 'kotlin.gradle.test.report.memory.usage' from 'org.jetbrains.kotlin.gradle.plugin.KotlinGradleBuildServices'")
        spec.withProblem("unknown property: read system property 'idea.active' from 'org.jetbrains.kotlin.gradle.plugin.statistics.DefaultKotlinBuildStatsService'")
    }
}
