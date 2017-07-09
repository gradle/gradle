/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.DefaultAntBuilder
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue

import static org.gradle.util.TestUtil.createRootProject

class AntTaskExecutionPlanTest extends AbstractProjectBuilderSpec {

    DefaultTaskExecutionPlan executionPlan
    ProjectInternal root
    def cancellationToken = Mock(BuildCancellationToken)
    def coordinationService = Mock(ResourceLockCoordinationService)
    def leaseService = Mock(WorkerLeaseService)
    def internal = Mock(GradleInternal)
    private final AntLoggingAdapter loggingAdapter = Mock(AntLoggingAdapter)
    def ant

    def setup() {
        root = createRootProject(temporaryFolder.testDirectory)
        executionPlan = new DefaultTaskExecutionPlan(cancellationToken, coordinationService, leaseService, internal)
        ant = new DefaultAntBuilder(project, loggingAdapter)
    }

    private void addToGraphAndPopulate(List tasks) {
        executionPlan.addToTaskGraph(tasks)
        executionPlan.determineExecutionPlan()
    }

    @Issue("https://github.com/gradle/gradle/issues/2293")
    def "determining execution plan for an imported ant project should not fail on empty cycles list"() {
        /*
            The following is a minimal test case for the problem described in the issue 2293.
            Have to provide actual xml contents of the Ant build script, since an integrated
            test wouldn't allow to create the actual runtime data structure that triggers the bug.

            The Ant project contains 5 targets corresponding to gradle tasks. Here's the initial
            dependencies graph defined by the xml contents:

            x--+->d+-------+
               |           |
               +->c+-+--+  |
               |     |  |  |
               +->b<-+  |  |
               |        |  |
               +->a<----+--+

            The Ant project importer adds several additional edges, called "shouldRunAfter", as per
            https://github.com/gradle/gradle/pull/224. These only impose an ordering among tasks that
            are othewise independent.

            a--->c
            |    |
            |    v
            +--->b-->d

            As one can see, these two graphs, when combined, produce a cycle. For example, between "a" and "c".

            Now, DefaultTaskExecutionPlan treats the two kinds of edges the same when building a plan,
            see addSuccessorsInReverseOrder() method. But since a "shouldRunAfter" edge doesn't really
            constitute a dependency, these are rightfully ignored by the graphWalker in onOrderingCycle().
            Thus, a cycle is not detected, and this condition must be checked.
         */

        // integrated test wouldn't allow to reproduce the problem
        File buildFile = new File(project.projectDir, 'build.xml')
        buildFile.withWriter {Writer wr ->
            def contents =
                '''
                <project name="fubar" default="x">
                <target name="x" depends="d, b, c, a"/>
                <target name="a"/>
                <target name="b"/>
                <target name="c" depends="b, a"/>
                <target name="d" depends="a"/>
                </project>
                '''
            wr.write(contents)
        }

        when:
        ant.importBuild(buildFile)
        def taskList = project.getAllTasks(true)[project].asList()
        addToGraphAndPopulate(taskList)

        then:
        notThrown(IndexOutOfBoundsException)
    }

}
