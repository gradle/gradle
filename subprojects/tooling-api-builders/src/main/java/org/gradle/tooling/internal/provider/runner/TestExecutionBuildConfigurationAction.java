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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.execution.BuildConfigurationAction;
import org.gradle.execution.BuildExecutionContext;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest;
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor;

import java.util.*;

class TestExecutionBuildConfigurationAction implements BuildConfigurationAction {
    private final GradleInternal gradle;
    private List<TestListener> globalTestListeners;
    private InternalTestExecutionRequest testExecutionRequest;

    public TestExecutionBuildConfigurationAction(InternalTestExecutionRequest testExecutionRequest, GradleInternal gradle, TestListener... globalTestListeners) {
        this.testExecutionRequest = testExecutionRequest;
        this.gradle = gradle;
        this.globalTestListeners = Arrays.asList(globalTestListeners);
    }

    @Override
    public void configure(BuildExecutionContext context) {
        final Set<Test> allTestTasksToRun = new LinkedHashSet<Test>();
        final GradleInternal gradleInternal = context.getGradle();
        allTestTasksToRun.addAll(configureBuildForTestDescriptors(gradleInternal, testExecutionRequest));
        allTestTasksToRun.addAll(configureBuildForTestClasses(gradleInternal, testExecutionRequest));

        configureTestTasks(allTestTasksToRun);
        gradle.getTaskGraph().addTasks(allTestTasksToRun);
    }

    private void configureTestTasks(Set<Test> allTestTasksToRun) {
        registerGlobalTestListener(allTestTasksToRun);
        forceTaskExecution(allTestTasksToRun);
    }

    private void forceTaskExecution(Set<? extends Task> allTestTasksToRun) {
        for (Task task : allTestTasksToRun) {
            task.getOutputs().upToDateWhen(Specs.SATISFIES_NONE);
        }
    }

    private List<Test> configureBuildForTestDescriptors(GradleInternal gradle, final InternalTestExecutionRequest testExecutionRequestAction) {
        final Collection<InternalTestDescriptor> testDescriptors = testExecutionRequestAction.getTestExecutionDescriptors();

        final List<String> testTaskPaths = org.gradle.util.CollectionUtils.collect(testDescriptors, new Transformer<String, InternalTestDescriptor>() {
            @Override
            public String transform(InternalTestDescriptor testDescriptor) {
                return ((DefaultTestDescriptor) testDescriptor).getTaskPath();
            }
        });

        List<Test> testTasksToRun = new ArrayList<Test>();
        for (final String testTaskPath : testTaskPaths) {
            final Task task = gradle.getRootProject().getTasks().findByPath(testTaskPath);
            if (task == null) {
                throw new TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", testTaskPath));
            } else if (!(task instanceof Test)) {
                throw new TestExecutionException(String.format("Task '%s' of type '%s' not supported for executing tests via TestLauncher API.", testTaskPath, task.getClass().getName()));
            } else {
                Test testTask = (Test) task;
                for (InternalTestDescriptor testDescriptor : testDescriptors) {
                    DefaultTestDescriptor defaultTestDescriptor = (DefaultTestDescriptor) testDescriptor;
                    if (defaultTestDescriptor.getTaskPath().equals(testTaskPath)) {
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

    private void registerGlobalTestListener(Set<Test> testTasks) {
        for (TestListener globalTestListener : globalTestListeners) {
            for (Test task : testTasks) {
                task.addTestListener(globalTestListener);
            }
        }
    }

    private List<Test> configureBuildForTestClasses(GradleInternal gradle, final InternalTestExecutionRequest testExecutionRequest) {
        if (testExecutionRequest.getTestClassNames().isEmpty()) {
            return Collections.emptyList();
        }
        List<Test> tasksToExecute = new ArrayList<Test>();
        final Collection<String> testClassNames = testExecutionRequest.getTestClassNames();
        final Set<Project> allprojects = gradle.getRootProject().getAllprojects();
        for (Project project : allprojects) {
            final TaskCollection<Test> testTasks = project.getTasks().withType(Test.class);
            for (Test testTask : testTasks) {
                configureTestClassFilter(testTask, testClassNames);
            }
            tasksToExecute.addAll(testTasks);
        }
        return tasksToExecute;
    }

    private void configureTestClassFilter(Test testTask, Collection<String> testClassNames) {
        for (String testClassName : testClassNames) {
            final TestFilter filter = testTask.getFilter();
            filter.includeTest(testClassName, null);
            filter.setFailOnNoMatchingTests(false);
        }
    }
}
