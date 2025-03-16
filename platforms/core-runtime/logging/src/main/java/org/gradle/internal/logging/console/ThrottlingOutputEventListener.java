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

import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.InteractiveEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Queue output events to be forwarded and schedule flush when time passed or if end of build is signalled.
 */
public class ThrottlingOutputEventListener implements OutputEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThrottlingOutputEventListener.class);

    private final OutputEventListener listener;

    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final int throttleMs;
    private final Object lock = new Object();

    private final List<OutputEvent> queue = new ArrayList<OutputEvent>();

    public ThrottlingOutputEventListener(OutputEventListener listener, Clock clock) {
        this(listener, Integer.getInteger("org.gradle.internal.console.throttle", 100), Executors.newSingleThreadScheduledExecutor(), clock);
    }

    ThrottlingOutputEventListener(OutputEventListener listener, int throttleMs, ScheduledExecutorService executor, Clock clock) {
        this.throttleMs = throttleMs;
        this.listener = listener;
        this.executor = executor;
        this.clock = clock;
        scheduleUpdateNow();
    }

    private void scheduleUpdateNow() {
        ScheduledFuture<?> ignored = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    onOutput(new UpdateNowEvent(clock.getCurrentTime()));
                } catch (Throwable t) {
                    // this class is used as task in a scheduled executor service, so it must not throw any throwable,
                    // otherwise the further invocations of this task get automatically and silently cancelled
                    LOGGER.debug("Exception while displaying output", t);
                }
            }
        }, throttleMs, throttleMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOutput(OutputEvent newEvent) {
        synchronized (lock) {
            queue.add(newEvent);

            if (queue.size() == 10000) {
                renderNow();
                return;
            }

            if (newEvent instanceof InteractiveEvent) {
                renderNow();
                return;
            }

            if (newEvent instanceof EndOutputEvent) {
                // Flush and clean up
                renderNow();
                executor.shutdown();
            }

            // Else, wait for the next update event
        }
    }

    private void renderNow() {
        // Remove event only as it is handled, and leave unhandled events in the queue
        while (!queue.isEmpty()) {
            OutputEvent event = queue.remove(0);
            listener.onOutput(event);
        }
    }
}
