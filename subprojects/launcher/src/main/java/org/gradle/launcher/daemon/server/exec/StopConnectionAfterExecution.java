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

public class StopConnectionAfterExecution implements DaemonCommandAction {
    
    private static final Logger LOGGER = Logging.getLogger(StopConnectionAfterExecution.class);

    public void execute(DaemonCommandExecution execution) {
        try {
            execution.proceed();
            //TODO SF this needs to be refactored down the road with consideration around exception handling
            //for now, we'll just execute all finalizers here
            LOGGER.debug("Execution completed. Running finalizers...");
            execution.executeFinalizers();
            LOGGER.debug("Finalizers execution complete.");
        } finally {
            LOGGER.debug("Stopping connection: {}", execution.getConnection());
            execution.getConnection().stop();
            LOGGER.debug("Connection stopped.");
        }
    }

}
