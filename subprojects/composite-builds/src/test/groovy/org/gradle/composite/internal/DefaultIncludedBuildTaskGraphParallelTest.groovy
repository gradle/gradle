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
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.execution.plan.BuildWorkPlan
import org.gradle.execution.plan.DefaultExecutionPlan
import org.gradle.execution.plan.DefaultPlanExecutor
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeValidator
import org.gradle.execution.plan.PlanExecutor
import org.gradle.execution.plan.SelfExecutingNode
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildToolingModelController
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
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.util.internal.RedirectStdOutAndErr
import org.jetbrains.annotations.Nullable
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function

@Timeout(60)
class DefaultIncludedBuildTaskGraphParallelTest extends AbstractIncludedBuildTaskGraphTest {
    static final int MONITOR_POLL_TIME = 100
    static final int SLOW_NODE_EXECUTION_TIME = MONITOR_POLL_TIME * 4

    @Rule
    RedirectStdOutAndErr stdout = new RedirectStdOutAndErr()

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
        def build = services.build(DefaultBuildIdentifier.ROOT)

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build) { graphBuilder ->
                def node = new TestNode()
                graphBuilder.addNodes([node])
            }
        }

        then:
        result.failures.empty

        where:
        workers << [1, manyWorkers]
    }

    def "runs scheduled work across multiple builds"() {
        def services = new Services(workers)
        def childBuild = services.build(new DefaultBuildIdentifier("child"))
        def build = services.build(DefaultBuildIdentifier.ROOT)

        when:
        def result = scheduleAndRun(services) { builder ->
            def childNode = new TestNode("child build node")
            builder.withWorkGraph(build) { graphBuilder ->
                def node = new DelegateNode("main build node", [childNode])
                graphBuilder.addNodes([node])
            }
            builder.withWorkGraph(childBuild) { graphBuilder ->
                graphBuilder.addNodes([childNode])
            }
        }

        then:
        result.failures.empty

        where:
        workers << [1, manyWorkers]
    }

    def "fails when no further nodes can be selected"() {
        def services = new Services(manyWorkers)
        def build = services.build(DefaultBuildIdentifier.ROOT)

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build) { graphBuilder ->
                def node = new DependenciesStuckNode()
                graphBuilder.addNodes([node])
            }
        }

        then:
        result.failures.size() == 1
        result.failures.first() instanceof IllegalStateException
        result.failures.first().message == "Unable to make progress running work. There are items queued for execution but none of them can be started"

        stdout.stdOut.contains("Unable to make progress running work. The following items are queued for execution but none of them can be started:")
        stdout.stdOut.contains("- test node (state=SHOULD_RUN")
    }

    def "fails when no further nodes can be selected across multiple builds"() {
        def services = new Services(manyWorkers)
        def childBuild = services.build(new DefaultBuildIdentifier("child"))
        def build = services.build(DefaultBuildIdentifier.ROOT)

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build) { graphBuilder ->
                def node = new DependenciesStuckNode("main build node")
                graphBuilder.addNodes([node])
            }
            builder.withWorkGraph(childBuild) { graphBuilder ->
                def node = new DependenciesStuckNode("child build node")
                graphBuilder.addNodes([node])
            }
        }

        then:
        result.failures.size() == 1
        result.failures.first() instanceof IllegalStateException
        result.failures.first().message == "Unable to make progress running work. There are items queued for execution but none of them can be started"

        stdout.stdOut.contains("Unable to make progress running work. The following items are queued for execution but none of them can be started:")
        stdout.stdOut.contains("- Queued nodes for build ':':")
        stdout.stdOut.contains("- main build node (state=SHOULD_RUN")
        stdout.stdOut.contains("- Queued nodes for build 'child':")
        stdout.stdOut.contains("- child build node (state=SHOULD_RUN")
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

    private BuildWorkGraphController buildWorkGraphController(String displayName, Services services) {
        def builder = Mock(BuildLifecycleController.WorkGraphBuilder)
        def nodeFactory = new TaskNodeFactory(Stub(GradleInternal), Stub(DocumentationRegistry), Stub(BuildTreeWorkGraphController), Stub(NodeValidator))
        def hierarchies = new ExecutionNodeAccessHierarchies(CaseSensitivity.CASE_SENSITIVE, TestFiles.fileSystem())
        def plan = new DefaultExecutionPlan(displayName, nodeFactory, Stub(TaskDependencyResolver), hierarchies.outputHierarchy, hierarchies.destroyableHierarchy, services.coordinationService)
        def workPlan = Stub(BuildWorkPlan) {
            _ * stop() >> { plan.close() }
        }

        def controller = new TestBuildLifecycleController(plan, workPlan, builder, services)

        _ * builder.addNodes(_) >> { args ->
            plan.addNodes(args[0])
        }

        return new DefaultBuildWorkGraphController(
            nodeFactory,
            controller
        )
    }

    private class TestBuildLifecycleController implements BuildLifecycleController {
        final ExecutionPlan plan
        final BuildWorkPlan workPlan
        final WorkGraphBuilder builder
        final Services services

        TestBuildLifecycleController(ExecutionPlan plan, BuildWorkPlan workPlan, WorkGraphBuilder builder, Services services) {
            this.workPlan = workPlan
            this.plan = plan
            this.builder = builder
            this.services = services
        }

        @Override
        BuildWorkPlan newWorkGraph() {
            return workPlan
        }

        @Override
        void populateWorkGraph(BuildWorkPlan plan, Consumer<? super WorkGraphBuilder> action) {
            action.accept(builder)
        }

        @Override
        void finalizeWorkGraph(BuildWorkPlan workPlan) {
            plan.determineExecutionPlan()
            plan.finalizePlan()
        }

        @Override
        ExecutionResult<Void> executeTasks(BuildWorkPlan buildPlan) {
            return services.planExecutor.process(plan.asWorkSource()) { node -> }
        }

        @Override
        GradleInternal getGradle() {
            throw new UnsupportedOperationException()
        }

        @Override
        void loadSettings() {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> T withSettings(Function<? super SettingsInternal, T> action) {
            throw new UnsupportedOperationException()
        }

        @Override
        void configureProjects() {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> T withProjectsConfigured(Function<? super GradleInternal, T> action) {
            throw new UnsupportedOperationException()
        }

        @Override
        GradleInternal getConfiguredBuild() {
            throw new UnsupportedOperationException()
        }

        @Override
        void prepareToScheduleTasks() {
        }

        @Override
        <T> T withToolingModels(Function<? super BuildToolingModelController, T> action) {
            throw new UnsupportedOperationException()
        }

        @Override
        ExecutionResult<Void> finishBuild(@Nullable Throwable failure) {
            throw new UnsupportedOperationException()
        }

        @Override
        void addListener(Object listener) {
            throw new UnsupportedOperationException()
        }
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
                MONITOR_POLL_TIME,
                TimeUnit.MILLISECONDS
            )
        }

        BuildState build(BuildIdentifier identifier) {
            return build(identifier, buildWorkGraphController(identifier.toString(), this))
        }

        void stop() {
            CompositeStoppable.stoppable(buildTaskGraph, planExecutor, workerLeaseService, coordinationService, execFactory).stop()
        }
    }

    private static class TestNode extends Node implements SelfExecutingNode {
        private final String displayName
        private final List<Runnable> observers = []

        TestNode(String displayName = "test node") {
            this.displayName = displayName
            require()
        }

        void addObserver(Runnable runnable) {
            observers.add(runnable)
        }

        @Override
        Throwable getNodeFailure() {
            return null
        }

        @Override
        void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        }

        @Override
        String toString() {
            return displayName
        }

        @Override
        void execute(NodeExecutionContext context) {
            sleep(SLOW_NODE_EXECUTION_TIME)
        }

        @Override
        void finishExecution(Consumer<Node> completionAction) {
            super.finishExecution(completionAction)
            for (final def action in observers) {
                action.run()
            }
        }
    }

    private static class DependenciesStuckNode extends TestNode {
        DependenciesStuckNode(String displayName = "test node") {
            super(displayName)
        }

        @Override
        protected DependenciesState doCheckDependenciesComplete() {
            return DependenciesState.NOT_COMPLETE
        }
    }

    private static class DelegateNode extends TestNode {
        private final List<TestNode> dependencies
        private Action<Node> monitor

        DelegateNode(String displayName, List<TestNode> dependencies) {
            super(displayName)
            this.dependencies = dependencies
            for (TestNode dep in dependencies) {
                dep.addObserver {
                    monitor.execute(this)
                }
            }
        }

        @Override
        void prepareForExecution(Action<Node> monitor) {
            this.monitor = monitor
        }

        @Override
        protected DependenciesState doCheckDependenciesComplete() {
            for (TestNode dep in dependencies) {
                if (!dep.isComplete()) {
                    return DependenciesState.NOT_COMPLETE
                }
            }
            return DependenciesState.COMPLETE_AND_SUCCESSFUL
        }
    }
}
