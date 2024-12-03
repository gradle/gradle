/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.TaskSelection;
import org.gradle.execution.TaskSelectionException;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.internal.build.event.types.DefaultTestDescriptor;
import org.gradle.process.internal.DefaultJavaDebugOptions;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalDebugOptions;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.protocol.test.InternalTaskSpec;
import org.gradle.tooling.internal.protocol.test.InternalTestSpec;
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@NonNullApi
class TestExecutionBuildConfigurationAction implements EntryTaskSelector {
    private final TestExecutionRequestAction testExecutionRequest;

    public TestExecutionBuildConfigurationAction(TestExecutionRequestAction testExecutionRequest) {
        this.testExecutionRequest = testExecutionRequest;
    }

    @Override
    public void applyTasksTo(Context context, ExecutionPlan plan) {
        final Set<Task> allTasksToRun = new LinkedHashSet<>();
        collectTasksForTestDescriptors(context, allTasksToRun);
        collectTasksForInternalJvmTestRequest(context.getGradle(), allTasksToRun);
        collectTestTasks(context, allTasksToRun);
        configureTestTasks(allTasksToRun);
        addEntryTasksTo(plan, allTasksToRun);
    }

    @Override
    public void postProcessExecutionPlan(Context context, QueryableExecutionPlan plan) {
        configureTestTasksForTestDescriptors(context);
        configureTestTasksForInternalJvmTestRequest(plan);
        configureTestTasksInBuild(context);
    }

    private static void addEntryTasksTo(ExecutionPlan plan, Set<Task> allTasksToRun) {
        for (Task task : allTasksToRun) {
            plan.addEntryTask(task);
        }
    }

    private void configureTestTasks(Set<Task> tasks) {
        for (Task task : tasks) {
            if (task instanceof AbstractTestTask) {
                configureTestTask((AbstractTestTask) task);
            }
        }
    }

    private void configureTestTask(AbstractTestTask test) {
        test.getFilter().getFailOnNoMatchingTests().set(false);
        test.getOutputs().upToDateWhen(Specs.SATISFIES_NONE);
        if (test instanceof Test) {
            InternalDebugOptions debugOptions = testExecutionRequest.getDebugOptions();
            if (debugOptions.isDebugMode()) {
                ((Test) test).debugOptions(javaDebugOptions -> {
                    DefaultJavaDebugOptions options = (DefaultJavaDebugOptions) javaDebugOptions;
                    options.getEnabled().set(true);
                    options.getPort().set(debugOptions.getPort());
                    options.getServer().set(false);
                    options.getSuspend().set(false);
                });
            } else {
                ((Test) test).debugOptions(javaDebugOptions -> {
                    DefaultJavaDebugOptions options = (DefaultJavaDebugOptions) javaDebugOptions;
                    options.getEnabled().set(false);
                });
            }
        }
    }

    private void configureTestTasksForTestDescriptors(Context context) {
        Map<String, List<InternalJvmTestRequest>> taskAndTests = testExecutionRequest.getTaskAndTests();
        for (final Map.Entry<String, List<InternalJvmTestRequest>> entry : taskAndTests.entrySet()) {
            String testTaskPath = entry.getKey();
            for (AbstractTestTask testTask : queryTestTasks(context, testTaskPath)) {
                configureTestTask(testTask);
                for (InternalJvmTestRequest jvmTestRequest : entry.getValue()) {
                    final TestFilter filter = testTask.getFilter();
                    filter.includeTest(jvmTestRequest.getClassName(), jvmTestRequest.getMethodName());
                }
            }
        }

        for (InternalTaskSpec taskSpec : testExecutionRequest.getTaskSpecs()) {
            if (taskSpec instanceof InternalTestSpec) {
                InternalTestSpec testSpec = (InternalTestSpec) taskSpec;
                Set<AbstractTestTask> tasks = queryTestTasks(context, taskSpec.getTaskPath());
                for (AbstractTestTask task : tasks) {
                    DefaultTestFilter filter = (DefaultTestFilter) task.getFilter();
                    for (String cls : testSpec.getClasses()) {
                        filter.includeCommandLineTest(cls, null);
                    }
                    for (Map.Entry<String, List<String>> entry : testSpec.getMethods().entrySet()) {
                        String cls = entry.getKey();
                        for (String method : entry.getValue()) {
                            filter.includeCommandLineTest(cls, method);
                        }
                    }
                    SetProperty<String> commandLineIncludePatterns = filter.getCommandLineIncludePatterns();
                    commandLineIncludePatterns.addAll(testSpec.getPatterns());
                    for (String pkg : testSpec.getPackages()) {
                        commandLineIncludePatterns.add(pkg + ".*");
                    }
                }
            }
        }
    }

