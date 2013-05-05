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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.protocol.DaemonFailure;

/**
 * Wraps the rest of the command execution in a try catch in order to forward any internal
 * errors back to the client as DaemonFailure results.
 */
public class CatchAndForwardDaemonFailure implements DaemonCommandAction {

    private static final Logger LOGGER = Logging.getLogger(CatchAndForwardDaemonFailure.class);

    public void execute(DaemonCommandExecution execution) {
        try {
            execution.proceed();
        } catch (Throwable e) {
            LOGGER.error(String.format("Daemon failure during execution of %s - ", execution.getCommand()), e);
            execution.getConnection().completed(new DaemonFailure(e));
        }
    }
}