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

package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SoftwareModelTaskReportIntegrationTest extends AbstractIntegrationSpec {
    private final static String[] TASKS_REPORT_TASK = ['tasks'] as String[]
    private final static String[] TASKS_DETAILED_REPORT_TASK = TASKS_REPORT_TASK + ['--all'] as String[]

    def "renders tasks with dependencies created by model rules running #tasks"() {
        when:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile """
            plugins {
                id 'component-model-base'
            }

            model {
                tasks {
                    create('a')
                }
            }

            task b {
                dependsOn 'a'
            }

            task c

            model {
                tasks {
                    create('d') {
                        dependsOn c
                    }
                }
            }
        """

        then:
        succeeds tasks

        output.contains("""
${otherGroupHeader}
a
b
c
components - Displays the components produced by root project 'test-project'. [deprecated]
d
dependentComponents - Displays the dependent components of components in root project 'test-project'. [deprecated]
model - Displays the configuration model of root project 'test-project'. [deprecated]
""") == rendersTasks

        where:
        tasks                      | rendersTasks
        TASKS_REPORT_TASK          | false
        TASKS_DETAILED_REPORT_TASK | true
    }

    private static String getOtherGroupHeader() {
        String header = "Other tasks"
        """$header
${'-' * header.length()}"""
    }
}
