/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.api.Action;
import org.gradle.launcher.protocol.Command;
import org.gradle.launcher.protocol.CommandComplete;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Connection;

/**
 * The client piece of the build daemon.
 *
 * <p>Protocol is this:</p>
 *
 * <ol> <li>Client connects to the server.</li>
 *
 * <li>Client sends a {@link org.gradle.launcher.protocol.Command} message.</li>
 *
 * <li>Server sends zero or more {@link org.gradle.logging.internal.OutputEvent} messages. Note that the server may
 * send output messages before it receives the command message.
 * </li>
 *
 * <li>Server sends a {@link org.gradle.launcher.protocol.CommandComplete} message.</li>
 *
 * <li>Connection is closed.</li>
 *
 * </ol>
 */
public abstract class DaemonClientAction implements Action<ExecutionListener> {
    protected final OutputEventListener outputEventListener;

    public DaemonClientAction(OutputEventListener outputEventListener) {
        this.outputEventListener = outputEventListener;
    }

    protected void run(Command command, Connection<Object> connection, ExecutionListener executionListener) {
        try {
            connection.dispatch(command);

            while (true) {
                Object object = connection.receive();
                if (object instanceof CommandComplete) {
                    CommandComplete commandComplete = (CommandComplete) object;
                    if (commandComplete.getFailure() != null) {
                        executionListener.onFailure(commandComplete.getFailure());
                    }
                    break;
                }
                OutputEvent outputEvent = (OutputEvent) object;
                outputEventListener.onOutput(outputEvent);
            }
        } finally {
            connection.stop();
        }
    }
}
