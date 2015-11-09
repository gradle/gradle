/*
 * Copyright 2012 the original author or authors.
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
import spock.lang.Issue

class TaskReportTaskIntegrationTest extends AbstractIntegrationSpec {

    def "task selector description is taken from task that TaskNameComparator considers to be of lowest ordering"() {
        given:
        settingsFile << """
include 'sub1'
include 'sub2'
"""
        file("sub1/build.gradle") << """
task alpha { description = 'ALPHA_in_sub1' }
"""
        file("sub2/build.gradle") << """
task alpha { description = 'ALPHA_in_sub2' }
"""

        when:
        run "tasks"

        then:
        output.contains """
Other tasks
-----------
alpha - ALPHA_in_sub1
"""
    }

    def "task report includes tasks defined via model rules"() {
        when:
        buildScript """
            model {
                tasks {
                    create("t1") {
                        description = "from model rule"
                    }
                }
            }
        """

        then:
        succeeds "tasks"

        and:
        output.contains("t1 - from model rule")
    }

    def "task report includes task container rule based tasks which are a dependency of a task defined via model rule"() {
        when:
        buildScript """
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
        succeeds "tasks", "--all"

        and:
        output.contains("t1 - from model rule")
        output.contains("containerRule1 - from container rule")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2023")
    def "can deal with tasks with named task dependencies that are created by rules"() {
        when:
        buildFile << getBuildScriptContent()

        then:
        succeeds "tasks", "--all"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2023")
    def "can deal with tasks with named task dependencies that are created by rules - multiproject"() {
        when:
        settingsFile << "include 'module'"

        file("module/build.gradle") << getBuildScriptContent()

        then:
        succeeds "tasks", "--all"
    }

    protected static String getBuildScriptContent() {
        """
            tasks.addRule("test rule") {
                if (it.startsWith("autoCreate")) {
                    def name = it - "autoCreate"
                    name = name[0].toLowerCase() + name[1..-1]
                    if (tasks.findByName(name)) {
                        project.tasks.create(it)
                    }
                }
            }

            // Source task must be alphabetically before task that is created by dependency
            task aaa { dependsOn("autoCreateFoo") }
            task foo
        """
    }

}
