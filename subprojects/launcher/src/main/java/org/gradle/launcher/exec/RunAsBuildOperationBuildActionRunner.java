/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

/**
 * An {@link BuildActionRunner} that wraps all work in a build operation.
 */
public class RunAsBuildOperationBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private static final RunBuildBuildOperationType.Details DETAILS = new RunBuildBuildOperationType.Details() {};
    private static final RunBuildBuildOperationType.Result RESULT = new RunBuildBuildOperationType.Result() {};

    public RunAsBuildOperationBuildActionRunner(BuildActionRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public void run(final BuildAction action, final BuildController buildController) {
        BuildOperationExecutor buildOperationExecutor = buildController.getGradle().getServices().get(BuildOperationExecutor.class);
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                checkDeprecations((StartParameterInternal)buildController.getGradle().getStartParameter());
                buildController.getGradle().getServices().get(IncludedBuildControllers.class).rootBuildOperationStarted();
                delegate.run(action, buildController);
                context.setResult(RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Run build").details(DETAILS);
            }
        });
    }

    private void checkDeprecations(StartParameterInternal startParameter) {
        startParameter.checkDeprecation();
    }
}
