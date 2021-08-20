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

import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction;

import java.util.Collections;

public class TestExecutionRequestActionRunner implements BuildActionRunner {
    private final BuildOperationAncestryTracker ancestryTracker;
    private final BuildOperationListenerManager buildOperationListenerManager;

    public TestExecutionRequestActionRunner(
        BuildOperationAncestryTracker ancestryTracker,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        this.ancestryTracker = ancestryTracker;
        this.buildOperationListenerManager = buildOperationListenerManager;
    }

    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        if (!(action instanceof TestExecutionRequestAction)) {
            return Result.nothing();
        }

        try {
            TestExecutionRequestAction testExecutionRequestAction = (TestExecutionRequestAction) action;
            TestExecutionResultEvaluator testExecutionResultEvaluator = new TestExecutionResultEvaluator(ancestryTracker, testExecutionRequestAction);
            buildOperationListenerManager.addListener(testExecutionResultEvaluator);
            try {
                doRun(testExecutionRequestAction, buildController);
            } finally {
                buildOperationListenerManager.removeListener(testExecutionResultEvaluator);
            }
            testExecutionResultEvaluator.evaluate();
        } catch (RuntimeException e) {
            Throwable throwable = findRootCause(e);
            if (throwable instanceof TestExecutionException) {
                return Result.failed(e, new InternalTestExecutionException("Error while running test(s)", throwable));
            } else {
                return Result.failed(e);
            }
        }

        return Result.of(null);
    }

    private void doRun(TestExecutionRequestAction action, BuildTreeLifecycleController buildController) {
        buildController.beforeBuild(gradle -> {
            TestExecutionBuildConfigurationAction testTasksConfigurationAction = new TestExecutionBuildConfigurationAction(action, gradle);
            gradle.getServices().get(BuildConfigurationActionExecuter.class).setTaskSelectors(Collections.singletonList(testTasksConfigurationAction));
        });
        buildController.scheduleAndRunTasks();
    }

    private Throwable findRootCause(Exception tex) {
        Throwable t = tex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
