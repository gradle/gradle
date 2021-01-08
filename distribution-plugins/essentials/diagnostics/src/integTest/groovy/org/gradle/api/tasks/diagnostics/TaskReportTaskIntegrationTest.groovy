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
import spock.lang.Unroll

class TaskReportTaskIntegrationTest extends AbstractIntegrationSpec {

    private final static String[] TASKS_REPORT_TASK = ['tasks'] as String[]
    private final static String[] TASKS_DETAILED_REPORT_TASK = TASKS_REPORT_TASK + ['--all'] as String[]
    private final static String GROUP = 'Hello world'

    @Unroll
    def "always renders default tasks running #tasks"() {
        given:
        String projectName = 'test'
        settingsFile << "rootProject.name = '$projectName'"

        when:
        succeeds tasks

        then:
        output.contains("""
Build Setup tasks
-----------------
init - Initializes a new Gradle build.
wrapper - Generates Gradle wrapper files.

Help tasks
----------
buildEnvironment - Displays all buildscript dependencies declared in root project '$projectName'.
dependencies - Displays all dependencies declared in root project '$projectName'.
dependencyInsight - Displays the insight into a specific dependency in root project '$projectName'.
help - Displays a help message.
javaToolchains - Displays the detected java toolchains.
outgoingVariants - Displays the outgoing variants of root project '$projectName'.
projects - Displays the sub-projects of root project '$projectName'.
properties - Displays the properties of root project '$projectName'.
tasks - Displays the tasks runnable from root project '$projectName'.""")

        where:
        tasks << [TASKS_REPORT_TASK, TASKS_DETAILED_REPORT_TASK]
    }

    @Unroll
    def "always renders task rule running #tasks"() {
        given:
        buildFile << """
            tasks.addRule("Pattern: ping<ID>") { String taskName ->
                if (taskName.startsWith("ping")) {
                    task(taskName) {
                        doLast {
                            println "Pinging: " + (taskName - 'ping')
                        }
                    }
                }
            }
        """

        when:
        succeeds tasks

        then:
        output.contains("""
Rules
-----
Pattern: ping<ID>
""")
        where:
        tasks << [TASKS_REPORT_TASK, TASKS_DETAILED_REPORT_TASK]
    }

    @Unroll
    def "renders tasks with and without group running #tasks"() {
        given:
        buildFile << """
            task a {
                group = '$GROUP'
            }

            task b
        """

        when:
        succeeds tasks

        then:
        output.contains("""
$helloWorldGroupHeader
a
""") == rendersGroupedTask
        output.contains("""
$otherGroupHeader
b
""") == rendersUngroupedTask

        where:
        tasks                      | rendersGroupedTask | rendersUngroupedTask
        TASKS_REPORT_TASK          | true               | false
        TASKS_DETAILED_REPORT_TASK | true               | true
    }

    @Unroll
    def "renders task with dependencies without group in detailed report running #tasks"() {
        given:
        buildFile << """
            task a

            task b {
                dependsOn a
            }
        """

        when:
        succeeds tasks

        then:
        output.contains("""
$otherGroupHeader
a
b
""") == rendersTasks

        where:
        tasks                      | rendersTasks
        TASKS_REPORT_TASK          | false
        TASKS_DETAILED_REPORT_TASK | true
    }

    @Unroll
    def "renders grouped task with dependencies in detailed report running #tasks"() {
        given:
        buildFile << """
            task a {
                group = '$GROUP'
            }

            task b {
                group = '$GROUP'
                dependsOn a
            }
        """

        when:
        succeeds tasks

        then:
        output.contains("""
$helloWorldGroupHeader
a
b
""")

        where:
        tasks                      | rendersTasks
        TASKS_REPORT_TASK          | true
        TASKS_DETAILED_REPORT_TASK | true
    }

