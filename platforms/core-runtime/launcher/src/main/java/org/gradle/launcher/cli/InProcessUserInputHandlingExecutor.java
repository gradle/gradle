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

package org.gradle.launcher.cli;

import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.console.UserInputReceiver;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.BuildExecuter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * A {@link BuildActionExecuter} implementation that takes care of connecting the {@link GlobalUserInputReceiver} and {@link UserInputReader} together when the build is run in-process.
 */
class InProcessUserInputHandlingExecutor implements BuildActionExecuter<BuildActionParameters, BuildRequestContext> {
    private final GlobalUserInputReceiver userInputReceiver;
    private final UserInputReader userInputReader;
    private final BuildExecuter delegate;

    public InProcessUserInputHandlingExecutor(GlobalUserInputReceiver userInputReceiver, UserInputReader userInputReader, BuildExecuter delegate) {
        this.userInputReceiver = userInputReceiver;
        this.userInputReader = userInputReader;
        this.delegate = delegate;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildRequestContext buildRequestContext) {
        userInputReceiver.dispatchTo(new AsyncStdInReader());
        try {
            return delegate.execute(action, actionParameters, buildRequestContext);
        } finally {
            userInputReceiver.stopDispatching();
        }
    }

    private class AsyncStdInReader implements UserInputReceiver {
        @Override
        public void readAndForwardText(UserInputReceiver.Normalizer normalizer) {
            // Starts a new thread for each input request
            // This should be refactored to share more logic with DaemonClientInputForwarder
            Thread thread = new Thread(() -> {
                // Read a single line of text from stdin and forward to the UserInputReader
                while (true) {
                    String line;
                    try {
                        line = new BufferedReader(new InputStreamReader(System.in)).readLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (line != null) {
                        String result = normalizer.normalize(line);
                        if (result != null) {
                            userInputReader.putInput(new UserInputReader.TextResponse(result));
                        } else {
                            // Need to prompt the user again
                            continue;
                        }
                    } else {
                        userInputReader.putInput(UserInputReader.END_OF_INPUT);
                    }
                    break;
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
    }
}
