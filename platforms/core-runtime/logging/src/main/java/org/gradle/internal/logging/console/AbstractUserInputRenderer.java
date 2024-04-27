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
import org.gradle.internal.logging.events.ReadStdInEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.events.UserInputValidationProblemEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public abstract class AbstractUserInputRenderer implements OutputEventListener {
    protected final OutputEventListener delegate;
    private final GlobalUserInputReceiver userInput;
    private final List<OutputEvent> eventQueue = new ArrayList<OutputEvent>();
    private boolean paused;

    public AbstractUserInputRenderer(OutputEventListener delegate, GlobalUserInputReceiver userInput) {
        this.delegate = delegate;
        this.userInput = userInput;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof UserInputRequestEvent) {
            handleUserInputRequestEvent();
            return;
        } else if (event instanceof UserInputResumeEvent) {
            handleUserInputResumeEvent((UserInputResumeEvent) event);
            return;
        } else if (event instanceof PromptOutputEvent) {
            handlePromptOutputEvent((PromptOutputEvent) event);
            return;
        } else if (event instanceof UserInputValidationProblemEvent) {
            handleValidationProblemEvent((UserInputValidationProblemEvent) event);
            return;
        } else if (event instanceof ReadStdInEvent) {
            userInput.readAndForwardStdin((ReadStdInEvent) event);
            return;
        }

        if (paused) {
            eventQueue.add(event);
            return;
        }

        delegate.onOutput(event);
    }

    private void handleValidationProblemEvent(UserInputValidationProblemEvent event) {
        handlePrompt(event);
    }

    private void handlePromptOutputEvent(PromptOutputEvent event) {
        // Start capturing input prior to displaying the prompt so that the input received after the prompt is displayed will be captured.
        // This does leave a small window where some text may be captured prior to the prompt being fully displayed, however this is
        // better than doing things in the other order, where there will be a small window where text may not be captured after prompt is fully displayed.
        // This is only a problem for tooling; for a human (the audience for this feature) this makes no difference.
        userInput.readAndForwardText(event);
        handlePrompt(event);
    }

    private void handleUserInputRequestEvent() {
        startInput();
        paused = true;
    }

    private void handleUserInputResumeEvent(UserInputResumeEvent event) {
        if (!paused) {
            throw new IllegalStateException("Cannot resume user input if not paused yet");
        }

        paused = false;
        finishInput(event);
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

    abstract void handlePrompt(RenderableOutputEvent event);

    abstract void finishInput(RenderableOutputEvent event);
}
