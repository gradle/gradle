/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.PromptOutputEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.jspecify.annotations.Nullable;

public class UserInputConsoleRenderer extends AbstractUserInputRenderer {
    private final Console console;
    private final @Nullable ProgramStatusReporter programStatusReporter;

    public UserInputConsoleRenderer(OutputEventListener delegate, Console console, GlobalUserInputReceiver userInput, TemporaryFileProvider temporaryFileProvider) {
        this(delegate, console, userInput, temporaryFileProvider, null);
    }

    public UserInputConsoleRenderer(OutputEventListener delegate, Console console, GlobalUserInputReceiver userInput, TemporaryFileProvider temporaryFileProvider, @Nullable ProgramStatusReporter programStatusReporter) {
        super(delegate, userInput, temporaryFileProvider);
        this.console = console;
        this.programStatusReporter = programStatusReporter;
    }

    @Override
    void startInput() {
        toggleBuildProgressAreaVisibility(false);
        flushConsole();
        if (programStatusReporter != null) {
            // Output is paused until the prompt is answered, so the state of the build is written now rather than on the next redraw
            programStatusReporter.flush();
        }
    }

    @Override
    void handlePrompt(RenderableOutputEvent event) {
        event.render(console.getBuildOutputArea());
        flushConsole();
        if (programStatusReporter != null && event instanceof PromptOutputEvent) {
            // Written after the prompt has been flushed to the console, so the sequence cannot land inside another escape sequence
            programStatusReporter.blocked(((PromptOutputEvent) event).getPrompt());
        }
    }

    @Override
    void finishInput(RenderableOutputEvent event) {
        event.render(console.getBuildOutputArea());
        toggleBuildProgressAreaVisibility(true);
        flushConsole();
        if (programStatusReporter != null) {
            programStatusReporter.unblocked();
        }
    }

    private void toggleBuildProgressAreaVisibility(boolean visible) {
        console.getBuildProgressArea().setVisible(visible);
    }

    private void flushConsole() {
        console.flush();
    }
}
