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

public class WatchForDisconnection implements DaemonCommandAction {

    private static final Logger LOGGER = Logging.getLogger(WatchForDisconnection.class);

    public void execute(final DaemonCommandExecution execution) {
        // Watch for the client disconnecting before we call stop()
        execution.getConnection().onDisconnect(new Runnable() {
            public void run() {
                LOGGER.warn("client disconnection detected, stopping the daemon");
                
                /*
                    When the daemon was started through the DaemonMain entry point, this will cause the entire
                    JVM to exit with code 1 (which is what we want) because the call to requestStopOnIdleTimeout() in
                    DaemonMain#doAction will throw a DaemonStoppedException. Note that at this point we will also 
                    immediately remove the daemon from the registry.
                */
                execution.getDaemonStateControl().requestForcefulStop();
            }
        });

        try {
            execution.proceed();
        } finally {
            // Remove the handler
            execution.getConnection().onDisconnect(null);
        }
    }

}