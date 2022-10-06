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

import org.junit.jupiter.api.Test


class AlphabeticalAcceptedApiChangesTaskIntegrationTest : AbstractAcceptedApiChangesMaintenanceTaskIntegrationTest() {
    @Test
    fun `verify AlphabeticalAcceptedApiChangesTask detects misordered changes`() {
        //language=JSON
        acceptedApiChangesFile.writeText(
            """
                {
                    "acceptedApiChanges": [
                        {
                            "type": "org.gradle.api.tasks.AbstractExecTask",
                            "member": "Method org.gradle.api.tasks.AbstractExecTask.getExecResult()",
                            "acceptation": "Removed for Gradle 8.0",
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
                            "member": "Method org.gradle.api.file.SourceDirectorySet.getOutputDir()",
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
                            "type": "org.gradle.api.file.SourceDirectorySet",
                            "member": "Method org.gradle.api.file.SourceDirectorySet.setOutputDir(java.io.File)",
                            "acceptation": "Deprecated method removed",
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
            """.trimIndent()
        )

        assertHasMisorderedChanges(
            listOf(
                Change("org.gradle.api.tasks.AbstractExecTask", "Method org.gradle.api.tasks.AbstractExecTask.getExecResult()"),
                Change("org.gradle.api.AntBuilder", "Class org.gradle.api.AntBuilder"),
                Change("org.gradle.api.file.SourceDirectorySet", "Method org.gradle.api.file.SourceDirectorySet.getOutputDir()"),
                Change("org.gradle.api.file.SourceDirectorySet", "Method org.gradle.api.file.SourceDirectorySet.setOutputDir(java.io.File)")
            )
        )
    }

    @Test
    fun `verify AlphabeticalAcceptedApiChangesTask accepts properly ordered changes`() {
        //language=JSON
        acceptedApiChangesFile.writeText(
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
                            "member": "Method org.gradle.api.file.SourceDirectorySet.setOutputDir(org.gradle.api.provider.Provider)",
                            "acceptation": "Deprecated method removed",
                            "changes": [
                                "Method has been removed"
                            ]
                        }
                    ]
                }
            """.trimIndent()
        )

        assertChangesProperlyOrdered()
    }
}
