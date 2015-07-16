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
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.execution.BuildConfigurationAction;
import org.gradle.execution.BuildExecutionContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestExecutionDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.internal.provider.TestExecutionRequestAction;

import java.util.*;

public class TestExecutionRequestActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof TestExecutionRequestAction)) {
            return;
        }
        final GradleInternal gradle = buildController.getGradle();
        final TestExecutionRequestAction testExecutionRequestAction = (TestExecutionRequestAction) action;
        buildController.registerBuildConfigurationAction(new BuildConfigurationAction() {
            @Override
            public void configure(BuildExecutionContext context) {
                final Set<Task> allTasksToRun = new HashSet<Task>();
                final GradleInternal gradleInternal = context.getGradle();
                allTasksToRun.addAll(configureBuildForTestDescriptors(gradleInternal, testExecutionRequestAction));
                allTasksToRun.addAll(configureBuildForTestClasses(gradleInternal, testExecutionRequestAction));
                gradle.getTaskGraph().addTasks(allTasksToRun);
            }
        });
        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);

        Throwable failure = null;
        try {
            buildController.run();
        } catch (RuntimeException rex) {
            Throwable throwable = findRootCause(rex);
            if (throwable instanceof InternalTestExecutionException) {
                failure = throwable;
            } else if (throwable instanceof TestExecutionException) {
                failure = new InternalTestExecutionException("Error while running test(s)", throwable);
            } else {
                throw rex;
            }
        }
        BuildActionResult buildActionResult;
        if (failure != null) {
            buildActionResult = new BuildActionResult(null, payloadSerializer.serialize(failure));
        } else {
            buildActionResult = new BuildActionResult(payloadSerializer.serialize(null), null);
        }
        buildController.setResult(buildActionResult);
    }

    private List<Task> configureBuildForTestClasses(GradleInternal gradle, final TestExecutionRequestAction testExecutionRequestAction) {

        if (testExecutionRequestAction.getTestExecutionRequest().getTestClassNames().isEmpty()) {
            return Collections.emptyList();
        }
        List<Task> tasksToExecute = new ArrayList<Task>();
        final Collection<String> testClassNames = testExecutionRequestAction.getTestExecutionRequest().getTestClassNames();
        final Set<Project> allprojects = gradle.getRootProject().getAllprojects();
        for (Project project : allprojects) {
            final TaskCollection<Test> testTasks = project.getTasks().withType(Test.class);
            for (Test testTask : testTasks) {
                addTestClassFilter(testTask, testClassNames);
            }
            tasksToExecute.addAll(testTasks);
        }
        return tasksToExecute;
    }

    private void addTestClassFilter(Test testTask, Collection<String> testClassNames) {
        for (String testClassName : testClassNames) {
            testTask.getFilter().includeTest(testClassName, null);
        }
    }

    private List<Task> configureBuildForTestDescriptors(GradleInternal gradle, final TestExecutionRequestAction testExecutionRequestAction) {
        final Collection<InternalJvmTestExecutionDescriptor> testDescriptors = testExecutionRequestAction.getTestExecutionRequest().getTestExecutionDescriptors();
        final List<String> testTaskPaths = org.gradle.util.CollectionUtils.collect(testDescriptors, new Transformer<String, InternalJvmTestExecutionDescriptor>() {
            @Override
            public String transform(InternalJvmTestExecutionDescriptor internalJvmTestDescriptor) {
                return internalJvmTestDescriptor.getTaskPath();
            }
        });

        List<Task> testTasksToRun = new ArrayList<Task>();
        for (final String testTaskPath : testTaskPaths) {
            final Task task = gradle.getRootProject().getTasks().findByPath(testTaskPath);
            if (task == null) {
                throw new TestExecutionException(String.format("Requested test task with path '%s' cannot be found.", testTaskPath));
            } else if (!(task instanceof Test)) {
                throw new TestExecutionException(String.format("Task '%s' of type '%s' not supported for executing tests via TestLauncher API.", testTaskPath, task.getClass().getName()));
            } else {
                Test testTask = (Test) task;
                for (InternalJvmTestExecutionDescriptor testDescriptor : testDescriptors) {
                    if (testDescriptor.getTaskPath().equals(testTaskPath)) {
                        final String className = testDescriptor.getClassName();
                        final String methodName = testDescriptor.getMethodName();
                        if (className == null && methodName == null) {
                            testTask.getFilter().includeTestsMatching("*");
                        } else {
                            testTask.getFilter().includeTest(className, methodName);
                        }
                    }
                }
                testTasksToRun.add(task);
            }
        }
        return testTasksToRun;
    }

    private Throwable findRootCause(Exception tex) {
        Throwable t = tex.getCause();
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
