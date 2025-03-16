/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.events.ReadStdInEvent;
import org.gradle.internal.daemon.client.clientinput.DaemonClientInputForwarder;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.UserResponse;
import org.gradle.internal.daemon.clientinput.ClientInputForwarder;
import org.gradle.launcher.exec.BuildActionExecutor;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

import java.io.InputStream;

/**
 * Used in tooling API embedded mode to forward client provided user input to this process's System.in and other relevant services.
 * Reuses the services used by the daemon client and daemon server to forward user input.
 */
public class ForwardStdInToThisProcess implements BuildActionExecutor<BuildActionParameters, BuildRequestContext> {
    private final GlobalUserInputReceiver userInputReceiver;
    private final UserInputReader userInputReader;
    private final InputStream finalStandardInput;
    private final BuildActionExecutor<BuildActionParameters, BuildRequestContext> delegate;

    public ForwardStdInToThisProcess(
        GlobalUserInputReceiver userInputReceiver,
        UserInputReader userInputReader,
        InputStream finalStandardInput,
        BuildActionExecutor<BuildActionParameters, BuildRequestContext> delegate
    ) {
        this.userInputReceiver = userInputReceiver;
        this.userInputReader = userInputReader;
        this.finalStandardInput = finalStandardInput;
        this.delegate = delegate;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildRequestContext buildRequestContext) {
        ClientInputForwarder forwarder = new ClientInputForwarder(userInputReader, event -> {
            if (event instanceof ReadStdInEvent) {
                userInputReceiver.readAndForwardStdin((ReadStdInEvent) event);
            } else {
                throw new IllegalArgumentException();
            }
        });
        return forwarder.forwardInput(stdinHandler -> {
            DaemonClientInputForwarder inputForwarder = new DaemonClientInputForwarder(finalStandardInput, message -> {
                if (message instanceof UserResponse) {
                    stdinHandler.onUserResponse((UserResponse) message);
                } else if (message instanceof ForwardInput) {
                    stdinHandler.onInput((ForwardInput) message);
                } else if (message instanceof CloseInput) {
                    stdinHandler.onEndOfInput();
                } else {
                    throw new IllegalArgumentException();
                }
            }, userInputReceiver);
            try {
                return delegate.execute(action, actionParameters, buildRequestContext);
            } finally {
                inputForwarder.stop();
                stdinHandler.onEndOfInput();
            }
        });
    }
}
