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

package org.gradle.execution.plan

import org.gradle.api.DefaultTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.internal.tasks.DeferredCrossProjectDependency
import org.gradle.api.internal.tasks.TaskDependencyContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.Factory
import org.gradle.internal.file.Stat
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.work.WorkerLeaseService

import org.gradle.util.TestUtil

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class ParallelNodeRelationshipsResolverTest extends AbstractExecutionPlanSpec {

    def accessHierarchies = new ExecutionNodeAccessHierarchies(CASE_SENSITIVE, Stub(Stat))
    def taskNodeFactory = new TaskNodeFactory(project.gradle, Stub(BuildTreeWorkGraphController), nodeValidator, new TestBuildOperationRunner(), accessHierarchies, TestUtil.problemsService())
    def dependencyResolver = new TaskDependencyResolver([new TaskNodeDependencyResolver(taskNodeFactory)])
    def workerLeaseService = Stub(WorkerLeaseService) {
        runAsIsolatedTask(_ as Factory) >> { Factory f -> f.create() }
        runAsIsolatedTask(_ as Runnable) >> { Runnable r -> r.run() }
    }
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def projectStateRegistry = Stub(ProjectStateRegistry)

    def resolver = new ParallelNodeRelationshipsResolver(dependencyResolver, workerLeaseService, buildOperationExecutor, projectStateRegistry, taskNodeFactory)

    TaskInternal task(Map<String, ?> options = [:], String name) {
        def proj = (options.project ?: this.project) as ProjectInternal
        def task = createTask(name, proj, options.type ?: TaskInternal)
        _ * task.taskDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.lifecycleDependencies >> taskDependencyResolvingTo(task, options.dependsOn ?: [])
        _ * task.finalizedBy >> taskDependencyResolvingTo(task, options.finalizedBy ?: [])
        _ * task.shouldRunAfter >> taskDependencyResolvingTo(task, options.shouldRunAfter ?: [])
        _ * task.mustRunAfter >> taskDependencyResolvingTo(task, options.mustRunAfter ?: [])
        _ * task.sharedResources >> (options.resources ?: [])
        _ * task.taskIdentity >> TestTaskIdentities.create(name, DefaultTask, proj)
        TaskStateInternal state = Mock()
        _ * task.state >> state
        return task
    }

    LocalTaskNode nodeFor(TaskInternal task) {
        return taskNodeFactory.getOrCreateNode(task) as LocalTaskNode
    }

    LinkedList<Node> queueOf(LocalTaskNode... nodes) {
        return new LinkedList<Node>(Arrays.asList(nodes))
    }

    def "resolve returns empty map for empty initial queue"() {
        expect:
        resolver.resolve(new LinkedList<Node>()).isEmpty()
    }

    def "resolve ignores non-LocalTaskNode entries in initial queue"() {
        given:
        def nonLocalNode = Mock(Node)

        when:
        def result = resolver.resolve(new LinkedList<Node>([nonLocalNode]))

        then:
        result.isEmpty()
    }

    def "resolves single task with no dependencies"() {
        given:
        def taskA = task("a")
        def nodeA = nodeFor(taskA)

        when:
        def result = resolver.resolve(queueOf(nodeA))

        then:
        result.size() == 1
        result[nodeA].dependencies.isEmpty()
        result[nodeA].lifecycleDependencies.isEmpty()
        result[nodeA].finalizedBy.isEmpty()
        result[nodeA].mustRunAfter.isEmpty()
        result[nodeA].shouldRunAfter.isEmpty()
    }

    def "resolves task chain via BFS waves"() {
        given:
        def taskC = task("c")
        def taskB = task("b", dependsOn: [taskC])
        def taskA = task("a", dependsOn: [taskB])
        def nodeA = nodeFor(taskA)
        def nodeB = nodeFor(taskB)
        def nodeC = nodeFor(taskC)

        when:
        def result = resolver.resolve(queueOf(nodeA))

        then:
        result.size() == 3
        result.containsKey(nodeA)
        result.containsKey(nodeB)
        result.containsKey(nodeC)
        result[nodeA].dependencies.contains(nodeB)
        result[nodeB].dependencies.contains(nodeC)
        result[nodeC].dependencies.isEmpty()
    }

    def "resolves task with finalizedBy relationship"() {
        given:
        def taskB = task("b")
        def taskA = task("a", finalizedBy: [taskB])
        def nodeA = nodeFor(taskA)
        def nodeB = nodeFor(taskB)

        when:
        def result = resolver.resolve(queueOf(nodeA))

        then:
        result.size() == 2
        result.containsKey(nodeA)
        result.containsKey(nodeB)
        result[nodeA].finalizedBy.contains(nodeB)
        result[nodeA].dependencies.isEmpty()
    }

    def "resolves task with mustRunAfter and shouldRunAfter ordering"() {
        given:
        def taskB = task("b")
        def taskC = task("c")
        def taskA = task("a", mustRunAfter: [taskB], shouldRunAfter: [taskC])
        def nodeA = nodeFor(taskA)
        def nodeB = nodeFor(taskB)
        def nodeC = nodeFor(taskC)

        when:
        def result = resolver.resolve(queueOf(nodeA))

        then:
        // mustRunAfter/shouldRunAfter are resolved but do not trigger BFS discovery
        result.size() == 1
        result[nodeA].mustRunAfter.contains(nodeB)
        result[nodeA].shouldRunAfter.contains(nodeC)
    }

    def "resolves tasks from multiple projects in parallel wave"() {
        given:
        def projectA = project(project, "a")
        _ * projectA.projectPath >> projectA.projectIdentity.projectPath

        def projectB = project(project, "b")
        _ * projectB.projectPath >> projectB.projectIdentity.projectPath

        def taskA = task("taskA", project: projectA)
        def taskB = task("taskB", project: projectB)
        def nodeA = nodeFor(taskA)
        def nodeB = nodeFor(taskB)

        when:
        def result = resolver.resolve(queueOf(nodeA, nodeB))

        then:
        result.size() == 2
        result.containsKey(nodeA)
        result.containsKey(nodeB)
        result[nodeA].dependencies.isEmpty()
        result[nodeB].dependencies.isEmpty()
    }

    def "skips nodes where dependenciesProcessed is true"() {
        given:
        def taskA = task("a")
        def nodeA = nodeFor(taskA)
        nodeA.dependenciesProcessed()

        when:
        def result = resolver.resolve(queueOf(nodeA))

        then:
        result.isEmpty()
    }

    def "resolves deferred cross-project dependencies through full phase 1-2-3 flow"() {
        given:
        def projectA = project(project, "a")
        _ * projectA.projectPath >> projectA.projectIdentity.projectPath

        def projectB = project(project, "b")
        _ * projectB.projectPath >> projectB.projectIdentity.projectPath

        // Task B in project B (no dependencies)
        def taskB = task("taskB", project: projectB)
        def nodeB = nodeFor(taskB)

        // Stub project state registry so deferred resolution can find project B
        def projectBIdentityPath = projectB.identityPath
        def projectBState = projectB.owner
        _ * projectStateRegistry.stateFor(projectBIdentityPath) >> projectBState
        _ * projectBState.ensureTasksDiscovered() >> {}
        // Make project B's tasks container return taskB when looked up by name
        _ * projectB.tasks.findByName("taskB") >> taskB

        // Task A in project A, with a deferred cross-project dependency on project B's taskB
        def taskA = createTask("taskA", projectA)
        // Use separate mock instances for taskDependencies and lifecycleDependencies
        // to avoid CachingDirectedGraphWalker returning cached results
        def deferredTaskDep = Mock(TaskDependencyContainerInternal) {
            visitDependencies(_) >> { TaskDependencyResolveContext context ->
                context.add(new DeferredCrossProjectDependency.ByProjectTask(projectBIdentityPath, "taskB"))
            }
        }
        def deferredLifecycleDep = Mock(TaskDependencyContainerInternal) {
            visitDependencies(_) >> { TaskDependencyResolveContext context ->
                context.add(new DeferredCrossProjectDependency.ByProjectTask(projectBIdentityPath, "taskB"))
            }
        }
        _ * taskA.taskDependencies >> deferredTaskDep
        _ * taskA.lifecycleDependencies >> deferredLifecycleDep
        _ * taskA.finalizedBy >> taskDependencyResolvingTo(taskA, [])
        _ * taskA.shouldRunAfter >> taskDependencyResolvingTo(taskA, [])
        _ * taskA.mustRunAfter >> taskDependencyResolvingTo(taskA, [])
        _ * taskA.sharedResources >> []
        _ * taskA.taskIdentity >> TestTaskIdentities.create("taskA", DefaultTask, projectA)
        TaskStateInternal stateA = Mock()
        _ * taskA.state >> stateA

        // A dummy task in project B so the initial wave has multiple projects (triggers multi-project path)
        def taskDummy = task("dummy", project: projectB)
        def nodeA = nodeFor(taskA)
        def nodeDummy = nodeFor(taskDummy)

        when:
        def result = resolver.resolve(queueOf(nodeA, nodeDummy))

        then:
        // Phase 1: nodeA resolved with deferred deps, nodeDummy resolved normally
        // Phase 2: deferred ByProjectTask resolved → discovers nodeB
        // Phase 3: deferred results merged into nodeA's relationships
        result.containsKey(nodeA)
        result.containsKey(nodeDummy)
        result[nodeA].dependencies.contains(nodeB)
        result[nodeA].lifecycleDependencies.contains(nodeB)
    }

    def "resolves shared dependency only once across BFS waves"() {
        given:
        def taskC = task("c")
        def taskA = task("a", dependsOn: [taskC])
        def taskB = task("b", dependsOn: [taskC])
        def nodeA = nodeFor(taskA)
        def nodeB = nodeFor(taskB)
        def nodeC = nodeFor(taskC)

        when:
        def result = resolver.resolve(queueOf(nodeA, nodeB))

        then:
        result.size() == 3
        result[nodeA].dependencies.contains(nodeC)
        result[nodeB].dependencies.contains(nodeC)
        result.containsKey(nodeC)
    }

    def "BFS discovers dependencies of finalized tasks"() {
        given:
        def taskC = task("c")
        def taskB = task("b", dependsOn: [taskC])
        def taskA = task("a", finalizedBy: [taskB])
        def nodeA = nodeFor(taskA)
        def nodeB = nodeFor(taskB)
        def nodeC = nodeFor(taskC)

        when:
        def result = resolver.resolve(queueOf(nodeA))

        then:
        result.size() == 3
        result[nodeA].finalizedBy.contains(nodeB)
        result[nodeB].dependencies.contains(nodeC)
        result[nodeC].dependencies.isEmpty()
    }
}
