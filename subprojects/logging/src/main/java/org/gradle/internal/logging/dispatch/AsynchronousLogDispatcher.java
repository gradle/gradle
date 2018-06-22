/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.dispatch;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.FlushOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class AsynchronousLogDispatcher extends Thread {
    private final BlockingQueue<OutputEvent> eventQueue = new ArrayBlockingQueue<OutputEvent>(200);
    private final OutputEventListener eventListener;
    private Throwable failure;

    public AsynchronousLogDispatcher(OutputEventListener eventListener) {
        super("Log dispatcher");
        setDaemon(true);
        this.eventListener = eventListener;
    }

    public void submit(OutputEvent event) {
        try {
            eventQueue.put(event);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                OutputEvent event = eventQueue.take();
                try {
                    eventListener.onOutput(event);
                } catch (Throwable t) {
                    if (failure == null) {
                        failure = t;
                    }
                }
                if (event instanceof FlushOutputEvent) {
                    FlushOutputEvent flushOutputEvent = (FlushOutputEvent) event;
                    flushOutputEvent.handled(failure);
                    failure = null;
                }
                if (event instanceof EndOutputEvent) {
                    return;
                }
            } catch (Throwable t) {
                if (failure == null) {
                    failure = t;
                }
            }
        }
    }
}
