/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.client;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.daemon.connection.DaemonBuildExecuter;
import org.gradle.launcher.daemon.connection.DaemonBuildRequest;
import org.gradle.launcher.daemon.connection.DaemonBuildResult;
import org.gradle.launcher.daemon.connection.JvmBuildRequest;
import org.gradle.launcher.daemon.connection.JvmBuildResult;
import org.gradle.launcher.exec.BuildActionExecutor;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.jspecify.annotations.NullMarked;

/**
 * The client piece of the build daemon.
 *
 * <p>This adapts the {@link BuildActionExecutor} contract used by the launcher to the {@link DaemonBuildExecuter}
 * in the daemon connection layer: it unpacks the {@link ClientBuildRequestContext} into a transport-agnostic
 * {@link DaemonBuildRequest} and hands it to the executer, which locates or starts a daemon, sends the build,
 * streams its output back, and returns the result.
 */
@NullMarked
public class DaemonClient implements BuildActionExecutor<BuildActionParameters, ClientBuildRequestContext> {
    private final DaemonBuildExecuter executer;

    public DaemonClient(DaemonBuildExecuter executer) {
        this.executer = executer;
    }

    @VisibleForTesting
    public DaemonBuildExecuter getExecuter() {
        return executer;
    }

    /**
     * Executes the given action in a daemon. The action and parameters must be serializable.
     *
     * @param action The action
     */
    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters parameters, ClientBuildRequestContext requestContext) {
        DaemonBuildResult result = executer.execute(new JvmBuildRequest(
            action,
            requestContext.getClient(),
            requestContext.getStartTime(),
            requestContext.isInteractiveConsole(),
            parameters,
            requestContext.getCancellationToken(),
            requestContext.getEventConsumer()
        ));
        return ((JvmBuildResult) result).getBuildActionResult();
    }
}
