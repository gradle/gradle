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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsTaskGraphLazyQueriesIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    static final String REPORT_TASK_GRAPH_READS = "-Dorg.gradle.internal.isolated-projects.report-task-graph-reads=true"

    def setup() {
        createDirs("a", "b")
        settingsFile """
            include("a", "b")
        """
    }

    def "project can ask whether tasks of other projects are scheduled without problems"() {
        buildFile("a/build.gradle", """
            class Lint extends DefaultTask {}
            tasks.register("t")
            tasks.register("lint", Lint)
        """)
        buildFile("b/build.gradle", """
            class Lint extends DefaultTask {}

            def tScheduled = gradle.taskGraph.isScheduled(":a:t")
            def lintScheduled = gradle.taskGraph.anyScheduled(Lint)

            tasks.register("report") {
                doLast {
                    println("t scheduled = " + tScheduled.get())
                    println("lint scheduled = " + lintScheduled.get())
                }
            }
        """)

        when:
        isolatedProjectsRun(":a:t", ":a:lint", ":b:report", REPORT_TASK_GRAPH_READS)

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        outputContains("t scheduled = true")
        outputContains("lint scheduled = true")

        when:
        isolatedProjectsRun(":a:t", ":a:lint", ":b:report", REPORT_TASK_GRAPH_READS)

        then:
        fixture.assertStateLoaded()
        outputContains("t scheduled = true")
        outputContains("lint scheduled = true")

        when:
        isolatedProjectsRun(":b:report", REPORT_TASK_GRAPH_READS)

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        outputContains("t scheduled = false")
        outputContains("lint scheduled = false")

        when:
        isolatedProjectsRun(":b:report", REPORT_TASK_GRAPH_READS)

        then:
        fixture.assertStateLoaded()
        outputContains("t scheduled = false")
        outputContains("lint scheduled = false")
    }

    def "reading the task graph from project configuration via #statement is #outcome"() {
        buildFile("a/build.gradle", """
            tasks.register("t")
            $statement
        """)

        when:
        if (optIn) {
            isolatedProjectsFailsUsing(mode, ":a:t", REPORT_TASK_GRAPH_READS)
        } else {
            isolatedProjectsRun(":a:t")
        }

        then:
        if (optIn) {
            fixture.assertIsolatedProjectsProblems(mode) {
                projectsConfigured(":", ":a")
                problem("Build file 'a/build.gradle': line 3: Project ':a' cannot read the task graph at configuration time using 'TaskExecutionGraph.$api'. Use 'TaskExecutionGraph.isScheduled' or 'TaskExecutionGraph.anyScheduled' from a task instead")
            }
        } else {
            fixture.assertStateStored {
                projectsConfigured(":", ":a", ":b")
            }
        }

        where:
        statement                                                                  | api                             | optIn
        "gradle.taskGraph.whenReady { }"                                           | "whenReady"                     | true
        "gradle.taskGraph.whenReady({ } as Action)"                                | "whenReady"                     | true
        "gradle.taskGraph.addTaskExecutionGraphListener({ } as TaskExecutionGraphListener)" | "addTaskExecutionGraphListener" | true
        "gradle.taskGraph.hasTask(':a:t')"                                         | "hasTask"                       | true
        "gradle.taskGraph.whenReady { }"                                           | "whenReady"                     | false

        mode = IsolatedProjectsMode.FAIL_FAST
        outcome = optIn ? "reported when opted in" : "allowed by default"
    }
}
