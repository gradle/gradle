/*
 * Copyright 2025 the original author or authors.
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

class FullTaskListingTaskReportTaskIntegrationTest extends AbstractIntegrationSpec {
    private final static String[] TASKS_REPORT_TASK = ['tasks'] as String[]
    private final static String[] TASKS_DETAILED_REPORT_TASK = TASKS_REPORT_TASK + ['--all'] as String[]

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
updateDaemonJvm - Generates or updates the Gradle Daemon JVM criteria.
wrapper - Generates Gradle wrapper files.

Help tasks
----------
artifactTransforms - Displays the Artifact Transforms that can be executed in root project '$projectName'.
buildEnvironment - Displays all buildscript dependencies declared in root project '$projectName'.
dependencies - Displays all dependencies declared in root project '$projectName'.
dependencyInsight - Displays the insight into a specific dependency in root project '$projectName'.
help - Displays a help message.
javaToolchains - Displays the detected java toolchains.
outgoingVariants - Displays the outgoing variants of root project '$projectName'.
projects - Displays the sub-projects of root project '$projectName'.
properties - Displays the properties of root project '$projectName'.
resolvableConfigurations - Displays the configurations that can be resolved in root project '$projectName'.
tasks - Displays the tasks runnable from root project '$projectName'.""")

        where:
        tasks << [TASKS_REPORT_TASK, TASKS_DETAILED_REPORT_TASK]
    }

    def "shows task types when run with --types"() {
        given:
        String projectName = 'test'
        settingsFile << "rootProject.name = '$projectName'"

        when:
        succeeds "tasks", "--types"

        then:
        output.contains("""
Build Setup tasks
-----------------
init (org.gradle.buildinit.tasks.InitBuild) - Initializes a new Gradle build.
updateDaemonJvm (org.gradle.buildconfiguration.tasks.UpdateDaemonJvm) - Generates or updates the Gradle Daemon JVM criteria.
wrapper (org.gradle.api.tasks.wrapper.Wrapper) - Generates Gradle wrapper files.

Help tasks
----------
artifactTransforms (org.gradle.api.tasks.diagnostics.ArtifactTransformsReportTask) - Displays the Artifact Transforms that can be executed in root project '$projectName'.
buildEnvironment (org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask) - Displays all buildscript dependencies declared in root project '$projectName'.
dependencies (org.gradle.api.tasks.diagnostics.DependencyReportTask) - Displays all dependencies declared in root project '$projectName'.
dependencyInsight (org.gradle.api.tasks.diagnostics.DependencyInsightReportTask) - Displays the insight into a specific dependency in root project '$projectName'.
help (org.gradle.configuration.Help) - Displays a help message.
javaToolchains (org.gradle.jvm.toolchain.internal.task.ShowToolchainsTask) - Displays the detected java toolchains.
outgoingVariants (org.gradle.api.tasks.diagnostics.OutgoingVariantsReportTask) - Displays the outgoing variants of root project '$projectName'.
projects (org.gradle.api.tasks.diagnostics.ProjectReportTask) - Displays the sub-projects of root project '$projectName'.
properties (org.gradle.api.tasks.diagnostics.PropertyReportTask) - Displays the properties of root project '$projectName'.
resolvableConfigurations (org.gradle.api.tasks.diagnostics.ResolvableConfigurationsReportTask) - Displays the configurations that can be resolved in root project '$projectName'.
tasks (org.gradle.api.tasks.diagnostics.TaskReportTask) - Displays the tasks runnable from root project '$projectName'.""")
    }

    def "renders only tasks in given group running [tasks, --group=build setup]"() {
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
updateDaemonJvm - Generates or updates the Gradle Daemon JVM criteria.
wrapper - Generates Gradle wrapper files.

To see all tasks and more detail, run gradle tasks --all

To see more detail about a task, run gradle help --task <task>
""")
        !output.contains("custom")
    }

    def "renders only tasks in given group running [tasks, --groups=build setup]"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            task mytask {
                group = "custom"
            }
        """
        when:
        succeeds "tasks", "--groups=build setup"

        then:
        output.contains("""
------------------------------------------------------------
Tasks runnable from root project 'test'
------------------------------------------------------------

Build Setup tasks
-----------------
init - Initializes a new Gradle build.
updateDaemonJvm - Generates or updates the Gradle Daemon JVM criteria.
wrapper - Generates Gradle wrapper files.

To see all tasks and more detail, run gradle tasks --all

To see more detail about a task, run gradle help --task <task>
""")
        !output.contains("custom")
    }
}
