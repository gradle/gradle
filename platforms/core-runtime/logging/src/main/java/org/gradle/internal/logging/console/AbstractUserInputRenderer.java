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
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.FlushOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.PromptOutputEvent;
import org.gradle.internal.logging.events.ReadStdInEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.events.UserInputValidationProblemEvent;
import org.gradle.internal.logging.serializer.OutputEventSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public abstract class AbstractUserInputRenderer implements OutputEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractUserInputRenderer.class);
    private static final long MAX_OVERFLOW_FILE_SIZE = 1_024L * 1_024L * 1_024L; // 1GB

    static final int MEMORY_QUEUE_LIMIT = 2_500;

    protected final OutputEventListener delegate;
    private final GlobalUserInputReceiver userInput;
    private final TemporaryFileProvider temporaryFileProvider;
    private final Serializer<OutputEvent> outputEventSerializer;
    private final Deque<OutputEvent> eventQueue = new ArrayDeque<>(MEMORY_QUEUE_LIMIT);

    private @Nullable File overflowFile;
    private @Nullable KryoBackedEncoder overflowEncoder;
    private int overflowEventCount;
    private boolean paused;
    private boolean overflowFailed;

    public AbstractUserInputRenderer(OutputEventListener delegate, GlobalUserInputReceiver userInput, TemporaryFileProvider temporaryFileProvider) {
        this.delegate = delegate;
        this.userInput = userInput;
        this.temporaryFileProvider = temporaryFileProvider;
        this.outputEventSerializer = OutputEventSerializer.create();
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
            if (event instanceof UpdateNowEvent
                || event instanceof FlushOutputEvent
                || event instanceof EndOutputEvent) {
                return;
            }
            bufferEvent(event);
            return;
        }

        delegate.onOutput(event);
    }

    private void bufferEvent(OutputEvent event) {
        if (eventQueue.size() < MEMORY_QUEUE_LIMIT) {
            eventQueue.add(event);
        } else {
            writeToOverflow(event);
        }
    }

    private void writeToOverflow(OutputEvent event) {
        if (overflowFailed) {
            eventQueue.add(event);
            return;
        }
        if (overflowFile != null && overflowFile.length() >= MAX_OVERFLOW_FILE_SIZE) {
            cleanupOverflow();
            throw new IllegalStateException("User input overflow file exceeded " + MAX_OVERFLOW_FILE_SIZE + " bytes, aborting to prevent filling up the disk");
        }
        try {
            if (overflowEncoder == null) {
                overflowFile = temporaryFileProvider.createTemporaryFile("user-input-overflow-", ".bin");
                overflowFile.deleteOnExit();
                overflowEncoder = new KryoBackedEncoder(Files.newOutputStream(overflowFile.toPath()));
            }
            outputEventSerializer.write(overflowEncoder, event);
            overflowEventCount++;
        } catch (Exception e) {
            LOGGER.warn("Failed to write overflow event to disk, falling back to in-memory buffer", e);
            overflowFailed = true;
            closeOverflowEncoder();
            eventQueue.add(event);
        }
    }

    int getBufferedEventCount() {
        return eventQueue.size() + overflowEventCount;
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
        while (!eventQueue.isEmpty()) {
            delegate.onOutput(eventQueue.pop());
        }

        if (overflowFile != null) {
            try {
                replayOverflowFromDisk();
            } finally {
                cleanupOverflow();
            }
        } else {
            // Reset overflow failure flag even when no file was created,
            // so the next pause/resume cycle can attempt disk overflow again.
            overflowFailed = false;
        }
    }

    private void replayOverflowFromDisk() {
        try {
            closeOverflowEncoder();
            int replayedCount = 0;
            try (FileInputStream fis = new FileInputStream(Objects.requireNonNull(overflowFile));
                 KryoBackedDecoder decoder = new KryoBackedDecoder(fis)) {
                while (true) {
                    OutputEvent event = outputEventSerializer.read(decoder);
                    delegate.onOutput(event);
                    replayedCount++;
                }
            } catch (EOFException e) {
                if (replayedCount != overflowEventCount) {
                    LOGGER.warn("Overflow file may be corrupt: expected {} events but replayed {}", overflowEventCount, replayedCount);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to replay overflow events from disk", e);
        }
    }

    private void closeOverflowEncoder() {
        if (overflowEncoder != null) {
            try {
                overflowEncoder.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close overflow encoder", e);
            }
            overflowEncoder = null;
        }
    }

    private void cleanupOverflow() {
        closeOverflowEncoder();
        if (overflowFile != null) {
            overflowFile.delete();
            overflowFile = null;
        }
        overflowEventCount = 0;
        overflowFailed = false;
    }

    Deque<OutputEvent> getEventQueue() {
        return eventQueue;
    }

    abstract void startInput();

    abstract void handlePrompt(RenderableOutputEvent event);

    abstract void finishInput(RenderableOutputEvent event);
}
