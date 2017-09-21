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

import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;

import java.util.ArrayList;
import java.util.List;

public class UserInputConsoleRenderer implements OutputEventListener {
    private final OutputEventListener delegate;
    private final Console console;
    private final List<OutputEvent> eventQueue = new ArrayList<OutputEvent>();
    private boolean paused;

    public UserInputConsoleRenderer(OutputEventListener delegate, Console console) {
        this.delegate = delegate;
        this.console = console;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof UserInputRequestEvent) {
            String prompt = ((UserInputRequestEvent) event).getPrompt();
            handleUserInputRequestEvent(prompt);
            return;
        } else if (event instanceof UserInputResumeEvent) {
            handleUserInputResumeEvent();
            return;
        }

        if (paused) {
            eventQueue.add(event);
            return;
        }

        delegate.onOutput(event);
    }

    private void handleUserInputRequestEvent(String prompt) {
        toggleBuildProgressAreaVisibility(false);
        printToBuildProgressArea(prompt);
        paused = true;
    }

    private void handleUserInputResumeEvent() {
        if (!paused) {
            throw new IllegalStateException("Cannot resume user input if not paused yet");
        }

        paused = false;
        toggleBuildProgressAreaVisibility(true);
        replayEvents();
    }

    private void replayEvents() {
        for (OutputEvent outputEvent : eventQueue) {
            delegate.onOutput(outputEvent);
        }

        eventQueue.clear();
    }

    private void toggleBuildProgressAreaVisibility(boolean visible) {
        console.getBuildProgressArea().setVisible(visible);
        console.flush();
    }

    private void printToBuildProgressArea(String message) {
        console.getBuildOutputArea().println(message);
        console.flush();
    }

    List<OutputEvent> getEventQueue() {
        return eventQueue;
    }
}
