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

package org.gradle.launcher.exec;

import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;

public class WatchModeBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final BuildActionExecuter<BuildActionParameters> delegate;

    public WatchModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        if (watchModeEnabled(actionParameters)) {
            return executeMultipleBuilds(action, requestContext, actionParameters);
        }
        return executeSingleBuild(action, requestContext, actionParameters);
    }

    private Object executeMultipleBuilds(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        Object lastResult = null;
        while (buildNotStopped(requestContext)) {
            lastResult = executeSingleBuild(action, requestContext, actionParameters);
            waitForTrigger();
            // TODO: only run once right now.
            break;
        }
        return lastResult;
    }

    private Object executeSingleBuild(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        return delegate.execute(action, requestContext, actionParameters);
    }

    private boolean watchModeEnabled(BuildActionParameters actionParameters) {
        return actionParameters.isWatchModeEnabled();
    }

    private boolean buildNotStopped(BuildRequestContext requestContext) {
        return !requestContext.getCancellationToken().isCancellationRequested();
    }

    private void waitForTrigger() {
        try {
            Thread.sleep(1000);
        } catch (Exception e) {}
    }
}
