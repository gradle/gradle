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

package org.gradle.composite.internal

import com.google.common.collect.ImmutableList
import org.gradle.execution.plan.PlanExecutor
import org.gradle.execution.plan.TaskInAnotherBuild
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildWorkGraph
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.util.Path

import java.util.concurrent.TimeUnit

class DefaultBuildControllersTest extends AbstractIncludedBuildTaskGraphTest {

    def controllers = new DefaultBuildControllers(Stub(ManagedExecutor), Stub(WorkerLeaseService), Stub(PlanExecutor), 1, TimeUnit.SECONDS)

    def rootWorkGraph = Mock(BuildWorkGraph)
    def childWorkGraph = Mock(BuildWorkGraph)
    def rootBuild = buildWithWorkGraph(Path.ROOT, rootWorkGraph)
    def childBuild = buildWithWorkGraph(Path.path(":child"), childWorkGraph)

    def "queues the target of a discovered cross-build reference in the build that owns it"() {
        given:
        def targetNode = Stub(TaskNode)
        controllers.getBuildController(rootBuild)

        when:
        controllers.populateWorkGraphs()

        then:
        // The reference is discovered by the root build, and its target is scheduled in the child build
        1 * rootWorkGraph.takeCrossBuildReferences() >> ImmutableList.of(reference(childBuild, targetNode))
        1 * childWorkGraph.schedule({ it as List == [targetNode] }) >> true

        and:
        _ * rootWorkGraph.takeCrossBuildReferences() >> ImmutableList.of()
        _ * childWorkGraph.takeCrossBuildReferences() >> ImmutableList.of()
        1 * rootWorkGraph.finalizeGraph()
        1 * childWorkGraph.finalizeGraph()
    }

    def "queues the target of a reference discovered by a build that was itself only just scheduled"() {
        given:
        def grandChildWorkGraph = Mock(BuildWorkGraph)
        def grandChildBuild = buildWithWorkGraph(Path.path(":child:grandChild"), grandChildWorkGraph)
        def grandChildTargetNode = Stub(TaskNode)
        controllers.getBuildController(rootBuild)

        when:
        controllers.populateWorkGraphs()

        then:
        1 * rootWorkGraph.takeCrossBuildReferences() >> ImmutableList.of(reference(childBuild, Stub(TaskNode)))
        1 * childWorkGraph.schedule(_) >> true
        // Discovered while scheduling the child build, so the fixed point has to keep going
        1 * childWorkGraph.takeCrossBuildReferences() >> ImmutableList.of(reference(grandChildBuild, grandChildTargetNode))
        1 * grandChildWorkGraph.schedule({ it as List == [grandChildTargetNode] }) >> true

        and:
        _ * rootWorkGraph.takeCrossBuildReferences() >> ImmutableList.of()
        _ * childWorkGraph.takeCrossBuildReferences() >> ImmutableList.of()
        _ * grandChildWorkGraph.takeCrossBuildReferences() >> ImmutableList.of()
        1 * rootWorkGraph.finalizeGraph()
        1 * childWorkGraph.finalizeGraph()
        1 * grandChildWorkGraph.finalizeGraph()
    }

    def "does not schedule the target of a reference that has already been scheduled"() {
        given:
        def targetNode = Stub(TaskNode)
        controllers.getBuildController(rootBuild)

        when:
        controllers.populateWorkGraphs()

        then:
        // The same target is reported twice, once by the root build and once by the child build itself
        1 * rootWorkGraph.takeCrossBuildReferences() >> ImmutableList.of(reference(childBuild, targetNode))
        1 * childWorkGraph.takeCrossBuildReferences() >> ImmutableList.of(reference(childBuild, targetNode))
        1 * childWorkGraph.schedule({ it as List == [targetNode] }) >> true

        and:
        _ * rootWorkGraph.takeCrossBuildReferences() >> ImmutableList.of()
        _ * childWorkGraph.takeCrossBuildReferences() >> ImmutableList.of()
        1 * rootWorkGraph.finalizeGraph()
        1 * childWorkGraph.finalizeGraph()
    }

    private static TaskInAnotherBuild reference(BuildState targetBuild, TaskNode targetNode) {
        return TaskInAnotherBuild.of(targetNode, targetBuild)
    }
}
