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

import org.gradle.api.*;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestExecutionDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.internal.provider.TestExecutionRequestAction;

import java.util.Collection;
import java.util.List;

public class TestExecutionRequestActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof TestExecutionRequestAction)) {
            return;
        }
        final GradleInternal gradle = buildController.getGradle();


        TestExecutionRequestAction testExecutionRequestAction = (TestExecutionRequestAction) action;

        final Collection<InternalJvmTestExecutionDescriptor> testDescriptors = testExecutionRequestAction.getTestExecutionDescriptors();
        final List<String> testTaskPaths = org.gradle.util.CollectionUtils.collect(testDescriptors, new Transformer<String, InternalJvmTestExecutionDescriptor>() {
            @Override
            public String transform(InternalJvmTestExecutionDescriptor internalJvmTestDescriptor) {
                return internalJvmTestDescriptor.getTaskPath();
            }
        });
        testExecutionRequestAction.getStartParameter().setTaskNames(testTaskPaths);
        for (final String testTaskPath : testTaskPaths) {
            gradle.addProjectEvaluationListener(new ProjectEvaluationListener() {
                @Override
                public void beforeEvaluate(Project project) {
                }

                @Override
                public void afterEvaluate(Project project, ProjectState state) {
                    final Task task = project.getTasks().findByPath(testTaskPath);
                    if (task != null && task instanceof Test) {
                        Test testTask = (Test) task;
                        for (InternalJvmTestExecutionDescriptor testDescriptor : testDescriptors) {
                            if (testDescriptor.getTaskPath().equals(testTaskPath)) {
                                final String className = testDescriptor.getClassName();
                                final String methodName = testDescriptor.getMethodName();
                                if (className != null) {
                                    testTask.getFilter().includeTest(className, methodName);
                                }
                            }
                        }
                    }
                }
            });

        }


        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);

        Throwable failure = null;
        try {
            buildController.run();
        } catch (RuntimeException rex) {
            Throwable throwable = findRootCause(rex);
            if(throwable instanceof TestExecutionException){
                failure = new InternalTestExecutionException(throwable);
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

    private Throwable findRootCause(Exception tex) {
        Throwable t = tex.getCause();
        while(t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
