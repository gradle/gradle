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

import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.internal.daemon.clientinput.ClientInputForwarder;

/**
 * Listens for {@link org.gradle.launcher.daemon.protocol.InputMessage} commands during execution and forwards them to this process' System.in and services such
 * as {@link UserInputReader}.
 */
public class ForwardClientInput implements DaemonCommandAction {
    private final ClientInputForwarder forwarder;

    public ForwardClientInput(UserInputReader inputReader, OutputEventListener eventDispatch) {
        this.forwarder = new ClientInputForwarder(inputReader, eventDispatch);
    }

    @Override
    public void execute(final DaemonCommandExecution execution) {
        forwarder.forwardInput(stdinHandler -> {
            execution.getConnection().onStdin(stdinHandler);
            try {
                execution.proceed();
            } finally {
                execution.getConnection().onStdin(null);
            }
            return null;
        });
    }
}
