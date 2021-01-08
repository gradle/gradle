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
import org.gradle.internal.logging.events.PromptOutputEvent;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public abstract class AbstractUserInputRenderer implements OutputEventListener {
    final OutputEventListener delegate;
    private final List<OutputEvent> eventQueue = new ArrayList<OutputEvent>();
    private boolean paused;

    public AbstractUserInputRenderer(OutputEventListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof UserInputRequestEvent) {
            handleUserInputRequestEvent();
            return;
        } else if (event instanceof UserInputResumeEvent) {
            handleUserInputResumeEvent();
            return;
        } else if (event instanceof PromptOutputEvent) {
            handlePrompt((PromptOutputEvent) event);
            return;
        }

        if (paused) {
            eventQueue.add(event);
            return;
        }

        delegate.onOutput(event);
    }

    private void handleUserInputRequestEvent() {
        startInput();
        paused = true;
    }

    private void handleUserInputResumeEvent() {
        if (!paused) {
            throw new IllegalStateException("Cannot resume user input if not paused yet");
        }

        paused = false;
        finishInput();
        replayEvents();
    }

    private void replayEvents() {
        ListIterator<OutputEvent> iterator = eventQueue.listIterator();

        while (iterator.hasNext()) {
            delegate.onOutput(iterator.next());
            iterator.remove();
        }
    }

    List<OutputEvent> getEventQueue() {
        return eventQueue;
    }

    abstract void startInput();

    abstract void handlePrompt(PromptOutputEvent event);

    abstract void finishInput();
}
