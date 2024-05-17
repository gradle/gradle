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
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDestroyablesInternal
import org.gradle.api.internal.tasks.TaskLocalStateInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.execution.plan.BuildWorkPlan
import org.gradle.execution.plan.DefaultExecutionPlan
import org.gradle.execution.plan.DefaultPlanExecutor
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeValidator
import org.gradle.execution.plan.OrdinalGroupFactory
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
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.buildtree.BuildTreeWorkGraphPreparer
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.file.Stat
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Timeout

import javax.annotation.Nullable
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
    def preparer = Stub(BuildTreeWorkGraphPreparer)

    def "does nothing when nothing scheduled"() {
        when:
        def result = scheduleAndRun(new TreeServices(manyWorkers)) {
            // Nothing
        }

        then:
        result.failures.empty
    }

    def "runs scheduled work"() {
        def services = new TreeServices(workers)
        def build = build(services, DefaultBuildIdentifier.ROOT)
        def node = new TestNode()

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build.state) { graphBuilder ->
                def task = task(build, node)
                graphBuilder.addEntryTasks([task])
            }
        }

        then:
        result.failures.empty
        node.executed

        where:
        workers << [1, manyWorkers]
    }

    def "runs scheduled unrelated work across multiple builds"() {
        def services = new TreeServices(workers)
        def childBuild = build(services, new DefaultBuildIdentifier(Path.path(":child")))
        def build = build(services, DefaultBuildIdentifier.ROOT)
        def childNode = new TestNode("child build node")
        def node = new TestNode("main build node")

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build.state) { graphBuilder ->
                def task = task(build, node)
                graphBuilder.addEntryTasks([task])
            }
            builder.withWorkGraph(childBuild.state) { graphBuilder ->
                def task = task(childBuild, childNode)
                graphBuilder.addEntryTasks([task])
            }
        }

        then:
        result.failures.empty
        childNode.executed
        node.executed

        where:
        workers << [1, manyWorkers]
    }

    def "runs scheduled related work across multiple builds"() {
        def services = new TreeServices(workers)
        def childBuild = build(services, new DefaultBuildIdentifier(Path.path(":child")))
        def build = build(services, DefaultBuildIdentifier.ROOT)
        def childNode = new TestNode("child build node")
        def node = new DelegateNode("main build node", [childNode])

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build.state) { graphBuilder ->
                def task = task(build, node)
                graphBuilder.addEntryTasks([task])
            }
            builder.withWorkGraph(childBuild.state) { graphBuilder ->
                def task = task(childBuild, childNode)
                graphBuilder.addEntryTasks([task])
            }
        }

        then:
        result.failures.empty
        childNode.executed
        node.executed

        where:
        workers << [1, manyWorkers]
    }

    def "fails when no further nodes can be selected"() {
        def services = new TreeServices(manyWorkers)
        def build = build(services, DefaultBuildIdentifier.ROOT)
        def node = new DependenciesStuckNode()

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build.state) { graphBuilder ->
                def task = task(build, node)
                graphBuilder.addEntryTasks([task])
            }
        }

        then:
        node.stuck
        result.failures.size() == 1
        result.failures.first() instanceof IllegalStateException
        result.failures.first().message == "Unable to make progress running work. There are items queued for execution but none of them can be started"

        stdout.stdOut.contains("Unable to make progress running work. The following items are queued for execution but none of them can be started:")
        stdout.stdOut.contains("- Build ':':")
        stdout.stdOut.contains("- test node (state=SHOULD_RUN")
        stdout.stdOut.contains("- :task (state=SHOULD_RUN")
        stdout.stdOut.contains("- Ordinal groups: group 0 entry nodes: [:task (SHOULD_RUN)]")
    }

    def "fails when no further nodes can be selected across multiple builds"() {
        def services = new TreeServices(manyWorkers)
        def childBuild = build(services, new DefaultBuildIdentifier(Path.path(":child")))
        def build = build(services, DefaultBuildIdentifier.ROOT)
        def node = new DependenciesStuckNode("main build node")
        def childNode = new DependenciesStuckNode("child build node")

        when:
        def result = scheduleAndRun(services) { builder ->
            builder.withWorkGraph(build.state) { graphBuilder ->
                def task = task(build, node)
                graphBuilder.addEntryTasks([task])
            }
            builder.withWorkGraph(childBuild.state) { graphBuilder ->
                def task = task(childBuild, childNode)
                graphBuilder.addEntryTasks([task])
            }
        }

        then:
        node.stuck
        childNode.stuck
        result.failures.size() == 1
        result.failures.first() instanceof IllegalStateException
        result.failures.first().message == "Unable to make progress running work. There are items queued for execution but none of them can be started"

        stdout.stdOut.contains("Unable to make progress running work. The following items are queued for execution but none of them can be started:")
        stdout.stdOut.contains("- Build ':':")
        stdout.stdOut.contains("- main build node (state=SHOULD_RUN")
        stdout.stdOut.contains("- :task (state=SHOULD_RUN")
        stdout.stdOut.contains("- Ordinal groups: group 0 entry nodes: [:task (SHOULD_RUN)]")
        stdout.stdOut.contains("- Build ':child':")
        stdout.stdOut.contains("- child build node (state=SHOULD_RUN")
        stdout.stdOut.contains("- :child:task (state=SHOULD_RUN")
        stdout.stdOut.contains("- group 0 entry nodes: [:child:task (SHOULD_RUN)]")
    }

    ExecutionResult<Void> scheduleAndRun(TreeServices services, Action<BuildTreeWorkGraph.Builder> action) {
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

    BuildServices build(TreeServices services, BuildIdentifier identifier) {
        def identityPath = Stub(Path)
        def gradle = Stub(GradleInternal) {
            getIdentityPath() >> identityPath
        }
        return new BuildServices(services, identifier, gradle)
    }

    TaskInternal task(BuildServices services, Node dependsOn) {
        def projectState = Stub(ProjectState)
        def project = Stub(ProjectInternal)
        def task = Stub(TaskInternal)
        def dependencies = Stub(TaskDependency)
        _ * dependencies.getDependencies(_) >> [dependsOn].toSet()
        _ * task.taskDependencies >> dependencies
        _ * task.project >> project
        _ * task.identityPath >> Path.path(services.identifier.buildPath).child("task")
        _ * task.taskIdentity >> TestTaskIdentities.create("task", DefaultTask, project)
        _ * task.destroyables >> Stub(TaskDestroyablesInternal)
        _ * task.localState >> Stub(TaskLocalStateInternal)
        _ * project.gradle >> services.gradle
        _ * project.owner >> projectState
        def projectServices = new DefaultServiceRegistry(TestUtil.services())
        projectServices.add(Stub(PropertyWalker))
        projectServices.add(Stub(FileCollectionFactory))
        _ * project.services >> projectServices
        _ * project.pluginManager >> Stub(PluginManagerInternal)
        def lock = Stub(ResourceLock)
        _ * projectState.taskExecutionLock >> lock
        _ * lock.tryLock() >> true
        return task
    }

    private BuildWorkGraphController buildWorkGraphController(String displayName, BuildServices services) {
        def builder = Mock(BuildLifecycleController.WorkGraphBuilder)
        def nodeFactory = new TaskNodeFactory(services.gradle, Stub(BuildTreeWorkGraphController), Stub(NodeValidator), new TestBuildOperationExecutor(), new ExecutionNodeAccessHierarchies(CaseSensitivity.CASE_INSENSITIVE, Stub(Stat)))
        def hierarchies = new ExecutionNodeAccessHierarchies(CaseSensitivity.CASE_SENSITIVE, TestFiles.fileSystem())
        def dependencyResolver = Stub(TaskDependencyResolver)
        _ * dependencyResolver.resolveDependenciesFor(_, _) >> { TaskInternal task, Object dependencies ->
            if (dependencies instanceof TaskDependency) {
                dependencies.getDependencies(task)
            } else {
                []
            }
        }
        def plan = new DefaultExecutionPlan(displayName, nodeFactory, new OrdinalGroupFactory(), dependencyResolver, hierarchies.outputHierarchy, hierarchies.destroyableHierarchy, services.services.coordinationService)
        def workPlan = Stub(BuildWorkPlan) {
            _ * stop() >> { plan.close() }
        }

        def controller = new TestBuildLifecycleController(plan, workPlan, builder, services.services)

        _ * builder.addEntryTasks(_) >> { args ->
            plan.addEntryTasks(args[0])
        }

        return new DefaultBuildWorkGraphController(
            nodeFactory,
            controller,
            Stub(BuildState),
            services.services.workerLeaseService
        )
    }

    private class TestBuildLifecycleController implements BuildLifecycleController {
        final ExecutionPlan plan
        FinalizedExecutionPlan finalizedPlan
        final BuildWorkPlan workPlan
        final WorkGraphBuilder builder
        final TreeServices services

        TestBuildLifecycleController(ExecutionPlan plan, BuildWorkPlan workPlan, WorkGraphBuilder builder, TreeServices services) {
            this.workPlan = workPlan
            this.plan = plan
            this.builder = builder
            this.services = services
        }

        @Override
        void resetModel() {
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
            finalizedPlan = plan.finalizePlan()
        }

        @Override
        ExecutionResult<Void> executeTasks(BuildWorkPlan buildPlan) {
            return services.planExecutor.process(finalizedPlan.asWorkSource()) { node ->
                if (node instanceof SelfExecutingNode) {
                    node.execute(null)
                }
            }
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
        ExecutionResult<Void> beforeModelDiscarded(boolean failed) {
            throw new UnsupportedOperationException()
        }

        @Override
        ExecutionResult<Void> beforeModelReset() {
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

    private class BuildServices {
        final TreeServices services
        final GradleInternal gradle
        final BuildState state
        final BuildIdentifier identifier

        BuildServices(TreeServices services, BuildIdentifier identifier, GradleInternal gradle) {
            this.identifier = identifier
            this.services = services
            this.gradle = gradle
            this.state = build(identifier, buildWorkGraphController(identifier.toString(), this))
        }
    }

    private class TreeServices {
        final PlanExecutor planExecutor
        final DefaultIncludedBuildTaskGraph buildTaskGraph
        final WorkerLeaseService workerLeaseService
        final ExecutorFactory execFactory
        final coordinationService = new DefaultResourceLockCoordinationService()

        TreeServices(int workers) {
            def configuration = new DefaultParallelismConfiguration(true, workers)
            workerLeaseService = new DefaultWorkerLeaseService(coordinationService, configuration)
            workerLeaseService.startProjectExecution(true)
            execFactory = new DefaultExecutorFactory()
            planExecutor = new DefaultPlanExecutor(configuration, execFactory, workerLeaseService, cancellationToken, coordinationService, new DefaultInternalOptions([:]))
            buildTaskGraph = new DefaultIncludedBuildTaskGraph(
                execFactory,
                new TestBuildOperationExecutor(),
                buildStateRegistry,
                workerLeaseService,
                planExecutor,
                preparer,
                MONITOR_POLL_TIME,
                TimeUnit.MILLISECONDS
            )
        }

        void stop() {
            CompositeStoppable.stoppable(buildTaskGraph, planExecutor, workerLeaseService, coordinationService, execFactory).stop()
        }
    }

    private static class TestNode extends Node implements SelfExecutingNode {
        private final String displayName
        private final List<Runnable> observers = []
        boolean executed

        TestNode(String displayName = "test node") {
            this.displayName = displayName
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
            executed = true
            sleep(SLOW_NODE_EXECUTION_TIME)
        }

        @Override
        void finishExecution(Consumer<Node> completionAction) {
            try {
                super.finishExecution(completionAction)
            } finally {
                for (final def action in observers) {
                    action.run()
                }
            }
        }
    }

    private static class DependenciesStuckNode extends TestNode {
        boolean stuck

        DependenciesStuckNode(String displayName = "test node") {
            super(displayName)
        }

        @Override
        protected DependenciesState doCheckDependenciesComplete() {
            stuck = true
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