    def "renders only tasks in help group running [tasks, --group=build setup"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            task mytask {
                group = "custom"
            }
        """
        when:
        succeeds "tasks", "--group=build setup"

        then:
        output.contains("""
------------------------------------------------------------
Tasks runnable from root project 'test'
------------------------------------------------------------

Build Setup tasks
-----------------
init - Initializes a new Gradle build.
wrapper - Generates Gradle wrapper files.

To see all tasks and more detail, run gradle tasks --all

To see more detail about a task, run gradle help --task <task>
""")
        !output.contains("custom")
    }

    def "renders tasks in a multi-project build running [tasks]"() {
        given:
        buildFile << multiProjectBuild()
        settingsFile << "include 'sub1', 'sub2'"

        when:
        succeeds TASKS_REPORT_TASK

        then:
        output.contains("""
$helloWorldGroupHeader
a
""")
        !output.contains("""
$otherGroupHeader
c
""")
    }

    def "renders tasks in a multi-project build running [tasks, --all]"() {
        given:
        buildFile << multiProjectBuild()
        settingsFile << "include 'sub1', 'sub2'"

        when:
        succeeds TASKS_DETAILED_REPORT_TASK

        then:
        output.contains("""
$helloWorldGroupHeader
a
sub1:a
sub2:a
""")
        output.contains("""
$otherGroupHeader
sub1:b
sub2:b
c
""")
    }

    def "task selector description is taken from task that TaskNameComparator considers to be of lowest ordering"() {
        given:
        settingsFile << """
include 'sub1'
include 'sub2'
"""
        file("sub1/build.gradle") << """
            task alpha {
                group = '$GROUP'
                description = 'ALPHA_in_sub1'
            }
        """
        file("sub2/build.gradle") << """
            task alpha {
                group = '$GROUP'
                description = 'ALPHA_in_sub2'
            }
        """

        when:
        succeeds TASKS_REPORT_TASK

        then:
        output.contains """
$helloWorldGroupHeader
alpha - ALPHA_in_sub1
"""
    }

    @Unroll
    def "task report includes tasks defined via model rules running #tasks"() {
        when:
        buildScript """
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

    @Unroll
    def "task report includes tasks with dependencies defined via model rules running #tasks"() {
        when:
        buildScript """
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
$otherGroupHeader
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
        succeeds TASKS_DETAILED_REPORT_TASK

        and:
        output.contains("t1 - from model rule")
        output.contains("Pattern: containerRule<ID>")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2023")
    def "can deal with tasks with named task dependencies that are created by rules"() {
        when:
        buildFile << getBuildScriptContent()

        then:
        succeeds TASKS_DETAILED_REPORT_TASK
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2023")
    def "can deal with tasks with named task dependencies that are created by rules - multiproject"() {
        when:
        settingsFile << "include 'module'"

        file("module/build.gradle") << getBuildScriptContent()

        then:
        succeeds TASKS_DETAILED_REPORT_TASK
    }

    @Unroll
    def "renders tasks with dependencies created by model rules running #tasks"() {
        when:
        settingsFile << "rootProject.name = 'test-project'"
        buildScript """
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
$otherGroupHeader
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

    def "can run multiple task reports in parallel"() {
        given:
        buildFile << multiProjectBuild()
        def projects = (1..100).collect {"project$it"}
        settingsFile << "include '${projects.join("', '")}'"

        expect:
        succeeds(":tasks", *projects.collect { "$it:tasks" }, "--parallel")
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

    static String getHelloWorldGroupHeader() {
        getGroupHeader(GROUP)
    }

    static String getOtherGroupHeader() {
        getGroupHeader('Other')
    }

    private static String getGroupHeader(String group) {
        String header = "$group tasks"
        """$header
${'-' * header.length()}"""
    }

    static String multiProjectBuild() {
        """
            allprojects {
                task a {
                    group = '$GROUP'
                }
            }

            subprojects {
                task b
            }

            task c
        """
    }
}
