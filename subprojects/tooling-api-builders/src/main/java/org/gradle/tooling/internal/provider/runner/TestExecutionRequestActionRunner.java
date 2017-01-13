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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.TestExecutionRequestAction;

import java.util.Collections;

public class TestExecutionRequestActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof TestExecutionRequestAction)) {
            return;
        }
        GradleInternal gradle = buildController.getGradle();

        try {
            TestExecutionRequestAction testExecutionRequestAction = (TestExecutionRequestAction) action;
            TestExecutionResultEvaluator testExecutionResultEvaluator = new TestExecutionResultEvaluator(testExecutionRequestAction);
            buildController.addNestedListener(testExecutionResultEvaluator);
            doRun(testExecutionRequestAction, buildController);
            testExecutionResultEvaluator.evaluate();
        } catch (RuntimeException rex) {
            Throwable throwable = findRootCause(rex);
            if (throwable instanceof TestExecutionException) {
                throw new InternalTestExecutionException("Error while running test(s)", throwable);
            } else {
                throw rex;
            }
        }
        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(null), null));
    }

    private void doRun(TestExecutionRequestAction action, BuildController buildController) {
        TestExecutionBuildConfigurationAction testTasksConfigurationAction = new TestExecutionBuildConfigurationAction(action, buildController.getGradle());
        buildController.getGradle().getServices().get(BuildConfigurationActionExecuter.class).setTaskSelectors(Collections.singletonList(testTasksConfigurationAction));
        buildController.run();
    }

    private Throwable findRootCause(Exception tex) {
        Throwable t = tex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
