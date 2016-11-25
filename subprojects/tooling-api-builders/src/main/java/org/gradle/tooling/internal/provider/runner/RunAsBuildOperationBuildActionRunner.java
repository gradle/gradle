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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.Action;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationExecutor;

/**
 * An {@link BuildActionRunner} that wraps all work in a build operation. Should be run after {@link SubscribableBuildActionRunner} which takes care of listener registration to forward the events back to the client.
 */
public class RunAsBuildOperationBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    RunAsBuildOperationBuildActionRunner(BuildActionRunner delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void run(final BuildAction action, final BuildController buildController) {
        buildOperationExecutor.run("Run build", new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                delegate.run(action, buildController);
            }
        });
    }
}
