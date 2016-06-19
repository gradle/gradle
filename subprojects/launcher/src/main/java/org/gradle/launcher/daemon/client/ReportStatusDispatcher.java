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

package org.gradle.launcher.daemon.client;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.remote.internal.Connection;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.Failure;
import org.gradle.launcher.daemon.protocol.Finished;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.protocol.Result;
import org.gradle.launcher.daemon.protocol.Status;
import org.gradle.launcher.daemon.protocol.Success;

public class ReportStatusDispatcher {
    private static final Logger LOGGER = Logging.getLogger(ReportStatusDispatcher.class);

    public Status dispatch(Connection<Message> connection, Command statusCommand) {
        Status returnedStatus = null;
        Throwable failure = null;
        try {
            connection.dispatch(statusCommand);
            Result result = (Result) connection.receive();
            if (result instanceof Failure) {
                failure = ((Failure) result).getValue();
            } else if (result instanceof Success) {
                returnedStatus = (Status) result.getValue();
            }
            connection.dispatch(new Finished());
        } catch (Throwable e) {
            failure = e;
        }
        if (failure != null) {
            LOGGER.debug(String.format("Unable to get status of %s.", connection), failure);
        }
        return returnedStatus;
    }
}
