/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;

public class RequestStopIfSingleUsedDaemon implements DaemonCommandAction {

    private static final Logger LOGGER = Logging.getLogger(RequestStopIfSingleUsedDaemon.class);

    public void execute(DaemonCommandExecution execution) {
        if (execution.isSingleUseDaemon()) {
            LOGGER.debug("Requesting daemon stop after processing {}", execution.getCommand());
            // Does not take effect until after execution has completed
            execution.getDaemonStateControl().requestStop("stopping after processing");
        }
        execution.proceed();
    }
}
