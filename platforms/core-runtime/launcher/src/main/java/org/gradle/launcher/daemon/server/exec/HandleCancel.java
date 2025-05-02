/*
 * Copyright 2014 the original author or authors.
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

/**
 * Install a handler for cancel request processed asynchronously while the daemon proceeds to build execution.
 */
public class HandleCancel implements DaemonCommandAction {
    private static final Logger LOGGER = Logging.getLogger(HandleCancel.class);

    @Override
    public void execute(final DaemonCommandExecution execution) {
        execution.getConnection().onCancel(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("HandleCancel processing {}", execution.getCommand());
                execution.getDaemonStateControl().cancelBuild();
            }
        });
        try {
            execution.proceed();
        } finally {
            execution.getConnection().onCancel(null);
        }
    }
}
