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

import com.google.common.collect.Lists;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.execution.DefaultTasksBuildExecutionAction;
import org.gradle.execution.TaskNameResolvingBuildConfigurationAction;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.internal.provider.TestExecutionRequestAction;

public class TestExecutionRequestActionRunner implements BuildActionRunner {

    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof TestExecutionRequestAction)) {
            return;
        }
        TestCountListener testCountListener = new TestCountListener();
        final GradleInternal gradle = buildController.getGradle();

        Throwable failure = null;
        try {
            doRun((TestExecutionRequestAction) action, buildController, testCountListener);
            evaluateTestCount(testCountListener);
        } catch (RuntimeException rex) {
            Throwable throwable = findRootCause(rex);
            if (throwable instanceof TestExecutionException) {
                failure = new InternalTestExecutionException("Error while running test(s)", throwable);
            } else {
                throw rex;
            }
        }
        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        BuildActionResult buildActionResult;
        if (failure != null) {
            buildActionResult = new BuildActionResult(null, payloadSerializer.serialize(failure));
        } else {
            buildActionResult = new BuildActionResult(payloadSerializer.serialize(null), null);
        }
        buildController.setResult(buildActionResult);
    }

    private void evaluateTestCount(TestCountListener testCountListener) {
        if (testCountListener.hasUnmatchedTests()) {
            throw new TestExecutionException("Tests configured in TestLauncher not found in any candidate test task.");
        }
    }

    private void doRun(TestExecutionRequestAction action, BuildController buildController, TestCountListener testCountListener) {
        TestExecutionBuildConfigurationAction testTasksConfigurationAction = new TestExecutionBuildConfigurationAction(action.getTestExecutionRequest(), buildController.getGradle(), testCountListener);
        @SuppressWarnings("unchecked")
        ReplaceBuildConfigurationTransformer replaceBuildConfigurationTransformer = new ReplaceBuildConfigurationTransformer(testTasksConfigurationAction,
            Lists.newArrayList(TaskNameResolvingBuildConfigurationAction.class, DefaultTasksBuildExecutionAction.class));

        buildController.registerBuildConfigurationTransformer(replaceBuildConfigurationTransformer);

        buildController.run();
    }

    private Throwable findRootCause(Exception tex) {
        Throwable t = tex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private class TestCountListener implements TestListener {
        private long resultCount;
        private boolean didJob;

        @Override
        public void beforeSuite(TestDescriptor suite) {
        }

        @Override
        public void afterSuite(TestDescriptor suite, TestResult result) {
            didJob = true;
            if (suite.getParent() == null) {
                resultCount = resultCount + result.getTestCount();
            }
        }

        @Override
        public void beforeTest(TestDescriptor testDescriptor) {
        }

        @Override
        public void afterTest(TestDescriptor test, TestResult result) {
        }

        public boolean hasUnmatchedTests() {
            return resultCount == 0 && didJob; // up-to-date tests tasks are ignored
        }
    }

}
