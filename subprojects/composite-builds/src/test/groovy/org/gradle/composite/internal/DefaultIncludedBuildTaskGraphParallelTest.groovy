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

package org.gradle.composite.internal

import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.execution.plan.BuildWorkPlan
import org.gradle.execution.plan.DefaultExecutionPlan
import org.gradle.execution.plan.DefaultPlanExecutor
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeValidator
import org.gradle.execution.plan.PlanExecutor
import org.gradle.execution.plan.SelfExecutingNode
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildWorkGraphController
import org.gradle.internal.build.DefaultBuildWorkGraphController
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseService
import org.jetbrains.annotations.NotNull
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Timeout(60)
class DefaultIncludedBuildTaskGraphParallelTest extends AbstractIncludedBuildTaskGraphTest {
    // Use a reasonable number of workers to catch issues with contention
    @Shared
    def manyWorkers = 10
    def cancellationToken = new DefaultBuildCancellationToken()

    def "does nothing when nothing scheduled"() {
        when:
        def result = scheduleAndRun(new Services(manyWorkers)) {
            // Nothing
        }

        then:
        result.failures.empty
    }

    def "runs scheduled work"() {
        def services = new Services(workers)
        def build = build(DefaultBuildIdentifier.ROOT, buildWorkGraphController(services))

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build) { graphBuilder ->
                def node = new TestNode()
                node.require()
                node.dependenciesProcessed()
                graphBuilder.addNodes([node])
            }
        }

        then:
        result.failures.empty

        where:
        workers << [1, manyWorkers]
    }

    def "fails when no further nodes can be selected"() {
        def services = new Services(manyWorkers)
        def build = build(DefaultBuildIdentifier.ROOT, buildWorkGraphController(services))

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build) { graphBuilder ->
                def node = new StuckNode()
                node.require()
                node.dependenciesProcessed()
                graphBuilder.addNodes([node])
            }
        }

        then:
        result.failures.size() == 1
        result.failures.first() instanceof IllegalStateException
        result.failures.first().message == "Unable to make progress running work. There are items queued for execution but none of them can be started"
    }

    ExecutionResult<Void> scheduleAndRun(Services services, Action<BuildTreeWorkGraph.Builder> action) {
        def result = null
        services.workerLeaseService.runAsWorkerThread {
            services.buildTaskGraph.withNewWorkGraph { graph ->
                graph.scheduleWork { builder ->
                    action(builder)
                }
                result = graph.runWork()
            }
            // Ensure that everything can be cleanly stopped
            services.stop()
        }
        return result
    }

    BuildWorkGraphController buildWorkGraphController(Services services) {
        def controller = Mock(BuildLifecycleController)
        def builder = Mock(BuildLifecycleController.WorkGraphBuilder)
        def nodeFactory = new TaskNodeFactory(Stub(GradleInternal), Stub(DocumentationRegistry), Stub(BuildTreeWorkGraphController), Stub(NodeValidator))
        def hierarchies = new ExecutionNodeAccessHierarchies(CaseSensitivity.CASE_SENSITIVE, TestFiles.fileSystem())
        def plan = new DefaultExecutionPlan("work", nodeFactory, Stub(TaskDependencyResolver), hierarchies.outputHierarchy, hierarchies.destroyableHierarchy, services.coordinationService)

        _ * controller.newWorkGraph() >> Stub(BuildWorkPlan) {
            _ * stop() >> { plan.close() }
        }
        _ * controller.populateWorkGraph(_, _) >> { BuildWorkPlan p, Consumer action ->
            action.accept(builder)
        }
        _ * builder.addNodes(_) >> { args ->
            plan.addNodes(args[0])
        }
        _ * controller.executeTasks(_) >> {
            plan.determineExecutionPlan()
            return services.planExecutor.process(plan) { node -> }
        }

        return new DefaultBuildWorkGraphController(
            nodeFactory,
            controller
        )
    }

    private class Services {
        final PlanExecutor planExecutor
        final DefaultIncludedBuildTaskGraph buildTaskGraph
        final WorkerLeaseService workerLeaseService
        final ExecutorFactory execFactory
        final coordinationService = new DefaultResourceLockCoordinationService()

        Services(int workers) {
            def configuration = new DefaultParallelismConfiguration(true, workers)
            workerLeaseService = new DefaultWorkerLeaseService(coordinationService, configuration)
            execFactory = new DefaultExecutorFactory()
            planExecutor = new DefaultPlanExecutor(configuration, execFactory, workerLeaseService, cancellationToken, coordinationService)
            buildTaskGraph = new DefaultIncludedBuildTaskGraph(
                execFactory,
                new TestBuildOperationExecutor(),
                buildStateRegistry,
                workerLeaseService,
                planExecutor,
                100,
                TimeUnit.MILLISECONDS
            )
        }

        void stop() {
            CompositeStoppable.stoppable(buildTaskGraph, planExecutor, workerLeaseService, coordinationService, execFactory).stop()
        }
    }

    private static class TestNode extends Node implements SelfExecutingNode {
        @Override
        Throwable getNodeFailure() {
            return null
        }

        @Override
        void rethrowNodeFailure() {
        }

        @Override
        void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
        }

        @Override
        ResourceLock getProjectToLock() {
            return null
        }

        @Override
        ProjectInternal getOwningProject() {
            return null
        }

        @Override
        List<? extends ResourceLock> getResourcesToLock() {
            return []
        }

        @Override
        String toString() {
            return "test node"
        }

        @Override
        int compareTo(@NotNull Node o) {
            return -1
        }

        @Override
        void execute(NodeExecutionContext context) {
        }
    }

    private static class StuckNode extends TestNode {
        @Override
        protected boolean doCheckDependenciesComplete() {
            return false
        }
    }
}
