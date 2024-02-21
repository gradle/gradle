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

package gradlebuild.binarycompatibility

import com.google.gson.Gson
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class SortAcceptedApiChangesTaskIntegrationTest : AbstractAcceptedApiChangesMaintenanceTaskIntegrationTest() {
    @Test
    fun `verify misordered changes can be sorted`() {
        //language=JSON
        acceptedApiChangesFile.writeText(
            """
                {
                    "acceptedApiChanges": [
                        {
                            "type": "org.gradle.api.file.SourceDirectorySet",
                            "member": "Method org.gradle.api.file.SourceDirectorySet.getOutputDir()",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
{
                            "type": "org.gradle.api.tasks.AbstractExecTask",
                            "member": "Method org.gradle.api.tasks.AbstractExecTask.getExecResult()",
                            "acceptation": "Removed for Gradle 8.0",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
                        {
                            "type": "org.gradle.api.tasks.testing.AbstractTestTask",
                            "member": "Method org.gradle.api.tasks.testing.AbstractTestTask.getBinResultsDir()",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
                        {
                            "type": "org.gradle.api.AntBuilder",
                            "member": "Class org.gradle.api.AntBuilder",
                            "acceptation": "org.gradle.api.AntBuilder now extends groovy.ant.AntBuilder",
                            "changes": [
                                "Abstract method has been added in implemented interface"
                            ]
                        },
                        {
                            "type": "org.gradle.api.file.SourceDirectorySet",
                            "member": "Method org.gradle.api.file.SourceDirectorySet.setOutputDir(org.gradle.api.provider.Provider)",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
                        {
                            "type": "org.gradle.api.file.SourceDirectorySet",
                            "member": "Method org.gradle.api.file.SourceDirectorySet.setOutputDir(java.io.File)",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        }
                    ]
                }
            """.trimIndent()
        )

        val initialVerifyResult = run(":verifyAcceptedApiChangesOrdering").buildAndFail()
        Assertions.assertEquals(TaskOutcome.FAILED, initialVerifyResult.task(":verifyAcceptedApiChangesOrdering")!!.outcome)

        val sortingResult = run(":sortAcceptedApiChanges").build()
        Assertions.assertEquals(TaskOutcome.SUCCESS, sortingResult.task(":sortAcceptedApiChanges")!!.outcome)

        val finalVerifyResult = run(":verifyAcceptedApiChangesOrdering").build()
        Assertions.assertEquals(TaskOutcome.SUCCESS, finalVerifyResult.task(":verifyAcceptedApiChangesOrdering")!!.outcome)

        val expectedJson = loadChangesJson(
            """
                {
                    "acceptedApiChanges": [
                        {
                            "type": "org.gradle.api.AntBuilder",
                            "member": "Class org.gradle.api.AntBuilder",
                            "acceptation": "org.gradle.api.AntBuilder now extends groovy.ant.AntBuilder",
                            "changes": [
                                "Abstract method has been added in implemented interface"
                            ]
                        },
                        {
                            "type": "org.gradle.api.file.SourceDirectorySet",
                            "member": "Method org.gradle.api.file.SourceDirectorySet.getOutputDir()",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
                        {
                            "type": "org.gradle.api.file.SourceDirectorySet",
                            "member": "Method org.gradle.api.file.SourceDirectorySet.setOutputDir(java.io.File)",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
                        {
                            "type": "org.gradle.api.file.SourceDirectorySet",
                            "member": "Method org.gradle.api.file.SourceDirectorySet.setOutputDir(org.gradle.api.provider.Provider)",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
                        {
                            "type": "org.gradle.api.tasks.AbstractExecTask",
                            "member": "Method org.gradle.api.tasks.AbstractExecTask.getExecResult()",
                            "acceptation": "Removed for Gradle 8.0",
                            "changes": [
                                "Method has been removed"
                            ]
                        },
                        {
                            "type": "org.gradle.api.tasks.testing.AbstractTestTask",
                            "member": "Method org.gradle.api.tasks.testing.AbstractTestTask.getBinResultsDir()",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        }
                    ]
                }
            """)
        val actualJson = loadChangesJson(acceptedApiChangesFile.readText())

        Assertions.assertEquals(expectedJson, actualJson)
    }

    private
    fun loadChangesJson(rawText: String) = Gson().fromJson(rawText, AbstractAcceptedApiChangesMaintenanceTask.AcceptedApiChanges::class.java)
}
