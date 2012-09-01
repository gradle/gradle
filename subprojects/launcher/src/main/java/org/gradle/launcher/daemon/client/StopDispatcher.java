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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.messaging.remote.internal.Connection;

/**
 * @author: Szczepan Faber, created at: 9/13/11
 */
public class StopDispatcher {
    private static final Logger LOGGER = Logging.getLogger(StopDispatcher.class);
    private final IdGenerator<?> idGenerator;

    public StopDispatcher(IdGenerator<?> idGenerator) {
        this.idGenerator = idGenerator;
    }

    public void dispatch(Connection<Object> connection) {
        //At the moment if we cannot communicate with the daemon we assume it is stopped and print a message to the user
        try {
            connection.dispatch(new Stop(idGenerator.generateId()));
        } catch (Exception e) {
            LOGGER.lifecycle("Unable to send the Stop command to one of the daemons. The daemon has already stopped or crashed.");
            LOGGER.debug("Unable to send Stop.", e);
            return;
        }
        try {
            connection.receive();
        } catch (Exception e) {
            LOGGER.lifecycle("The daemon didn't reply to Stop command. It is already stopped or crashed.");
            LOGGER.debug("Unable to receive reply.", e);
        }
    }
}
