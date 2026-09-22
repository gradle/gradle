/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon.server.api;

import org.gradle.launcher.daemon.protocol.CancelBuild;
import org.gradle.launcher.daemon.protocol.Success;
import org.jspecify.annotations.NullMarked;

/**
 * Cancels the build a daemon is running, on behalf of a process that did not start it.
 *
 * <p>The request is made and answered immediately rather than waited on. Cancellation can take as long
 * as the build takes to unwind, and the caller is a management tool that must not be held open for it.
 * Whether the build actually ends is observed through the daemon's state, not through this reply.
 */
@NullMarked
public class HandleCancelBuild implements DaemonCommandAction {

    @Override
    public void execute(DaemonCommandExecution execution) {
        if (!(execution.getCommand() instanceof CancelBuild)) {
            execution.proceed();
            return;
        }
        boolean wasRunningBuild = execution.getDaemonStateControl().getState() == DaemonState.Busy;
        if (wasRunningBuild) {
            execution.getDaemonStateControl().requestCancel();
        }
        execution.getConnection().completed(new Success(wasRunningBuild));
    }
}
