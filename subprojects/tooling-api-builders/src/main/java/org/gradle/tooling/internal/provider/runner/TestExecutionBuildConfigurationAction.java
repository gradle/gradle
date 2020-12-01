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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.execution.BuildConfigurationAction;
import org.gradle.execution.BuildExecutionContext;
import org.gradle.execution.TaskSelection;
import org.gradle.execution.TaskSelectionException;
import org.gradle.execution.TaskSelector;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.event.types.DefaultTestDescriptor;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.internal.DefaultJavaDebugOptions;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalDebugOptions;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.provider.TestExecutionRequestAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TestExecutionBuildConfigurationAction implements BuildConfigurationAction {
    private final GradleInternal gradle;
    private final TestExecutionRequestAction testExecutionRequest;

    public TestExecutionBuildConfigurationAction(TestExecutionRequestAction testExecutionRequest, GradleInternal gradle) {
        this.testExecutionRequest = testExecutionRequest;
        this.gradle = gradle;
    }

    @Override
    public void configure(BuildExecutionContext context) {
        final Set<Test> allTestTasksToRun = new LinkedHashSet<Test>();
        final GradleInternal gradleInternal = context.getGradle();
        allTestTasksToRun.addAll(configureBuildForTestDescriptors(gradleInternal.getServices().get(TaskSelector.class), testExecutionRequest));
        allTestTasksToRun.addAll(configureBuildForInternalJvmTestRequest(gradleInternal, testExecutionRequest));
        allTestTasksToRun.addAll(configureBuildForTestTasks(gradleInternal, testExecutionRequest));
        configureTestTasks(allTestTasksToRun);
        gradle.getTaskGraph().addEntryTasks(allTestTasksToRun);
    }

    private void configureTestTasks(Set<Test> allTestTasksToRun) {
        for (Test task : allTestTasksToRun) {
            task.setIgnoreFailures(true);
            task.getFilter().setFailOnNoMatchingTests(false);
            task.getOutputs().upToDateWhen(Specs.SATISFIES_NONE);
            InternalDebugOptions debugOptions = testExecutionRequest.getDebugOptions();
            if (debugOptions.isDebugMode()) {
                task.debugOptions(new Action<JavaDebugOptions>() {
                    @Override
                    public void execute(JavaDebugOptions javaDebugOptions) {
                        DefaultJavaDebugOptions options = (DefaultJavaDebugOptions) javaDebugOptions;
                        options.getEnabled().set(true);
                        options.getPort().set(debugOptions.getPort());
                        options.getServer().set(false);
                        options.getSuspend().set(false);
                    }
                });
            }
        }
    }

    private List<Test> configureBuildForTestDescriptors(TaskSelector taskSelector, TestExecutionRequestAction testExecutionRequest) {
        Map<String, List<InternalJvmTestRequest>> taskAndTests = testExecutionRequest.getTaskAndTests();

        List<Test> testTasksToRun = new ArrayList<Test>();
        for (final Map.Entry<String, List<InternalJvmTestRequest>> entry : taskAndTests.entrySet()) {
            String testTaskPath = entry.getKey();
            TaskSelection taskSelection = taskSelector.getSelection(testTaskPath);
            Set<Task> tasks = taskSelection.getTasks();
            if (tasks.isEmpty()) {
                throw new TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", testTaskPath));
            }
            for (Task task : tasks) {
                if (!(task instanceof Test)) {
                    throw new TestExecutionException(String.format("Task '%s' of type '%s' not supported for executing tests via TestLauncher API.", testTaskPath, task.getClass().getName()));
                } else {
                    Test testTask = (Test) task;
                    for (InternalJvmTestRequest jvmTestRequest : entry.getValue()) {
                        final TestFilter filter = testTask.getFilter();
                        filter.includeTest(jvmTestRequest.getClassName(), jvmTestRequest.getMethodName());
                    }
                    testTasksToRun.add(testTask);
                }
            }
        }
        return testTasksToRun;
    }

    private static List<Test> configureBuildForTestTasks(GradleInternal rootBuild, TestExecutionRequestAction testExecutionRequest) {
        final Collection<InternalTestDescriptor> testDescriptors = testExecutionRequest.getTestExecutionDescriptors();
        List<ContainerAwareTaskPath> testTaskPaths = collectTaskPaths(rootBuild, testDescriptors);

        List<Test> testTasksToRun = new ArrayList<>();
        for (ContainerAwareTaskPath testTaskPath : testTaskPaths) {
            Set<Test> tasks = lookupTestTasks(testTaskPath);
            for (Test testTask : tasks) {
                for (InternalTestDescriptor testDescriptor : testDescriptors) {
                    DefaultTestDescriptor defaultTestDescriptor = (DefaultTestDescriptor) testDescriptor;
                    if (defaultTestDescriptor.getTaskPath().equals(testTaskPath.getPath())) {
                        String className = defaultTestDescriptor.getClassName();
                        String methodName = defaultTestDescriptor.getMethodName();
                        if (className == null && methodName == null) {
                            testTask.getFilter().includeTestsMatching("*");
                        } else {
                            testTask.getFilter().includeTest(className, methodName);
                        }
                    }
                }
                testTasksToRun.add(testTask);
            }
        }
        return testTasksToRun;
    }

    private static List<ContainerAwareTaskPath> collectTaskPaths(GradleInternal rootBuild, Collection<InternalTestDescriptor> testDescriptors) {
        Map<String, GradleInternal> includedBuilds = getIncludedBuilds(rootBuild);

        List<ContainerAwareTaskPath> testTaskPaths = new ArrayList<>();
        for (InternalTestDescriptor testDescriptor : testDescriptors) {
            DefaultTestDescriptor defaultTestDescriptor = (DefaultTestDescriptor) testDescriptor;
            String buildIdentityPath = defaultTestDescriptor.getBuildIdentityPath();
            if (buildIdentityPath != null) {
                GradleInternal includedBuild = includedBuilds.get(buildIdentityPath);
                if (includedBuild == null) {
                    throw new IllegalStateException("Build operation references nonexisting included build: " + buildIdentityPath);
                }
                testTaskPaths.add(new ContainerAwareTaskPath(defaultTestDescriptor.getTaskPath(), includedBuild));
            } else {
                testTaskPaths.add(new ContainerAwareTaskPath(defaultTestDescriptor.getTaskPath(), rootBuild));
            }
        }
        return testTaskPaths;
    }

    private static Set<Test> lookupTestTasks(ContainerAwareTaskPath testTaskPath) {
        Set<Task> tasks;
        String path = testTaskPath.getPath();
        try {
            TaskSelector taskSelector = testTaskPath.getGradle().getServices().get(TaskSelector.class);
            TaskSelection taskSelection = taskSelector.getSelection(path);
            tasks = taskSelection.getTasks();
        } catch (TaskSelectionException e) {
            throw new TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", path));
        }
        if (tasks.isEmpty()) {
            throw new TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", path));
        }
        Set<Test> testTasks = new LinkedHashSet<>();
        for (Task task : tasks) {
            if (!(task instanceof Test)) {
                throw new TestExecutionException(String.format("Task '%s' of type '%s' not supported for executing tests via TestLauncher API.", testTaskPath, task.getClass().getName()));
            }
            testTasks.add((Test) task);
        }
        return testTasks;
    }

    private static Map<String, GradleInternal> getIncludedBuilds(GradleInternal rootBuild) {
        BuildStateRegistry buildStateRegistry = rootBuild.getServices().get(BuildStateRegistry.class);
        Map<String, GradleInternal> includedBuilds = new LinkedHashMap<>();
        for (IncludedBuildState includedBuild : buildStateRegistry.getIncludedBuilds()) {
            includedBuilds.put(includedBuild.getIdentityPath().getPath(), includedBuild.getBuild());
        }
        return includedBuilds;
    }

    private List<Test> configureBuildForInternalJvmTestRequest(GradleInternal gradle, TestExecutionRequestAction testExecutionRequest) {
        final Collection<InternalJvmTestRequest> internalJvmTestRequests = testExecutionRequest.getInternalJvmTestRequests();
        if(internalJvmTestRequests.isEmpty()){
            return Collections.emptyList();
        }

        List<Test> tasksToExecute = new ArrayList<>();

        final Set<Project> allprojects = gradle.getRootProject().getAllprojects();
        for (Project project : allprojects) {
            final Collection<Test> testTasks = project.getTasks().withType(Test.class);
            for (Test testTask : testTasks) {
                for (InternalJvmTestRequest jvmTestRequest : internalJvmTestRequests) {
                    final TestFilter filter = testTask.getFilter();
                    filter.includeTest(jvmTestRequest.getClassName(), jvmTestRequest.getMethodName());
                }
            }
            tasksToExecute.addAll(testTasks);
        }
        return tasksToExecute;
    }

    private static class ContainerAwareTaskPath {
        private final String path;
        private final GradleInternal gradle;

        public ContainerAwareTaskPath(String path, GradleInternal gradle) {

            this.path = path;
            this.gradle = gradle;
        }

        public String getPath() {
            return path;
        }

        public GradleInternal getGradle() {
            return gradle;
        }
    }
}
