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

class SoftwareModelTaskReportTaskIntegrationTest extends AbstractIntegrationSpec {
    private final static String[] TASKS_REPORT_TASK = ['tasks'] as String[]
    private final static String[] TASKS_DETAILED_REPORT_TASK = TASKS_REPORT_TASK + ['--all'] as String[]
    private final static String GROUP = 'Hello world'

    def "task report includes tasks defined via model rules running #tasks"() {
        when:
        buildFile """
            model {
                tasks {
                    create('a') {
                        group = '$GROUP'
                        description = "from model rule 1"
                    }
                    create('b') {
                        description = "from model rule 2"
                    }
                }
            }
        """

        then:
        succeeds tasks

        and:
        output.contains("a - from model rule 1") == rendersGroupedTask
        output.contains("b - from model rule 2") == rendersUngroupedTask

        where:
        tasks                      | rendersGroupedTask | rendersUngroupedTask
        TASKS_REPORT_TASK          | true               | false
        TASKS_DETAILED_REPORT_TASK | true               | true
    }

    def "task report includes tasks with dependencies defined via model rules running #tasks"() {
        when:
        buildFile """
            model {
                tasks {
                    create('a')
                    create('b') {
                        dependsOn 'b'
                    }
                }
            }
        """

        then:
        succeeds tasks

        output.contains("""
${getGroupHeader('Other')}
a
b
""") == rendersTasks

        where:
        tasks                      | rendersTasks
        TASKS_REPORT_TASK          | false
        TASKS_DETAILED_REPORT_TASK | true
    }

    def "task report includes task container rule based tasks defined via model rule"() {
        when:
        buildFile """
            tasks.addRule("Pattern: containerRule<ID>") { taskName ->
                if (taskName.startsWith("containerRule")) {
                    task(taskName) {
                        description = "from container rule"
                    }
                }
            }

            model {
                tasks {
                    create("t1") {
                        description = "from model rule"
                        dependsOn "containerRule1"
                    }
                }
            }
        """

        then:
        succeeds TASKS_DETAILED_REPORT_TASK

        and:
        output.contains("t1 - from model rule")
        output.contains("Pattern: containerRule<ID>")
    }

    private static String getGroupHeader(String group) {
        String header = "$group tasks"
        """$header
${'-' * header.length()}"""
    }
}