    private void configureTestTasksForInternalJvmTestRequest(QueryableExecutionPlan plan) {
        final Collection<InternalJvmTestRequest> internalJvmTestRequests = testExecutionRequest.getInternalJvmTestRequests();
        if (internalJvmTestRequests.isEmpty()) {
            return;
        }

        forEachTaskIn(plan, task -> {
            if (task instanceof AbstractTestTask) {
                AbstractTestTask testTask = (AbstractTestTask) task;
                configureTestTask(testTask);
                for (InternalJvmTestRequest jvmTestRequest : internalJvmTestRequests) {
                    final TestFilter filter = testTask.getFilter();
                    filter.includeTest(jvmTestRequest.getClassName(), jvmTestRequest.getMethodName());
                }
            }
        });
    }

    private void configureTestTasksInBuild(Context context) {
        final Collection<InternalTestDescriptor> testDescriptors = testExecutionRequest.getTestExecutionDescriptors();
        for (final InternalTestDescriptor descriptor : testDescriptors) {
            final String testTaskPath = taskPathOf(descriptor);
            for (AbstractTestTask testTask : queryTestTasks(context, testTaskPath)) {
                configureTestTask(testTask);
                for (InternalTestDescriptor testDescriptor : testDescriptors) {
                    if (taskPathOf(testDescriptor).equals(testTaskPath)) {
                        includeTestMatching((InternalJvmTestDescriptor) testDescriptor, testTask);
                    }
                }
            }
        }
    }

    private void collectTasksForTestDescriptors(Context context, Collection<Task> tasksToRun) {
        Map<String, List<InternalJvmTestRequest>> taskAndTests = testExecutionRequest.getTaskAndTests();
        for (final Map.Entry<String, List<InternalJvmTestRequest>> entry : taskAndTests.entrySet()) {
            String testTaskPath = entry.getKey();
            tasksToRun.addAll(queryTestTasks(context, testTaskPath));
        }

        for (InternalTaskSpec taskSpec : testExecutionRequest.getTaskSpecs()) {
            if (taskSpec instanceof InternalTestSpec) {
                tasksToRun.addAll(queryTestTasks(context, taskSpec.getTaskPath()));
            } else {
                tasksToRun.addAll(queryTasks(context, taskSpec.getTaskPath()));
            }
        }
    }

    private static void includeTestMatching(InternalJvmTestDescriptor descriptor, AbstractTestTask testTask) {
        String className = descriptor.getClassName();
        String methodName = descriptor.getMethodName();
        if (className == null && methodName == null) {
            testTask.getFilter().includeTestsMatching("*");
        } else {
            testTask.getFilter().includeTest(className, methodName);
        }
    }

    private void collectTestTasks(Context context, Collection<Task> testTasksToRun) {
        for (final InternalTestDescriptor descriptor : testExecutionRequest.getTestExecutionDescriptors()) {
            final String testTaskPath = taskPathOf(descriptor);
            testTasksToRun.addAll(queryTestTasks(context, testTaskPath));
        }
    }

    private static Set<Task> queryTasks(Context context, String testTaskPath) {
        TaskSelection taskSelection;
        try {
            taskSelection = context.getSelection(testTaskPath);
        } catch (TaskSelectionException e) {
            throw new TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", testTaskPath));
        }

        Set<Task> tasks = taskSelection.getTasks();
        if (tasks.isEmpty()) {
            throw new TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", testTaskPath));
        }

        return tasks;
    }

    private static Set<AbstractTestTask> queryTestTasks(Context context, String testTaskPath) {
        Set<AbstractTestTask> result = new LinkedHashSet<>();
        for (Task task : queryTasks(context, testTaskPath)) {
            if (!(task instanceof AbstractTestTask)) {
                throw new TestExecutionException(String.format("Task '%s' of type '%s' not supported for executing tests via TestLauncher API.", testTaskPath, task.getClass().getName()));
            }
            result.add((AbstractTestTask) task);
        }
        return result;
    }

    private void collectTasksForInternalJvmTestRequest(GradleInternal gradle, Collection<Task> tasksToExecute) {
        final Collection<InternalJvmTestRequest> internalJvmTestRequests = testExecutionRequest.getInternalJvmTestRequests();
        if (internalJvmTestRequests.isEmpty()) {
            return;
        }

        gradle.getOwner().ensureProjectsConfigured();
        for (ProjectState projectState : gradle.getOwner().getProjects().getAllProjects()) {
            projectState.ensureConfigured();
            projectState.applyToMutableState(project -> {
                final Collection<AbstractTestTask> testTasks = project.getTasks().withType(AbstractTestTask.class);
                tasksToExecute.addAll(testTasks);
            });
        }
    }

    private static void forEachTaskIn(QueryableExecutionPlan plan, Consumer<Task> taskConsumer) {
        plan.getTasks().forEach(taskConsumer);
    }

    private static String taskPathOf(InternalTestDescriptor descriptor) {
        return ((DefaultTestDescriptor) descriptor).getTaskPath();
    }
}
