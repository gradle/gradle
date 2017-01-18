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

package org.gradle.internal.logging.console;

import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsoleBackedProgressRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private final Console console;
    private final ProgressOperations operations = new ProgressOperations();
    private final DefaultStatusBarFormatter statusBarFormatter;
    private final ScheduledExecutorService executor;
    private final TimeProvider timeProvider;
    private final int throttleMs;
    // Protected by lock
    private final Object lock = new Object();
    private long lastUpdate;
    private final List<OutputEvent> queue = new ArrayList<OutputEvent>();
    private ProgressOperation mostRecentOperation;
    private Label statusBar;

    public ConsoleBackedProgressRenderer(OutputEventListener listener, Console console, DefaultStatusBarFormatter statusBarFormatter, TimeProvider timeProvider) {
        this(listener, console, statusBarFormatter, Integer.getInteger("org.gradle.console.throttle", 85), Executors.newSingleThreadScheduledExecutor(), timeProvider);
    }

    ConsoleBackedProgressRenderer(OutputEventListener listener, Console console, DefaultStatusBarFormatter statusBarFormatter, int throttleMs, ScheduledExecutorService executor, TimeProvider timeProvider) {
        this.throttleMs = throttleMs;
        this.listener = listener;
        this.console = console;
        this.statusBarFormatter = statusBarFormatter;
        this.executor = executor;
        this.timeProvider = timeProvider;
    }

    public void onOutput(OutputEvent newEvent) {
        synchronized (lock) {
            queue.add(newEvent);

            if (newEvent instanceof EndOutputEvent) {
                // Flush and clean up
                renderNow(timeProvider.getCurrentTime());
                executor.shutdown();
                return;
            }

            if (queue.size() > 1) {
                // Currently queuing events, a thread is scheduled to flush the queue later
                return;
            }

            long now = timeProvider.getCurrentTime();
            if (now - lastUpdate >= throttleMs) {
                // Has been long enough since last update - flush now
                renderNow(now);
                return;
            }

            // This is the first queued event - schedule a thread to flush later
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        renderNow(timeProvider.getCurrentTime());
                    }
                }
            }, throttleMs, TimeUnit.MILLISECONDS);
        }
    }

    private void renderNow(long now) {
        if (queue.isEmpty()) {
            // Already rendered - don't update anything
            return;
        }

        ProgressOperation lastOp = mostRecentOperation;
        for (OutputEvent event : queue) {
            try {
                if (event instanceof ProgressStartEvent) {
                    ProgressStartEvent startEvent = (ProgressStartEvent) event;
                    lastOp = operations.start(startEvent.getShortDescription(), startEvent.getStatus(), startEvent.getOperationId(), startEvent.getParentId());
                } else if (event instanceof ProgressCompleteEvent) {
                    ProgressOperation op = operations.complete(((ProgressCompleteEvent) event).getOperationId());
                    lastOp = op.getParent();
                } else if (event instanceof ProgressEvent) {
                    ProgressEvent progressEvent = (ProgressEvent) event;
                    lastOp = operations.progress(progressEvent.getStatus(), progressEvent.getOperationId());
                }
                listener.onOutput(event);
            } catch (Exception e) {
                throw new RuntimeException("Unable to process incoming event '" + event + "' (" + event.getClass().getSimpleName() + ")", e);
            }
        }
        if (lastOp != null) {
            getStatusBar().setText(statusBarFormatter.format(lastOp));
        } else if (mostRecentOperation != null) {
            getStatusBar().setText("");
        }
        mostRecentOperation = lastOp;
        queue.clear();
        lastUpdate = now;
        console.flush();
    }

    private Label getStatusBar() {
        if (statusBar == null) {
            statusBar = console.getStatusBar();
        }
        return statusBar;
    }
}
